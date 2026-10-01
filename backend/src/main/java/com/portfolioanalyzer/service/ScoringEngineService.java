package com.portfolioanalyzer.service;

import com.portfolioanalyzer.dto.external.GitHubEventDto;
import com.portfolioanalyzer.dto.external.GitHubRepoDto;
import com.portfolioanalyzer.dto.response.ScoreBreakdownDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic scoring engine. Every number produced here is a pure
 * function of the GitHub data fetched for the user — there is no generative
 * AI, no external LLM call, and no non-reproducible randomness anywhere in
 * this class. Running it twice on the same input always yields the same
 * output, which is what makes score history comparable over time.
 *
 * ==========================================================================
 * SCORING ALGORITHM OVERVIEW
 * ==========================================================================
 *
 * Overall Score = 0.30 * CommitConsistency
 *               + 0.20 * RepositoryQuality
 *               + 0.20 * CodeImpact
 *               + 0.15 * TechDiversity
 *               + 0.15 * RecentActivity
 *
 * All five components are independently normalized to a 0-100 scale before
 * weighting, so the weights above sum to exactly 1.0 and the overall score
 * is always in [0, 100].
 *
 * 1) COMMIT CONSISTENCY (30%)
 *    GitHub's public-events feed is bucketed into ISO weeks (Mon-Sun) over a
 *    configurable trailing window (default 12 weeks). Consistency rewards
 *    showing up every week rather than one huge burst:
 *        consistency = (weeksWithAtLeastOneCommit / totalWeeksInWindow) * 100
 *
 * 2) REPOSITORY QUALITY (20%) — stars/forks earned relative to repo size
 *    For each non-fork repository:
 *        qualityIndex = (stars + 2*forks) / sqrt(sizeInHundredsOfKb + 1)
 *    This rewards repos that attract engagement without needing to be huge
 *    (the sqrt-of-size denominator flattens the penalty for legitimately
 *    large projects while still discounting bloated, low-engagement repos).
 *    The mean qualityIndex of the user's top 5 repos (by qualityIndex) is
 *    then compared against a benchmark constant and clamped to [0, 100].
 *
 * 3) CODE IMPACT (20%) — aggregate reach across all owned, non-fork repos
 *        impactRaw = totalStars + 2 * totalForks
 *    Impact is logarithmic (a jump from 10 to 100 stars matters more than
 *    a jump from 1000 to 1090), scored against a benchmark:
 *        impactScore = 100 * log10(impactRaw + 1) / log10(benchmark + 1)
 *
 * 4) TECH DIVERSITY (15%) — breadth of languages actually shipped
 *        diversity = 100 * min(1, distinctPrimaryLanguages / diversityCap)
 *
 * 5) RECENT ACTIVITY (15%) — exponential recency decay
 *    Using the most recent push event timestamp:
 *        recentActivity = 100 * e^(-daysSinceLastPush / decayConstant)
 *    A user who pushed today scores ~100; one who pushed 45 days ago (the
 *    default decay constant) scores ~37; the score asymptotically
 *    approaches (but never reaches) 0 for long-dormant accounts.
 */
@Service
public class ScoringEngineService {

    @Value("${scoring.weights.commit-consistency}")
    private double weightCommitConsistency;

    @Value("${scoring.weights.repository-quality}")
    private double weightRepositoryQuality;

    @Value("${scoring.weights.code-impact}")
    private double weightCodeImpact;

    @Value("${scoring.weights.tech-diversity}")
    private double weightTechDiversity;

    @Value("${scoring.weights.recent-activity}")
    private double weightRecentActivity;

    @Value("${scoring.consistency-window-weeks:12}")
    private int consistencyWindowWeeks;

    @Value("${scoring.tech-diversity-cap-languages:8}")
    private int techDiversityCapLanguages;

    @Value("${scoring.code-impact-benchmark:1000}")
    private double codeImpactBenchmark;

    @Value("${scoring.recent-activity-decay-days:45}")
    private double recentActivityDecayDays;

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * Computes the full score breakdown for a user given their fetched
     * repositories and public events.
     */
    public ScoringResult calculateScore(List<GitHubRepoDto> repos, List<GitHubEventDto> events) {
        List<GitHubRepoDto> ownedNonForkRepos = repos.stream()
                .filter(r -> r.getFork() == null || !r.getFork())
                .collect(Collectors.toList());

        Map<LocalDate, Integer> weeklyCommitCounts = buildWeeklyCommitCounts(events, consistencyWindowWeeks);

        BigDecimal commitConsistency = scoreCommitConsistency(weeklyCommitCounts);
        BigDecimal repositoryQuality = scoreRepositoryQuality(ownedNonForkRepos);
        BigDecimal codeImpact = scoreCodeImpact(ownedNonForkRepos);
        BigDecimal techDiversity = scoreTechDiversity(ownedNonForkRepos);
        BigDecimal recentActivity = scoreRecentActivity(events);

        BigDecimal overall = weighted(commitConsistency, weightCommitConsistency)
                .add(weighted(repositoryQuality, weightRepositoryQuality))
                .add(weighted(codeImpact, weightCodeImpact))
                .add(weighted(techDiversity, weightTechDiversity))
                .add(weighted(recentActivity, weightRecentActivity))
                .setScale(2, RoundingMode.HALF_UP);

        ScoreBreakdownDto breakdown = ScoreBreakdownDto.builder()
                .commitConsistency(commitConsistency)
                .repositoryQuality(repositoryQuality)
                .codeImpact(codeImpact)
                .techDiversity(techDiversity)
                .recentActivity(recentActivity)
                .build();

        return ScoringResult.builder()
                .overallScore(clamp(overall))
                .breakdown(breakdown)
                .weeklyCommitCounts(weeklyCommitCounts)
                .build();
    }

    // -------------------------------------------------------------------
    // 1) Commit Consistency
    // -------------------------------------------------------------------

    private Map<LocalDate, Integer> buildWeeklyCommitCounts(List<GitHubEventDto> events, int windowWeeks) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // Initialize every week in the window to 0 so weeks with no activity
        // are explicit gaps, not missing keys.
        Map<LocalDate, Integer> weekly = new LinkedHashMap<>();
        for (int i = windowWeeks - 1; i >= 0; i--) {
            weekly.put(currentWeekStart.minusWeeks(i), 0);
        }

        LocalDate windowStart = currentWeekStart.minusWeeks(windowWeeks - 1L);

        for (GitHubEventDto event : events) {
            if (!event.isPushEvent() || event.getCreatedAt() == null) {
                continue;
            }
            LocalDate eventDate = parseDate(event.getCreatedAt());
            if (eventDate == null || eventDate.isBefore(windowStart)) {
                continue;
            }
            LocalDate weekStart = eventDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int commitsInPush = event.getPayload() != null && event.getPayload().getSize() != null
                    ? event.getPayload().getSize() : 1;
            weekly.merge(weekStart, commitsInPush, Integer::sum);
        }

        return weekly;
    }

    private BigDecimal scoreCommitConsistency(Map<LocalDate, Integer> weeklyCommitCounts) {
        if (weeklyCommitCounts.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long weeksWithCommits = weeklyCommitCounts.values().stream().filter(c -> c > 0).count();
        double ratio = (double) weeksWithCommits / weeklyCommitCounts.size();
        return toScaledPercentage(ratio);
    }

    // -------------------------------------------------------------------
    // 2) Repository Quality (stars/forks earned vs. size)
    // -------------------------------------------------------------------

    private BigDecimal scoreRepositoryQuality(List<GitHubRepoDto> repos) {
        if (repos.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<Double> qualityIndices = repos.stream()
                .map(this::repoQualityIndex)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        int topN = Math.min(5, qualityIndices.size());
        double avgTopQuality = qualityIndices.stream()
                .limit(topN)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // Benchmark: a qualityIndex of 20 (e.g. ~20 stars on a 100KB repo, or
        // proportionally more stars on a larger repo) is treated as a "full
        // score" ceiling. Tunable via BENCHMARK below.
        final double benchmark = 20.0;
        double normalized = Math.min(1.0, avgTopQuality / benchmark);
        return toScaledPercentage(normalized);
    }

    private double repoQualityIndex(GitHubRepoDto repo) {
        int stars = nz(repo.getStargazersCount());
        int forks = nz(repo.getForksCount());
        int sizeKb = nz(repo.getSizeKb());
        double sizeInHundredsOfKb = sizeKb / 100.0;
        double denominator = Math.sqrt(sizeInHundredsOfKb + 1);
        return (stars + 2.0 * forks) / denominator;
    }

    // -------------------------------------------------------------------
    // 3) Code Impact (aggregate reach, log-scaled)
    // -------------------------------------------------------------------

    private BigDecimal scoreCodeImpact(List<GitHubRepoDto> repos) {
        int totalStars = repos.stream().mapToInt(r -> nz(r.getStargazersCount())).sum();
        int totalForks = repos.stream().mapToInt(r -> nz(r.getForksCount())).sum();
        double impactRaw = totalStars + 2.0 * totalForks;

        double numerator = Math.log10(impactRaw + 1);
        double denominator = Math.log10(codeImpactBenchmark + 1);
        double normalized = Math.min(1.0, numerator / denominator);
        return toScaledPercentage(normalized);
    }

    // -------------------------------------------------------------------
    // 4) Tech Diversity (distinct languages, capped)
    // -------------------------------------------------------------------

    private BigDecimal scoreTechDiversity(List<GitHubRepoDto> repos) {
        long distinctLanguages = repos.stream()
                .map(GitHubRepoDto::getLanguage)
                .filter(Objects::nonNull)
                .filter(lang -> !lang.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .count();

        double normalized = Math.min(1.0, (double) distinctLanguages / techDiversityCapLanguages);
        return toScaledPercentage(normalized);
    }

    // -------------------------------------------------------------------
    // 5) Recent Activity (exponential recency decay)
    // -------------------------------------------------------------------

    private BigDecimal scoreRecentActivity(List<GitHubEventDto> events) {
        Optional<LocalDate> mostRecentPush = events.stream()
                .filter(GitHubEventDto::isPushEvent)
                .map(e -> parseDate(e.getCreatedAt()))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());

        if (mostRecentPush.isEmpty()) {
            return BigDecimal.ZERO;
        }

        long daysSinceLastPush = ChronoUnit.DAYS.between(mostRecentPush.get(), LocalDate.now(ZoneOffset.UTC));
        daysSinceLastPush = Math.max(0, daysSinceLastPush);

        double decayed = 100.0 * Math.exp(-daysSinceLastPush / recentActivityDecayDays);
        return BigDecimal.valueOf(decayed).setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private BigDecimal weighted(BigDecimal score, double weight) {
        return score.multiply(BigDecimal.valueOf(weight));
    }

    private BigDecimal toScaledPercentage(double ratioZeroToOne) {
        double clamped = Math.max(0.0, Math.min(1.0, ratioZeroToOne));
        return BigDecimal.valueOf(clamped * 100.0).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (value.compareTo(BigDecimal.valueOf(100)) > 0) return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        return value;
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDate parseDate(String isoDateTime) {
        if (isoDateTime == null) return null;
        try {
            return Instant.from(ISO_DATE_TIME.parse(isoDateTime)).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }
}

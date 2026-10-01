package com.portfolioanalyzer.service;

import com.portfolioanalyzer.dto.external.GitHubRepoDto;
import com.portfolioanalyzer.dto.response.InsightsDto;
import com.portfolioanalyzer.dto.response.ScoreBreakdownDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Converts the numeric {@link ScoreBreakdownDto} produced by
 * {@link ScoringEngineService} into human-readable strengths and weaknesses.
 * Every rule here is a fixed threshold on a deterministic number — there is
 * no generative text model involved, so the same score always produces the
 * same insight strings.
 */
@Service
public class InsightsGeneratorService {

    private static final BigDecimal STRONG_THRESHOLD = BigDecimal.valueOf(70);
    private static final BigDecimal WEAK_THRESHOLD = BigDecimal.valueOf(35);
    private static final BigDecimal ELITE_OVERALL_THRESHOLD = BigDecimal.valueOf(85);

    public InsightsDto generate(BigDecimal overallScore, ScoreBreakdownDto breakdown, List<GitHubRepoDto> repos) {
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();

        evaluateConsistency(breakdown.getCommitConsistency(), strengths, weaknesses);
        evaluateRepositoryQuality(breakdown.getRepositoryQuality(), strengths, weaknesses);
        evaluateCodeImpact(breakdown.getCodeImpact(), strengths, weaknesses);
        evaluateTechDiversity(breakdown.getTechDiversity(), strengths, weaknesses);
        evaluateRecentActivity(breakdown.getRecentActivity(), strengths, weaknesses);

        if (overallScore.compareTo(ELITE_OVERALL_THRESHOLD) >= 0) {
            strengths.add(0, "Elite Overall Developer Profile");
        }

        addRepoSpecificInsights(repos, strengths, weaknesses);

        if (strengths.isEmpty()) {
            strengths.add("No standout strengths detected yet — keep building!");
        }
        if (weaknesses.isEmpty()) {
            weaknesses.add("No significant weaknesses detected.");
        }

        return InsightsDto.builder().strengths(strengths).weaknesses(weaknesses).build();
    }

    private void evaluateConsistency(BigDecimal score, List<String> strengths, List<String> weaknesses) {
        if (score.compareTo(STRONG_THRESHOLD) >= 0) {
            strengths.add("Highly Consistent Contributor");
        } else if (score.compareTo(WEAK_THRESHOLD) < 0) {
            weaknesses.add("Irregular Commit Patterns");
        }
    }

    private void evaluateRepositoryQuality(BigDecimal score, List<String> strengths, List<String> weaknesses) {
        if (score.compareTo(STRONG_THRESHOLD) >= 0) {
            strengths.add("Well-Engineered, High-Engagement Repositories");
        } else if (score.compareTo(WEAK_THRESHOLD) < 0) {
            weaknesses.add("Low Community Engagement Relative to Repository Size");
        }
    }

    private void evaluateCodeImpact(BigDecimal score, List<String> strengths, List<String> weaknesses) {
        if (score.compareTo(STRONG_THRESHOLD) >= 0) {
            strengths.add("Active Open Source Impact (Stars/Forks)");
        } else if (score.compareTo(WEAK_THRESHOLD) < 0) {
            weaknesses.add("Limited Project Visibility (Few Stars/Forks)");
        }
    }

    private void evaluateTechDiversity(BigDecimal score, List<String> strengths, List<String> weaknesses) {
        if (score.compareTo(STRONG_THRESHOLD) >= 0) {
            strengths.add("Broad, Polyglot Technology Stack");
        } else if (score.compareTo(WEAK_THRESHOLD) < 0) {
            weaknesses.add("Narrow Technology Focus");
        }
    }

    private void evaluateRecentActivity(BigDecimal score, List<String> strengths, List<String> weaknesses) {
        if (score.compareTo(STRONG_THRESHOLD) >= 0) {
            strengths.add("Actively Coding — Recent Push Activity");
        } else if (score.compareTo(WEAK_THRESHOLD) < 0) {
            weaknesses.add("Few Recent Commits");
        }
    }

    private void addRepoSpecificInsights(List<GitHubRepoDto> repos, List<String> strengths, List<String> weaknesses) {
        if (repos == null || repos.isEmpty()) {
            weaknesses.add("No Public Repositories Found");
            return;
        }

        repos.stream()
                .filter(r -> r.getStargazersCount() != null)
                .max(Comparator.comparingInt(GitHubRepoDto::getStargazersCount))
                .filter(r -> r.getStargazersCount() >= 10)
                .ifPresent(r -> strengths.add(
                        "Popular Repository: \"" + r.getName() + "\" (" + r.getStargazersCount() + " stars)"));

        long archivedCount = repos.stream().filter(r -> Boolean.TRUE.equals(r.getArchived())).count();
        if (repos.size() > 0 && archivedCount == repos.size()) {
            weaknesses.add("All Public Repositories Are Archived");
        }

        boolean hasDescriptions = repos.stream()
                .anyMatch(r -> r.getDescription() != null && !r.getDescription().isBlank());
        if (!hasDescriptions) {
            weaknesses.add("Repositories Lack Descriptions");
        }
    }
}

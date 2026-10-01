package com.portfolioanalyzer.service;

import com.portfolioanalyzer.dto.external.GitHubEventDto;
import com.portfolioanalyzer.dto.external.GitHubRepoDto;
import com.portfolioanalyzer.dto.external.GitHubUserDto;
import com.portfolioanalyzer.dto.response.*;
import com.portfolioanalyzer.entity.*;
import com.portfolioanalyzer.exception.AnalysisNotFoundException;
import com.portfolioanalyzer.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Coordinates the full analysis pipeline:
 *   GitHub fetch -> deterministic scoring -> insight generation -> persistence
 * and separately, the resume-matching pipeline that cross-references a PDF
 * against a previously-analyzed user's GitHub corpus.
 */
@Service
@RequiredArgsConstructor
public class DeveloperAnalysisService {

    private final GitHubApiService gitHubApiService;
    private final ScoringEngineService scoringEngineService;
    private final InsightsGeneratorService insightsGeneratorService;
    private final ResumeMatcherService resumeMatcherService;

    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final CommitActivityRepository commitActivityRepository;
    private final ResumeSkillMatchRepository resumeSkillMatchRepository;

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME;

    @Transactional
    public DeveloperAnalysisResponse analyzeDeveloper(String username) {
        GitHubUserDto profile = gitHubApiService.fetchUserProfile(username);
        List<GitHubRepoDto> repos = gitHubApiService.fetchUserRepositories(username);
        List<GitHubEventDto> events = gitHubApiService.fetchUserPublicEvents(username);

        ScoringResult scoringResult = scoringEngineService.calculateScore(repos, events);
        InsightsDto insights = insightsGeneratorService.generate(
                scoringResult.getOverallScore(), scoringResult.getBreakdown(), repos);

        UserEntity user = upsertUser(profile);
        replaceRepositories(user, repos);
        ScoreHistoryEntity scoreHistory = persistScoreHistory(user, scoringResult, insights);
        persistCommitActivity(user, scoreHistory, scoringResult.getWeeklyCommitCounts());

        return buildResponse(user, profile, repos, scoringResult, insights, scoreHistory.getId());
    }

    @Transactional
    public ResumeMatchResponse matchResume(String username, MultipartFile resumeFile) {
        UserEntity user = userRepository.findByGithubUsernameIgnoreCase(username)
                .orElseThrow(() -> new AnalysisNotFoundException(username));

        ScoreHistoryEntity latestRun = scoreHistoryRepository.findByUserOrderByCalculatedAtDesc(user).stream()
                .findFirst()
                .orElseThrow(() -> new AnalysisNotFoundException(username));

        String resumeText = resumeMatcherService.extractText(resumeFile);

        // Refetch repos for freshness rather than trusting a stale DB snapshot.
        List<GitHubRepoDto> repos = gitHubApiService.fetchUserRepositories(username);

        ResumeMatchResponse response = resumeMatcherService.matchSkills(username, resumeText, repos);
        persistSkillMatches(latestRun, response);
        return response;
    }

    // -------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------

    private UserEntity upsertUser(GitHubUserDto profile) {
        UserEntity user = userRepository.findByGithubUsernameIgnoreCase(profile.getLogin())
                .orElseGet(UserEntity::new);

        user.setGithubId(profile.getId());
        user.setGithubUsername(profile.getLogin());
        user.setDisplayName(profile.getName());
        user.setAvatarUrl(profile.getAvatarUrl());
        user.setBio(profile.getBio());
        user.setCompany(profile.getCompany());
        user.setLocation(profile.getLocation());
        user.setPublicReposCount(profile.getPublicRepos());
        user.setFollowersCount(profile.getFollowers());
        user.setFollowingCount(profile.getFollowing());
        user.setGithubAccountCreatedAt(parseDateTime(profile.getCreatedAt()));
        user.setLastFetchedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    private void replaceRepositories(UserEntity user, List<GitHubRepoDto> repos) {
        repositoryRepository.deleteByUser(user);
        List<RepositoryEntity> entities = repos.stream()
                .map(r -> RepositoryEntity.builder()
                        .user(user)
                        .githubRepoId(r.getId())
                        .name(r.getName())
                        .description(r.getDescription())
                        .primaryLanguage(r.getLanguage())
                        .stargazersCount(r.getStargazersCount())
                        .forksCount(r.getForksCount())
                        .openIssuesCount(r.getOpenIssuesCount())
                        .sizeKb(r.getSizeKb())
                        .isFork(Boolean.TRUE.equals(r.getFork()))
                        .isArchived(Boolean.TRUE.equals(r.getArchived()))
                        .topics(r.getTopics() != null ? String.join(",", r.getTopics()) : null)
                        .repoCreatedAt(parseDateTime(r.getCreatedAt()))
                        .repoPushedAt(parseDateTime(r.getPushedAt()))
                        .build())
                .collect(Collectors.toList());
        repositoryRepository.saveAll(entities);
    }

    private ScoreHistoryEntity persistScoreHistory(UserEntity user, ScoringResult result, InsightsDto insights) {
        ScoreHistoryEntity entity = ScoreHistoryEntity.builder()
                .user(user)
                .overallScore(result.getOverallScore())
                .commitConsistencyScore(result.getBreakdown().getCommitConsistency())
                .repositoryQualityScore(result.getBreakdown().getRepositoryQuality())
                .codeImpactScore(result.getBreakdown().getCodeImpact())
                .techDiversityScore(result.getBreakdown().getTechDiversity())
                .recentActivityScore(result.getBreakdown().getRecentActivity())
                .strengths(String.join("|", insights.getStrengths()))
                .weaknesses(String.join("|", insights.getWeaknesses()))
                .build();
        return scoreHistoryRepository.save(entity);
    }

    private void persistCommitActivity(UserEntity user, ScoreHistoryEntity scoreHistory,
                                        Map<java.time.LocalDate, Integer> weeklyCounts) {
        List<CommitActivityEntity> entities = weeklyCounts.entrySet().stream()
                .map(e -> CommitActivityEntity.builder()
                        .user(user)
                        .scoreHistory(scoreHistory)
                        .weekStartDate(e.getKey())
                        .commitCount(e.getValue())
                        .build())
                .collect(Collectors.toList());
        commitActivityRepository.saveAll(entities);
    }

    private void persistSkillMatches(ScoreHistoryEntity scoreHistory, ResumeMatchResponse response) {
        List<ResumeSkillMatchEntity> entities = response.getSkillMatches().stream()
                .map(m -> ResumeSkillMatchEntity.builder()
                        .scoreHistory(scoreHistory)
                        .skillName(m.getSkillName())
                        .foundInResume(m.isFoundInResume())
                        .foundInGithub(m.isFoundInGithub())
                        .githubEvidenceStrength(m.getGithubEvidenceStrength())
                        .confidenceLevel(m.getConfidenceLevel())
                        .build())
                .collect(Collectors.toList());
        resumeSkillMatchRepository.saveAll(entities);
    }

    // -------------------------------------------------------------------
    // Response assembly
    // -------------------------------------------------------------------

    private DeveloperAnalysisResponse buildResponse(UserEntity user, GitHubUserDto profile,
                                                      List<GitHubRepoDto> repos, ScoringResult scoringResult,
                                                      InsightsDto insights, Long scoreHistoryId) {
        List<RepositorySummaryDto> topRepos = repos.stream()
                .filter(r -> r.getFork() == null || !r.getFork())
                .sorted(Comparator.comparing((GitHubRepoDto r) -> r.getStargazersCount() == null ? 0 : r.getStargazersCount()).reversed())
                .limit(10)
                .map(r -> RepositorySummaryDto.builder()
                        .name(r.getName())
                        .description(r.getDescription())
                        .primaryLanguage(r.getLanguage())
                        .stars(r.getStargazersCount())
                        .forks(r.getForksCount())
                        .sizeKb(r.getSizeKb())
                        .isFork(Boolean.TRUE.equals(r.getFork()))
                        .build())
                .collect(Collectors.toList());

        List<CommitTrendPointDto> commitTrend = scoringResult.getWeeklyCommitCounts().entrySet().stream()
                .map(e -> CommitTrendPointDto.builder()
                        .weekStartDate(e.getKey())
                        .commitCount(e.getValue())
                        .build())
                .collect(Collectors.toList());

        return DeveloperAnalysisResponse.builder()
                .scoreHistoryId(scoreHistoryId)
                .githubUsername(user.getGithubUsername())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .overallScore(scoringResult.getOverallScore())
                .scoreBreakdown(scoringResult.getBreakdown())
                .insights(insights)
                .topRepositories(topRepos)
                .commitTrend(commitTrend)
                .publicReposCount(profile.getPublicRepos())
                .followersCount(profile.getFollowers())
                .build();
    }

    private LocalDateTime parseDateTime(String isoDateTime) {
        if (isoDateTime == null) return null;
        try {
            return LocalDateTime.ofInstant(Instant.from(ISO_DATE_TIME.parse(isoDateTime)), ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }
}

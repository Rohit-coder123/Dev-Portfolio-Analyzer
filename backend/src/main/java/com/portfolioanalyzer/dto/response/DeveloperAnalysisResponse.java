package com.portfolioanalyzer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Top-level payload returned by POST /api/v1/analysis/{username}.
 * This is the single object the Angular dashboard component binds to.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeveloperAnalysisResponse {

    private Long scoreHistoryId;
    private String githubUsername;
    private String displayName;
    private String avatarUrl;
    private String bio;

    private BigDecimal overallScore;
    private ScoreBreakdownDto scoreBreakdown;
    private InsightsDto insights;

    private List<RepositorySummaryDto> topRepositories;
    private List<CommitTrendPointDto> commitTrend;

    private Integer publicReposCount;
    private Integer followersCount;
}

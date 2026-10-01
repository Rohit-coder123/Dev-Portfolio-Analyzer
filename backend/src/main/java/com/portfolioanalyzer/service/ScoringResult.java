package com.portfolioanalyzer.service;

import com.portfolioanalyzer.dto.response.ScoreBreakdownDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Internal carrier for the output of {@link ScoringEngineService}. Not
 * exposed directly to the frontend — {@code DeveloperAnalysisService} maps
 * this into {@code DeveloperAnalysisResponse} and into persisted entities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringResult {
    private BigDecimal overallScore;
    private ScoreBreakdownDto breakdown;
    /** Weekly commit counts across the consistency window, oldest week first. */
    private Map<LocalDate, Integer> weeklyCommitCounts;
}

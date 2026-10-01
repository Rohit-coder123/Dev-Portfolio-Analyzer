package com.portfolioanalyzer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The five weighted components that sum to the overall Developer Score.
 * Field names map 1:1 to the Angular ScoreBreakdown interface.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreBreakdownDto {
    private BigDecimal commitConsistency;   // weight 0.30
    private BigDecimal repositoryQuality;   // weight 0.20
    private BigDecimal codeImpact;          // weight 0.20
    private BigDecimal techDiversity;       // weight 0.15
    private BigDecimal recentActivity;      // weight 0.15
}

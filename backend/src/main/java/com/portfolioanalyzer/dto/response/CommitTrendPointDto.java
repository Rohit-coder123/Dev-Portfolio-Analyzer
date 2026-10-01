package com.portfolioanalyzer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * One data point for the ng2-charts commit-consistency line chart.
 * weekStartDate is ISO-8601 (yyyy-MM-dd); the frontend formats it for display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitTrendPointDto {
    private LocalDate weekStartDate;
    private Integer commitCount;
}

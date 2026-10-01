package com.portfolioanalyzer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositorySummaryDto {
    private String name;
    private String description;
    private String primaryLanguage;
    private Integer stars;
    private Integer forks;
    private Integer sizeKb;
    private Boolean isFork;
}

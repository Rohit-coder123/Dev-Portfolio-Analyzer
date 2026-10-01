package com.portfolioanalyzer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillMatchDto {
    private String skillName;
    private boolean foundInResume;
    private boolean foundInGithub;
    private int githubEvidenceStrength;
    /** HIGH / MEDIUM / LOW */
    private String confidenceLevel;
}

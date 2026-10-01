package com.portfolioanalyzer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeMatchResponse {
    private String githubUsername;
    private int skillsDetectedInResume;
    private int skillsCorroboratedByGithub;
    private List<SkillMatchDto> skillMatches;
}

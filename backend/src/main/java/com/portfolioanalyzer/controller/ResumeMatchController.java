package com.portfolioanalyzer.controller;

import com.portfolioanalyzer.dto.response.ResumeMatchResponse;
import com.portfolioanalyzer.exception.InvalidResumeException;
import com.portfolioanalyzer.service.DeveloperAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/resume")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200"})
public class ResumeMatchController {

    private final DeveloperAnalysisService developerAnalysisService;

    /**
     * Accepts a PDF resume plus the GitHub username it should be
     * cross-referenced against. The username must have already been
     * analyzed via POST /api/v1/analysis/{username} at least once.
     */
    @PostMapping(value = "/{username}/match", consumes = "multipart/form-data")
    public ResponseEntity<ResumeMatchResponse> match(
            @PathVariable String username,
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidResumeException("No resume file was uploaded.");
        }

        ResumeMatchResponse response = developerAnalysisService.matchResume(username.trim(), file);
        return ResponseEntity.ok(response);
    }
}

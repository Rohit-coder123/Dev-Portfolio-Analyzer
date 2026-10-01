package com.portfolioanalyzer.controller;

import com.portfolioanalyzer.dto.response.DeveloperAnalysisResponse;
import com.portfolioanalyzer.service.DeveloperAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200"}) // Angular dev server
public class DeveloperAnalysisController {

    private final DeveloperAnalysisService developerAnalysisService;

    /**
     * Fetches the given GitHub user's profile/repos/events, runs the
     * deterministic scoring engine, generates insights, persists a new
     * score-history row, and returns the full dashboard payload.
     */
    @PostMapping("/{username}")
    public ResponseEntity<DeveloperAnalysisResponse> analyze(@PathVariable String username) {
        String sanitized = validateUsername(username);
        DeveloperAnalysisResponse response = developerAnalysisService.analyzeDeveloper(sanitized);
        return ResponseEntity.ok(response);
    }

    private String validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("GitHub username must not be blank.");
        }
        String trimmed = username.trim();
        if (!trimmed.matches("^[A-Za-z0-9-]{1,39}$")) {
            throw new IllegalArgumentException("'" + username + "' is not a valid GitHub username.");
        }
        return trimmed;
    }
}

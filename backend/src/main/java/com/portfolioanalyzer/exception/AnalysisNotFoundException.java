package com.portfolioanalyzer.exception;

public class AnalysisNotFoundException extends RuntimeException {
    public AnalysisNotFoundException(String username) {
        super("No prior GitHub analysis found for '" + username
                + "'. Run the GitHub analysis endpoint before uploading a resume.");
    }
}

package com.portfolioanalyzer.exception;

public class GithubRateLimitExceededException extends RuntimeException {

    private final long resetEpochSeconds;

    public GithubRateLimitExceededException(long resetEpochSeconds) {
        super("GitHub API rate limit exceeded. Resets at epoch second " + resetEpochSeconds + ".");
        this.resetEpochSeconds = resetEpochSeconds;
    }

    public long getResetEpochSeconds() {
        return resetEpochSeconds;
    }
}

package com.portfolioanalyzer.exception;

public class GithubUserNotFoundException extends RuntimeException {
    public GithubUserNotFoundException(String username) {
        super("GitHub user '" + username + "' was not found.");
    }
}

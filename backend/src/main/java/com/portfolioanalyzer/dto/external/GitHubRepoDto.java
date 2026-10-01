package com.portfolioanalyzer.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Maps a single element of the response of
 * GET https://api.github.com/users/{username}/repos
 *
 * Note: GitHub's default repo list payload does not include "topics" unless
 * requested with the "application/vnd.github.mercy-preview+json" media type
 * (now generally available) — GitHubApiService requests it explicitly so
 * this field is populated.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepoDto {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("language")
    private String language;

    @JsonProperty("stargazers_count")
    private Integer stargazersCount;

    @JsonProperty("forks_count")
    private Integer forksCount;

    @JsonProperty("open_issues_count")
    private Integer openIssuesCount;

    @JsonProperty("size")
    private Integer sizeKb;

    @JsonProperty("fork")
    private Boolean fork;

    @JsonProperty("archived")
    private Boolean archived;

    @JsonProperty("topics")
    private List<String> topics;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("pushed_at")
    private String pushedAt;
}

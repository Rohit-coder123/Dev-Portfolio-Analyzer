package com.portfolioanalyzer.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Maps the response of GET https://api.github.com/users/{username}
 * Only the fields the application actually uses are declared; everything
 * else in the payload is ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubUserDto {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("login")
    private String login;

    @JsonProperty("name")
    private String name;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("bio")
    private String bio;

    @JsonProperty("company")
    private String company;

    @JsonProperty("location")
    private String location;

    @JsonProperty("public_repos")
    private Integer publicRepos;

    @JsonProperty("followers")
    private Integer followers;

    @JsonProperty("following")
    private Integer following;

    @JsonProperty("created_at")
    private String createdAt; // ISO-8601 string, parsed by the service layer
}

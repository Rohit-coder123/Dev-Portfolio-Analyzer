package com.portfolioanalyzer.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Maps a single element of the response of
 * GET https://api.github.com/users/{username}/events/public
 *
 * We only care about events of type "PushEvent" — GitHub's REST v3 does not
 * expose a clean "all commits by this author across all repos" endpoint
 * without either iterating every repository (expensive, rate-limit heavy) or
 * using the Search API (30 req/min cap, separate auth rules). The public
 * events feed is the pragmatic, rate-limit-friendly source for commit
 * cadence: it returns the last ~300 events / 90 days of public activity,
 * which is exactly the window the Scoring Engine needs for Commit
 * Consistency and Recent Activity.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubEventDto {

    @JsonProperty("type")
    private String type;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("repo")
    private RepoRef repo;

    @JsonProperty("payload")
    private Payload payload;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RepoRef {
        @JsonProperty("name")
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        /** Number of commits included in this specific push (may be capped at 20 by GitHub). */
        @JsonProperty("size")
        private Integer size;
    }

    public boolean isPushEvent() {
        return "PushEvent".equals(this.type);
    }
}

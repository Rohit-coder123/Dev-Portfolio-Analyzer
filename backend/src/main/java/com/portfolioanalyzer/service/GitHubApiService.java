package com.portfolioanalyzer.service;

import com.portfolioanalyzer.dto.external.GitHubEventDto;
import com.portfolioanalyzer.dto.external.GitHubRepoDto;
import com.portfolioanalyzer.dto.external.GitHubUserDto;
import com.portfolioanalyzer.exception.GithubRateLimitExceededException;
import com.portfolioanalyzer.exception.GithubUserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin, defensive wrapper around the GitHub REST v3 API.
 *
 * Rate limiting strategy: every response is inspected for the standard
 * X-RateLimit-* headers. If GitHub reports zero remaining requests (or
 * responds with HTTP 403/429, which is how it signals both rate limiting and
 * abuse-detection throttling), a {@link GithubRateLimitExceededException} is
 * thrown immediately with the reset time surfaced to the caller rather than
 * silently retrying and burning further quota.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubApiService {

    private final WebClient githubWebClient;

    @Value("${github.api.max-repos-fetched:100}")
    private int maxReposFetched;

    @Value("${github.api.max-events-fetched:300}")
    private int maxEventsFetched;

    private static final int PAGE_SIZE = 100;

    /** Fetches the public profile for a GitHub username. */
    public GitHubUserDto fetchUserProfile(String username) {
        return githubWebClient.get()
                .uri("/users/{username}", username)
                .exchangeToMono(response -> handle(response, username, GitHubUserDto.class))
                .block();
    }

    /**
     * Fetches up to {@code maxReposFetched} repositories owned by the user,
     * paginating in pages of 100 (GitHub's maximum page size).
     */
    public List<GitHubRepoDto> fetchUserRepositories(String username) {
        List<GitHubRepoDto> allRepos = new ArrayList<>();
        int page = 1;

        while (allRepos.size() < maxReposFetched) {
            final int currentPage = page;
            List<GitHubRepoDto> pageResult = githubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/users/{username}/repos")
                            .queryParam("per_page", PAGE_SIZE)
                            .queryParam("page", currentPage)
                            .queryParam("sort", "updated")
                            .build(username))
                    .exchangeToMono(response -> handleList(response, username))
                    .block();

            if (pageResult == null || pageResult.isEmpty()) {
                break;
            }
            allRepos.addAll(pageResult);
            if (pageResult.size() < PAGE_SIZE) {
                break; // last page
            }
            page++;
        }

        if (allRepos.size() > maxReposFetched) {
            return allRepos.subList(0, maxReposFetched);
        }
        return allRepos;
    }

    /**
     * Fetches up to {@code maxEventsFetched} public events for the user
     * (GitHub caps this feed at ~300 events / 90 days regardless of paging).
     */
    public List<GitHubEventDto> fetchUserPublicEvents(String username) {
        List<GitHubEventDto> allEvents = new ArrayList<>();
        int page = 1;

        while (allEvents.size() < maxEventsFetched) {
            final int currentPage = page;
            List<GitHubEventDto> pageResult = githubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/users/{username}/events/public")
                            .queryParam("per_page", PAGE_SIZE)
                            .queryParam("page", currentPage)
                            .build(username))
                    .exchangeToMono(response -> handleEventList(response, username))
                    .block();

            if (pageResult == null || pageResult.isEmpty()) {
                break;
            }
            allEvents.addAll(pageResult);
            if (pageResult.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }

        return allEvents;
    }

    // -------------------------------------------------------------------
    // Shared response handling: rate limit + 404 + parsing
    // -------------------------------------------------------------------

    private <T> Mono<T> handle(org.springframework.web.reactive.function.client.ClientResponse response,
                                String username, Class<T> type) {
        assertNotRateLimited(response);
        if (response.statusCode().value() == 404) {
            throw new GithubUserNotFoundException(username);
        }
        if (!response.statusCode().is2xxSuccessful()) {
            throw new RuntimeException("GitHub API returned unexpected status: " + response.statusCode());
        }
        return response.bodyToMono(type);
    }

    private Mono<List<GitHubRepoDto>> handleList(org.springframework.web.reactive.function.client.ClientResponse response,
                                                  String username) {
        assertNotRateLimited(response);
        if (response.statusCode().value() == 404) {
            throw new GithubUserNotFoundException(username);
        }
        if (!response.statusCode().is2xxSuccessful()) {
            throw new RuntimeException("GitHub API returned unexpected status: " + response.statusCode());
        }
        return response.bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<GitHubRepoDto>>() {});
    }

    private Mono<List<GitHubEventDto>> handleEventList(org.springframework.web.reactive.function.client.ClientResponse response,
                                                        String username) {
        assertNotRateLimited(response);
        if (response.statusCode().value() == 404) {
            throw new GithubUserNotFoundException(username);
        }
        if (!response.statusCode().is2xxSuccessful()) {
            throw new RuntimeException("GitHub API returned unexpected status: " + response.statusCode());
        }
        return response.bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<GitHubEventDto>>() {});
    }

    /**
     * Inspects X-RateLimit-Remaining / X-RateLimit-Reset and the HTTP status
     * to detect both hard rate limiting (403/429 with remaining=0) and
     * GitHub's secondary abuse-detection throttling (403 with a Retry-After
     * header but no rate-limit headers at all).
     */
    private void assertNotRateLimited(org.springframework.web.reactive.function.client.ClientResponse response) {
        HttpHeaders headers = response.headers().asHttpHeaders();
        HttpStatusCode status = response.statusCode();

        String remainingHeader = headers.getFirst("X-RateLimit-Remaining");
        String resetHeader = headers.getFirst("X-RateLimit-Reset");
        String retryAfterHeader = headers.getFirst("Retry-After");

        boolean looksRateLimited =
                (status.value() == 403 || status.value() == 429) &&
                        ("0".equals(remainingHeader) || retryAfterHeader != null);

        if (looksRateLimited) {
            long resetEpoch = resetHeader != null ? Long.parseLong(resetHeader)
                    : (System.currentTimeMillis() / 1000L) + (retryAfterHeader != null ? Long.parseLong(retryAfterHeader) : 60);
            log.warn("GitHub API rate limit hit. remaining={}, resetEpoch={}", remainingHeader, resetEpoch);
            throw new GithubRateLimitExceededException(resetEpoch);
        }
    }
}

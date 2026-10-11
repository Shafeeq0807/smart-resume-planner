package com.smartresume.planner.github.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.smartresume.planner.common.config.AppProperties;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.common.util.Texts;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Thin read-only client for the public GitHub REST API (optionally authenticated with GITHUB_TOKEN). */
@Slf4j
@Component
public class GithubClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Repo(String name, String htmlUrl, String description, String language, int stargazersCount,
                       int forksCount, String pushedAt, boolean fork, boolean archived, List<String> topics,
                       long size) { }

    private final RestClient client;
    private final int maxRepos;

    public GithubClient(RestClient.Builder builder, AppProperties props) {
        AppProperties.Github cfg = props.github();
        RestClient.Builder b = builder.baseUrl(cfg.apiBaseUrl())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "smart-resume-planner");
        if (cfg.token() != null && !cfg.token().isBlank()) {
            b.defaultHeader("Authorization", "Bearer " + cfg.token().trim());
        }
        this.client = b.build();
        this.maxRepos = cfg.maxRepos();
    }

    /** The user's own (non-fork) public repositories, most starred then most recently pushed first. */
    public List<Repo> listRepos(String username) {
        try {
            List<Repo> repos = client.get()
                    .uri("/users/{u}/repos?per_page=100&sort=pushed&type=owner", username)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Repo>>() { });
            if (repos == null) {
                return List.of();
            }
            return repos.stream().filter(r -> !r.fork())
                    .sorted(Comparator.comparingInt(Repo::stargazersCount).reversed()
                            .thenComparing(r -> r.pushedAt() == null ? "" : r.pushedAt(), Comparator.reverseOrder()))
                    .limit(maxRepos).toList();
        } catch (HttpClientErrorException e) {
            throw translate(e, username);
        } catch (RestClientException e) {
            log.warn("GitHub request failed: {}", e.toString());
            throw ApiException.unavailable("GITHUB_UNAVAILABLE", "Could not reach GitHub right now");
        }
    }

    public Map<String, Long> languages(String owner, String repo) {
        try {
            Map<String, Long> body = client.get().uri("/repos/{o}/{r}/languages", owner, repo).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Long>>() { });
            return body == null ? Map.of() : body;
        } catch (RestClientException e) {
            log.debug("No languages for {}/{}: {}", owner, repo, e.getMessage());
            return Map.of();
        }
    }

    /** Raw README text (max 20k chars) or null if the repo has none. */
    public String readme(String owner, String repo) {
        try {
            String body = client.get().uri("/repos/{o}/{r}/readme", owner, repo)
                    .accept(MediaType.parseMediaType("application/vnd.github.raw+json"))
                    .retrieve().body(String.class);
            return Texts.truncate(body, 20_000);
        } catch (RestClientException e) {
            return null;
        }
    }

    private ApiException translate(HttpClientErrorException e, String username) {
        HttpStatusCode status = e.getStatusCode();
        if (status.value() == 404) {
            return ApiException.notFound("GitHub user \"" + username + "\"");
        }
        if (status.value() == 403 || status.value() == 429) {
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_RATE_LIMITED",
                    "GitHub's rate limit was reached. Set a GITHUB_TOKEN in the backend config and try again.");
        }
        log.warn("GitHub returned {} for user {}", status, username);
        return ApiException.unavailable("GITHUB_UNAVAILABLE", "GitHub returned an unexpected error");
    }
}

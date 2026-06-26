package com.codereviewx.backend.review.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class DefaultGithubPrMetadataHttpClient implements GithubPrMetadataHttpClient {

    private final ObjectMapper objectMapper;

    public DefaultGithubPrMetadataHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public GithubPrMetadataHttpResponse fetchPullRequest(String apiBaseUrl,
                                                         GithubRepositoryRef repository,
                                                         int prNumber,
                                                         String token,
                                                         int timeoutSeconds) {
        int timeout = Math.max(1, timeoutSeconds);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pullRequestUrl(apiBaseUrl, repository, prNumber)))
                .timeout(Duration.ofSeconds(timeout))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "CodeReviewX")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean rateLimited = response.headers()
                    .firstValue("x-ratelimit-remaining")
                    .map("0"::equals)
                    .orElse(false);
            GithubPullRequestResponse body = null;
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                body = objectMapper.readValue(response.body(), GithubPullRequestResponse.class);
            }
            return new GithubPrMetadataHttpResponse(response.statusCode(), rateLimited, body);
        } catch (JsonProcessingException ex) {
            throw new GithubPrMetadataClientException("GitHub PR metadata response could not be parsed", ex);
        } catch (IOException ex) {
            throw new GithubPrMetadataClientException("GitHub PR metadata request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GithubPrMetadataClientException("GitHub PR metadata request was interrupted", ex);
        }
    }

    private static String pullRequestUrl(String apiBaseUrl, GithubRepositoryRef repository, int prNumber) {
        return normalizeBaseUrl(apiBaseUrl)
                + "/repos/"
                + encode(repository.owner())
                + "/"
                + encode(repository.repo())
                + "/pulls/"
                + prNumber;
    }

    private static String normalizeBaseUrl(String apiBaseUrl) {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            return "https://api.github.com";
        }
        return apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

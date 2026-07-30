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
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class DefaultGithubPrCommentHttpClient implements GithubPrCommentHttpClient {

    private final ObjectMapper objectMapper;

    public DefaultGithubPrCommentHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public GithubPrCommentHttpResponse publishPullRequestComment(String apiBaseUrl,
                                                                 GithubPrCommentPublishRequest publishRequest,
                                                                 String token,
                                                                 int timeoutSeconds) {
        int timeout = Math.max(1, timeoutSeconds);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pullRequestCommentsUrl(apiBaseUrl, publishRequest)))
                .timeout(Duration.ofSeconds(timeout))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "CodeReviewX")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toRequestBody(publishRequest)))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean rateLimited = response.headers()
                    .firstValue("x-ratelimit-remaining")
                    .map("0"::equals)
                    .orElse(false);
            Long commentId = null;
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                GithubPrCommentResponse body = objectMapper.readValue(response.body(), GithubPrCommentResponse.class);
                commentId = body.getId();
            }
            return new GithubPrCommentHttpResponse(response.statusCode(), rateLimited, commentId);
        } catch (JsonProcessingException ex) {
            throw new GithubPrCommentClientException("GitHub PR comment response could not be parsed", ex);
        } catch (IOException ex) {
            throw new GithubPrCommentClientException("GitHub PR comment request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GithubPrCommentClientException("GitHub PR comment request was interrupted", ex);
        }
    }

    @Override
    public GithubPrCommentHttpResponse upsertPullRequestComment(String apiBaseUrl,
                                                                GithubPrCommentPublishRequest publishRequest,
                                                                String token,
                                                                int timeoutSeconds) {
        String marker = extractMarker(publishRequest.body());
        if (marker == null) {
            return publishPullRequestComment(apiBaseUrl, publishRequest, token, timeoutSeconds);
        }
        int timeout = Math.max(1, timeoutSeconds);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout)).build();
        try {
            HttpRequest list = requestBuilder(pullRequestCommentsUrl(apiBaseUrl, publishRequest)
                    + "?per_page=100", token, timeout).GET().build();
            HttpResponse<String> listed = client.send(list, HttpResponse.BodyHandlers.ofString());
            if (listed.statusCode() < 200 || listed.statusCode() >= 300) {
                return response(listed);
            }
            JsonNode rows = objectMapper.readTree(listed.body());
            if (rows.isArray()) {
                for (JsonNode row : rows) {
                    if (row.path("body").asText("").contains(marker) && row.path("id").canConvertToLong()) {
                        long id = row.path("id").longValue();
                        String url = normalizeBaseUrl(apiBaseUrl) + "/repos/" + encode(publishRequest.owner())
                                + "/" + encode(publishRequest.repo()) + "/pulls/comments/" + id;
                        String body = objectMapper.writeValueAsString(Map.of("body", publishRequest.body()));
                        HttpRequest update = requestBuilder(url, token, timeout)
                                .header("Content-Type", "application/json")
                                .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
                        return response(client.send(update, HttpResponse.BodyHandlers.ofString()));
                    }
                }
            }
            return publishPullRequestComment(apiBaseUrl, publishRequest, token, timeout);
        } catch (JsonProcessingException ex) {
            throw new GithubPrCommentClientException("GitHub PR comment response could not be parsed", ex);
        } catch (IOException ex) {
            throw new GithubPrCommentClientException("GitHub PR comment request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GithubPrCommentClientException("GitHub PR comment request was interrupted", ex);
        }
    }

    private String toRequestBody(GithubPrCommentPublishRequest publishRequest) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("body", publishRequest.body());
        body.put("commit_id", publishRequest.commitId());
        body.put("path", publishRequest.path());
        body.put("side", publishRequest.side() == null || publishRequest.side().isBlank()
                ? "RIGHT"
                : publishRequest.side());
        body.put("line", publishRequest.line());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new GithubPrCommentClientException("GitHub PR comment request could not be serialized", ex);
        }
    }

    private HttpRequest.Builder requestBuilder(String url, String token, int timeout) {
        return HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(timeout))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "CodeReviewX")
                .header("Authorization", "Bearer " + token);
    }

    private GithubPrCommentHttpResponse response(HttpResponse<String> response) {
        boolean rateLimited = response.headers().firstValue("x-ratelimit-remaining")
                .map("0"::equals).orElse(false);
        Long commentId = null;
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                JsonNode body = objectMapper.readTree(response.body());
                if (body.path("id").canConvertToLong()) commentId = body.path("id").longValue();
            } catch (JsonProcessingException ex) {
                throw new GithubPrCommentClientException(
                        "GitHub PR comment response could not be parsed", ex);
            }
        }
        return new GithubPrCommentHttpResponse(response.statusCode(), rateLimited, commentId);
    }

    private String extractMarker(String body) {
        if (body == null) return null;
        int start = body.indexOf("<!-- codereviewx-demo:");
        if (start < 0) return null;
        int end = body.indexOf("-->", start);
        return end < 0 ? null : body.substring(start, end + 3);
    }

    private static String pullRequestCommentsUrl(String apiBaseUrl,
                                                 GithubPrCommentPublishRequest publishRequest) {
        return normalizeBaseUrl(apiBaseUrl)
                + "/repos/"
                + encode(publishRequest.owner())
                + "/"
                + encode(publishRequest.repo())
                + "/pulls/"
                + publishRequest.prNumber()
                + "/comments";
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

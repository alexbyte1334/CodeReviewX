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
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

@Component
public class DefaultGithubRepositoryContentHttpClient implements GithubRepositoryContentHttpClient {

    private final ObjectMapper objectMapper;

    public DefaultGithubRepositoryContentHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public GithubRepositoryFileContentHttpResponse fetchFileContent(String apiBaseUrl,
                                                                    GithubRepositoryRef repository,
                                                                    String path,
                                                                    String ref,
                                                                    String token,
                                                                    int timeoutSeconds,
                                                                    int maxBytes) {
        int timeout = Math.max(1, timeoutSeconds);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileContentUrl(apiBaseUrl, repository, path, ref)))
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
            GithubRepositoryFileContent content = null;
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                content = toFileContent(objectMapper.readValue(response.body(), GithubRepositoryContentResponse.class),
                        path, maxBytes);
            }
            return new GithubRepositoryFileContentHttpResponse(response.statusCode(), rateLimited, content);
        } catch (JsonProcessingException ex) {
            throw new GithubPrMetadataClientException("GitHub repository content response could not be parsed", ex);
        } catch (IllegalArgumentException ex) {
            throw new GithubPrMetadataClientException("GitHub repository content response could not be decoded", ex);
        } catch (IOException ex) {
            throw new GithubPrMetadataClientException("GitHub repository content request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GithubPrMetadataClientException("GitHub repository content request was interrupted", ex);
        }
    }

    private static GithubRepositoryFileContent toFileContent(GithubRepositoryContentResponse response,
                                                             String fallbackPath,
                                                             int maxBytes) {
        if (!"file".equalsIgnoreCase(response.getType()) || response.getContent() == null) {
            return null;
        }
        String normalizedContent = response.getContent().replaceAll("\\s+", "");
        byte[] decoded = "base64".equalsIgnoreCase(response.getEncoding())
                ? Base64.getDecoder().decode(normalizedContent)
                : normalizedContent.getBytes(StandardCharsets.UTF_8);
        boolean truncated = decoded.length > maxBytes;
        int length = Math.min(decoded.length, Math.max(0, maxBytes));
        String text = new String(decoded, 0, length, StandardCharsets.UTF_8);
        return new GithubRepositoryFileContent(
                response.getPath() == null ? fallbackPath : response.getPath(),
                text,
                decoded.length,
                truncated
        );
    }

    private static String fileContentUrl(String apiBaseUrl,
                                         GithubRepositoryRef repository,
                                         String path,
                                         String ref) {
        return normalizeBaseUrl(apiBaseUrl)
                + "/repos/"
                + encode(repository.owner())
                + "/"
                + encode(repository.repo())
                + "/contents/"
                + encodePath(path)
                + "?ref="
                + encode(ref);
    }

    private static String normalizeBaseUrl(String apiBaseUrl) {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            return "https://api.github.com";
        }
        return apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
    }

    private static String encodePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return Arrays.stream(path.replace("\\", "/").split("/"))
                .map(DefaultGithubRepositoryContentHttpClient::encode)
                .collect(Collectors.joining("/"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

package com.codereviewx.backend.review.github;

import java.net.URI;
import java.net.URISyntaxException;

public final class GithubRepositoryParser {

    private GithubRepositoryParser() {
    }

    public static GithubRepositoryRef parse(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new GithubRepositoryParseException("GitHub repository URL is required");
        }

        String trimmed = repoUrl.trim();
        if (trimmed.startsWith("git@github.com:")) {
            return parsePath(trimmed.substring("git@github.com:".length()));
        }

        try {
            URI uri = new URI(trimmed);
            if (uri.getHost() == null || !"github.com".equalsIgnoreCase(uri.getHost())) {
                throw new GithubRepositoryParseException("Only github.com repository URLs are supported");
            }
            return parsePath(uri.getPath());
        } catch (URISyntaxException ex) {
            throw new GithubRepositoryParseException("Invalid GitHub repository URL", ex);
        }
    }

    private static GithubRepositoryRef parsePath(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }

        String[] parts = path.split("/");
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new GithubRepositoryParseException("GitHub repository URL must include owner and repo");
        }
        return new GithubRepositoryRef(parts[0], parts[1]);
    }
}

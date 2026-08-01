package com.codereviewx.backend.config;

import com.codereviewx.backend.demo.DemoProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class PublicDemoConfigurationValidator implements InitializingBean {

    private static final Pattern HEAD_SHA = Pattern.compile("[0-9a-fA-F]{40}");
    private static final Pattern GITHUB_REPOSITORY =
            Pattern.compile("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?");

    private final DeploymentModeProperties deployment;
    private final DemoProperties demo;

    public PublicDemoConfigurationValidator(DeploymentModeProperties deployment, DemoProperties demo) {
        this.deployment = deployment;
        this.demo = demo;
    }

    @Override
    public void afterPropertiesSet() {
        if (!deployment.isPublicDemo()) return;
        require(demo.isEnabled(), "Public demo must be enabled");
        require(demo.getScenarioId() != null && !demo.getScenarioId().isBlank(),
                "Public demo scenario must be configured");
        require(demo.getRepoUrl() != null && GITHUB_REPOSITORY.matcher(demo.getRepoUrl()).matches(),
                "Public demo repository must be a canonical GitHub HTTPS URL");
        require(demo.getPrNumber() > 0, "Public demo PR number must be positive");
        require(demo.getExpectedHeadSha() != null && HEAD_SHA.matcher(demo.getExpectedHeadSha()).matches(),
                "Public demo expected head SHA must contain 40 hexadecimal characters");
        require(strongSecret(demo.getAdminToken()), "Public demo admin token must contain at least 32 characters");
        require(strongSecret(demo.getIpHashSalt()) && !demo.getIpHashSalt().toLowerCase().contains("change-me"),
                "Public demo IP hash salt must be non-default and contain at least 32 characters");
        validateOrigins(demo.getAllowedOrigins());
    }

    private static boolean strongSecret(String value) {
        if (value == null || value.length() < 32 || value.isBlank()) return false;
        String normalized = value.toLowerCase();
        return !value.contains("<") && !value.contains(">")
                && !normalized.contains("your-") && !normalized.contains("replace-");
    }

    private static void validateOrigins(List<String> origins) {
        require(origins != null && !origins.isEmpty(), "Public demo CORS origins must be configured");
        for (String origin : origins) {
            require(origin != null && !origin.isBlank() && !origin.contains("*"),
                    "Public demo CORS origins must not be blank or contain wildcards");
            URI uri;
            try {
                uri = URI.create(origin);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Public demo CORS origin is invalid", exception);
            }
            boolean local = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            require(local || "https".equalsIgnoreCase(uri.getScheme()),
                    "Public demo CORS origins must use HTTPS except for local development");
            require(uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                            && (uri.getPath() == null || uri.getPath().isEmpty()),
                    "Public demo CORS origins must contain only scheme, host, and optional port");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

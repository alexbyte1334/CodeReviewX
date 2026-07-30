package com.codereviewx.backend.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The original numeric run endpoints predate the public demo boundary.
 * Publishing through them is owner-only as well; read-only compatibility stays intact.
 */
@Component
public class LegacyPublishProtectionFilter extends OncePerRequestFilter {
    private final DemoProperties properties;

    public LegacyPublishProtectionFilter(DemoProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !request.getRequestURI().matches("/api/review-runs/\\d+/comment-previews(?:/\\d+)?/publish(?:-selected)?");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String supplied = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
        String configured = properties.getAdminToken() == null ? "" : properties.getAdminToken();
        if (configured.isBlank() || !MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"Owner authorization is required\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}

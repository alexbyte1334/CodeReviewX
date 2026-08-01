package com.codereviewx.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
public class PublicDemoEndpointFilter extends OncePerRequestFilter {

    private static final String UUID =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";
    private static final Pattern DEMO_SNAPSHOT = Pattern.compile("^/api/demo-runs/" + UUID + "$");
    private static final Pattern DEMO_EVENTS = Pattern.compile("^/api/demo-runs/" + UUID + "/events$");
    private static final Pattern DEMO_DECISION = Pattern.compile("^/api/demo-runs/" + UUID + "/decision$");
    private static final Pattern DEMO_PUBLISH =
            Pattern.compile("^/api/admin/demo-runs/" + UUID + "/publish$");

    private final DeploymentModeProperties deployment;

    public PublicDemoEndpointFilter(DeploymentModeProperties deployment) {
        this.deployment = deployment;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !deployment.isPublicDemo() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isAllowed(request.getMethod(), request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"ENDPOINT_NOT_AVAILABLE\",\"data\":null}");
    }

    static boolean isAllowed(String method, String path) {
        if (HttpMethod.OPTIONS.matches(method)) {
            return isPublicPath(path);
        }
        if (HttpMethod.GET.matches(method) && "/api/health".equals(path)) return true;
        if (HttpMethod.POST.matches(method) && "/api/demo-runs".equals(path)) return true;
        if (HttpMethod.GET.matches(method)
                && (DEMO_SNAPSHOT.matcher(path).matches() || DEMO_EVENTS.matcher(path).matches())) return true;
        if (HttpMethod.POST.matches(method) && DEMO_DECISION.matcher(path).matches()) return true;
        return HttpMethod.POST.matches(method) && DEMO_PUBLISH.matcher(path).matches();
    }

    private static boolean isPublicPath(String path) {
        return "/api/health".equals(path)
                || "/api/demo-runs".equals(path)
                || DEMO_SNAPSHOT.matcher(path).matches()
                || DEMO_EVENTS.matcher(path).matches()
                || DEMO_DECISION.matcher(path).matches()
                || DEMO_PUBLISH.matcher(path).matches();
    }
}

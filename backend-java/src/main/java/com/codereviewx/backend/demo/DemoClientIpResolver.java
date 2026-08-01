package com.codereviewx.backend.demo;

import com.codereviewx.backend.config.DeploymentModeProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.Inet6Address;
import java.net.InetAddress;

@Component
public class DemoClientIpResolver {

    private final DeploymentModeProperties deployment;

    public DemoClientIpResolver(DeploymentModeProperties deployment) {
        this.deployment = deployment;
    }

    public String resolve(HttpServletRequest request) {
        if (deployment.isPublicDemo()) {
            String railwayIp = request.getHeader("X-Real-IP");
            if (isIpLiteral(railwayIp)) return railwayIp.trim();
        }
        String remoteAddress = request.getRemoteAddr();
        return isIpLiteral(remoteAddress) ? remoteAddress.trim() : "unknown";
    }

    private static boolean isIpLiteral(String value) {
        if (!StringUtils.hasText(value)) return false;
        String candidate = value.trim();
        if (candidate.matches("(?:\\d{1,3}\\.){3}\\d{1,3}")) {
            for (String octet : candidate.split("\\.")) {
                if (Integer.parseInt(octet) > 255) return false;
            }
            return true;
        }
        if (!candidate.contains(":") || !candidate.matches("[0-9a-fA-F:]{2,45}")) return false;
        try {
            return InetAddress.getByName(candidate) instanceof Inet6Address;
        } catch (Exception ignored) {
            return false;
        }
    }
}

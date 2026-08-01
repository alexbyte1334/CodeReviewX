package com.codereviewx.backend.demo;

import com.codereviewx.backend.config.DeploymentModeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DemoClientIpResolverTest {

    @Test
    void publicDemoUsesRailwayRealIpAndIgnoresForwardedFor() {
        DeploymentModeProperties deployment = new DeploymentModeProperties();
        deployment.setDeploymentMode(DeploymentModeProperties.DeploymentMode.PUBLIC_DEMO);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Real-IP", "203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.1");

        assertThat(new DemoClientIpResolver(deployment).resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void invalidRailwayHeaderFallsBackToConnectionAddress() {
        DeploymentModeProperties deployment = new DeploymentModeProperties();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Real-IP", "999.999.999.999");

        assertThat(new DemoClientIpResolver(deployment).resolve(request)).isEqualTo("10.0.0.2");
    }

    @Test
    void selfHostDoesNotTrustProxyHeaders() {
        DeploymentModeProperties deployment = new DeploymentModeProperties();
        deployment.setDeploymentMode(DeploymentModeProperties.DeploymentMode.SELF_HOST);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.9");

        assertThat(new DemoClientIpResolver(deployment).resolve(request)).isEqualTo("127.0.0.1");
    }
}

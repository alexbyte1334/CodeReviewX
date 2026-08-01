package com.codereviewx.backend.config;

import com.codereviewx.backend.demo.DemoProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicDemoConfigurationValidatorTest {

    @Test
    void acceptsCompletePublicDemoConfiguration() {
        assertThatCode(() -> validator(validDemo(), DeploymentModeProperties.DeploymentMode.PUBLIC_DEMO)
                .afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void rejectsWeakSecretsWildcardOriginsAndInvalidTarget() {
        DemoProperties weakSecret = validDemo();
        weakSecret.setAdminToken("short");
        assertThatThrownBy(() -> validator(weakSecret, DeploymentModeProperties.DeploymentMode.PUBLIC_DEMO)
                .afterPropertiesSet()).hasMessageContaining("admin token");

        DemoProperties wildcard = validDemo();
        wildcard.setAllowedOrigins(List.of("*"));
        assertThatThrownBy(() -> validator(wildcard, DeploymentModeProperties.DeploymentMode.PUBLIC_DEMO)
                .afterPropertiesSet()).hasMessageContaining("wildcards");

        DemoProperties invalidSha = validDemo();
        invalidSha.setExpectedHeadSha("abc123");
        assertThatThrownBy(() -> validator(invalidSha, DeploymentModeProperties.DeploymentMode.PUBLIC_DEMO)
                .afterPropertiesSet()).hasMessageContaining("40 hexadecimal");
    }

    @Test
    void selfHostSkipsPinnedDemoRequirements() {
        assertThatCode(() -> validator(new DemoProperties(), DeploymentModeProperties.DeploymentMode.SELF_HOST)
                .afterPropertiesSet()).doesNotThrowAnyException();
    }

    private static PublicDemoConfigurationValidator validator(
            DemoProperties demo, DeploymentModeProperties.DeploymentMode mode) {
        DeploymentModeProperties deployment = new DeploymentModeProperties();
        deployment.setDeploymentMode(mode);
        return new PublicDemoConfigurationValidator(deployment, demo);
    }

    private static DemoProperties validDemo() {
        DemoProperties demo = new DemoProperties();
        demo.setEnabled(true);
        demo.setScenarioId("sql-injection-pr");
        demo.setRepoUrl("https://github.com/alexbyte1334/CodeReviewX-DemoTarget");
        demo.setPrNumber(1);
        demo.setExpectedHeadSha("d5aa95a3f43f23ca438e53e94c4d3bed4868904a");
        demo.setAdminToken("0123456789abcdef0123456789abcdef");
        demo.setIpHashSalt("abcdef0123456789abcdef0123456789");
        demo.setAllowedOrigins(List.of("https://alexbyte1334.github.io", "http://localhost:5173"));
        return demo;
    }
}

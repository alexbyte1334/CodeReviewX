package com.codereviewx.backend.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoRedactorTest {

    @Test
    void redactsCommonCredentialShapesWithoutRemovingSafeCounts() {
        String fakeBearer = "ghp_" + "abcdef1234567890abcdef";
        String safe = DemoRedactor.sanitize(
                "Authorization: " + "Bearer " + fakeBearer + " token=plain "
                        + "api_key=vendor-secret password=hunter2 matches=3",
                1900);

        assertThat(safe)
                .doesNotContain(fakeBearer, "plain", "vendor-secret", "hunter2")
                .contains("Authorization: Bearer [redacted]")
                .contains("matches=3");
    }

    @Test
    void boundsPublicTraceText() {
        assertThat(DemoRedactor.sanitize("x".repeat(25), 10))
                .isEqualTo("xxxxxxxxxx…");
    }
}

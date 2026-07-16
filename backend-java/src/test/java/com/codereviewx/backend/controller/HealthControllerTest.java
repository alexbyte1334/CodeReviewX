package com.codereviewx.backend.controller;

import com.codereviewx.backend.rag.health.DeliveryReadinessService;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void reportsDependencyReadinessWithoutPreventingLiveness() {
        XiaomiMiMoProperties mimo = new XiaomiMiMoProperties();
        DeliveryReadinessService readiness = () -> new DeliveryReadinessService.Snapshot(
                true, true, false, false);

        Map<String, Object> data = new HealthController(mimo, readiness).health().getData();

        assertThat(data).containsEntry("status", "UP");
        assertThat(data).containsEntry("ragReady", false);
        assertThat(data.get("dependencies")).isEqualTo(Map.of(
                "database", "UP",
                "github", "UP",
                "embedding", "DOWN",
                "rerank", "DOWN"));
    }
}

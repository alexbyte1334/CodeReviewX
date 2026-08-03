package com.codereviewx.backend.controller;

import com.codereviewx.backend.common.ApiResponse;
import com.codereviewx.backend.rag.health.DeliveryReadinessService;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final XiaomiMiMoProperties mimoProperties;
    private final DeliveryReadinessService readiness;

    public HealthController(XiaomiMiMoProperties mimoProperties, DeliveryReadinessService readiness) {
        this.mimoProperties = mimoProperties;
        this.readiness = readiness;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "backend-java");
        data.put("reviewProvider", mimoProperties.getProvider());
        data.put("modelProvider", mimoProperties.getProvider());
        data.put("modelName", mimoProperties.getModel());
        data.put("modelConfigured", mimoProperties.hasRoleApiKeys());
        data.put("mimoConfigured", mimoProperties.hasRoleApiKeys());
        DeliveryReadinessService.Snapshot snapshot = readiness.snapshot();
        data.put("ragReady", snapshot.ragReady());
        data.put("dependencies", Map.of(
                "database", state(snapshot.database()),
                "github", state(snapshot.github()),
                "mimo", state(mimoProperties.hasRoleApiKeys()),
                "embedding", state(snapshot.embedding()),
                "rerank", state(snapshot.rerank())));
        return ApiResponse.success(data);
    }

    private static String state(boolean ready) {
        return ready ? "UP" : "DOWN";
    }
}

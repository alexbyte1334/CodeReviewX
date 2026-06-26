package com.codereviewx.backend.controller;

import com.codereviewx.backend.common.ApiResponse;
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

    public HealthController(XiaomiMiMoProperties mimoProperties) {
        this.mimoProperties = mimoProperties;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "backend-java");
        data.put("reviewProvider", "mimo");
        data.put("mimoConfigured", mimoProperties.hasRoleApiKeys());
        return ApiResponse.success(data);
    }
}

package com.codereviewx.backend.review.pipeline.provider;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModelProviderPresets {
    private ModelProviderPresets() { }

    public static Map<String, Map<String, String>> all() {
        Map<String, Map<String, String>> presets = new LinkedHashMap<>();
        presets.put("openai", Map.of("label", "OpenAI", "baseUrl", "https://api.openai.com/v1", "model", "gpt-4o-mini"));
        presets.put("deepseek", Map.of("label", "DeepSeek", "baseUrl", "https://api.deepseek.com/v1", "model", "deepseek-chat"));
        presets.put("qwen", Map.of("label", "Qwen", "baseUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1", "model", "qwen-plus"));
        presets.put("moonshot", Map.of("label", "Moonshot", "baseUrl", "https://api.moonshot.cn/v1", "model", "moonshot-v1-8k"));
        presets.put("zhipu", Map.of("label", "智谱", "baseUrl", "https://open.bigmodel.cn/api/paas/v4", "model", "glm-4-flash"));
        presets.put("mimo", Map.of("label", "Legacy compatible model", "baseUrl", "https://api.xiaomimimo.com/v1", "model", "mimo-v2.5-pro"));
        presets.put("custom", Map.of("label", "Custom OpenAI-compatible", "baseUrl", "", "model", ""));
        return presets;
    }
}

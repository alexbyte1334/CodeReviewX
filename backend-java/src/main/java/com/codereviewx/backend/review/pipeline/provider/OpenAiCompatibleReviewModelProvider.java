package com.codereviewx.backend.review.pipeline.provider;

import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoClient;
import org.springframework.stereotype.Component;

/** OpenAI-compatible model adapter; the legacy HTTP client supplies the bounded retry/JSON contract. */
@Component
public class OpenAiCompatibleReviewModelProvider implements ReviewModelProvider {
    private final XiaomiMiMoClient client;

    public OpenAiCompatibleReviewModelProvider(XiaomiMiMoClient client) {
        this.client = client;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return client.complete(systemPrompt, userPrompt);
    }
}

package com.codereviewx.backend.review.pipeline.provider.mimo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * HTTP adapter for Xiaomi MiMo OpenAI-compatible chat completions.
 * Never logs API keys, authorization headers, or raw request/response bodies.
 */
@Component
public class XiaomiMiMoClient {

    private static final double DEFAULT_TEMPERATURE = 0.2;

    private final RestClient restClient;
    private final XiaomiMiMoProperties properties;

    @Autowired
    public XiaomiMiMoClient(XiaomiMiMoProperties properties) {
        this.properties = properties;
        this.restClient = buildRestClient(properties);
    }

    private static RestClient buildRestClient(XiaomiMiMoProperties properties) {
        int timeoutMs = Math.max(1, properties.getTimeoutSeconds()) * 1000;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    XiaomiMiMoClient(XiaomiMiMoProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public String complete(String systemPrompt, String userPrompt) {
        if (!properties.hasApiKey()) {
            throw new XiaomiMiMoClientException("MiMo API key is not configured");
        }

        XiaomiMiMoClientRequest request = new XiaomiMiMoClientRequest(
                properties.getModel(),
                List.of(
                        new XiaomiMiMoClientRequest.Message("system", systemPrompt),
                        new XiaomiMiMoClientRequest.Message("user", userPrompt)
                ),
                DEFAULT_TEMPERATURE
        );

        String url = normalizeBaseUrl(properties.getBaseUrl()) + "/chat/completions";

        try {
            XiaomiMiMoClientResponse response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(XiaomiMiMoClientResponse.class);

            return extractContent(response);
        } catch (RestClientResponseException ex) {
            throw new XiaomiMiMoClientException(
                    "MiMo API returned HTTP " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new XiaomiMiMoClientException("MiMo API request failed", ex);
        }
    }

    private static String extractContent(XiaomiMiMoClientResponse response) {
        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()) {
            throw new XiaomiMiMoClientException("MiMo API returned an empty response");
        }

        XiaomiMiMoClientResponse.Message message = response.getChoices().get(0).getMessage();
        if (message == null || message.getContent() == null || message.getContent().isBlank()) {
            throw new XiaomiMiMoClientException("MiMo API returned empty assistant content");
        }

        return message.getContent().trim();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new XiaomiMiMoClientException("MiMo base URL is not configured");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}

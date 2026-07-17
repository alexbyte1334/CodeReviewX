package com.codereviewx.backend.rag.config;

import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import com.codereviewx.backend.rag.embedding.OpenAiEmbeddingClient;
import com.codereviewx.backend.rag.retrieval.HttpRerankClient;
import com.codereviewx.backend.rag.retrieval.RerankClient;
import com.codereviewx.backend.rag.retrieval.RagContextAssembler;
import com.codereviewx.backend.rag.service.RagMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RagProperties.class)
public class RagConfiguration {

    public RagConfiguration(RagProperties properties) {
        properties.validate();
    }

    @Bean
    @ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
    EmbeddingClient embeddingClient(RagProperties properties, ObjectMapper objectMapper, RagMetricsService metrics) {
        return new OpenAiEmbeddingClient(properties, objectMapper, metrics);
    }

    @Bean
    @ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
    RerankClient rerankClient(RagProperties properties, ObjectMapper objectMapper, RagMetricsService metrics) {
        return new HttpRerankClient(properties, objectMapper, metrics);
    }

    @Bean
    @ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
    RagContextAssembler ragContextAssembler(RerankClient rerankClient, RagMetricsService metrics) {
        return new RagContextAssembler(rerankClient, metrics);
    }
}

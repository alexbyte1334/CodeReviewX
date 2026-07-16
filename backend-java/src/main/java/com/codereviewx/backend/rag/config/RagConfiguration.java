package com.codereviewx.backend.rag.config;

import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import com.codereviewx.backend.rag.embedding.OpenAiEmbeddingClient;
import com.codereviewx.backend.rag.retrieval.HttpRerankClient;
import com.codereviewx.backend.rag.retrieval.RerankClient;
import com.codereviewx.backend.rag.retrieval.RagContextAssembler;
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
    EmbeddingClient embeddingClient(RagProperties properties, ObjectMapper objectMapper) {
        return new OpenAiEmbeddingClient(properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
    RerankClient rerankClient(RagProperties properties, ObjectMapper objectMapper) {
        return new HttpRerankClient(properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
    RagContextAssembler ragContextAssembler(RerankClient rerankClient) {
        return new RagContextAssembler(rerankClient);
    }
}

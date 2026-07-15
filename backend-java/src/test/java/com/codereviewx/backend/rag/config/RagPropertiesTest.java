package com.codereviewx.backend.rag.config;

import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import com.codereviewx.backend.rag.retrieval.RerankClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(RagConfiguration.class);

    @Test
    void defaultsMatchV1ContractAndToStringRedactsKeysAndEndpoints() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingApiKey("embedding-secret");
        properties.setRerankApiKey("rerank-secret");
        properties.setEmbeddingBaseUrl("https://embedding.private.example");
        properties.setRerankBaseUrl("https://rerank.private.example");

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getEmbeddingModel()).isEqualTo("BAAI/bge-m3");
        assertThat(properties.getEmbeddingDimensions()).isEqualTo(1024);
        assertThat(properties.getEmbeddingBatchSize()).isEqualTo(32);
        assertThat(properties.getRerankModel()).isEqualTo("BAAI/bge-reranker-v2-m3");
        assertThat(properties.getTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.getMaxRetries()).isEqualTo(2);
        assertThat(properties.toString())
                .doesNotContain("embedding-secret", "rerank-secret", "embedding.private", "rerank.private");
    }

    @Test
    void disabledAllowsMissingEndpointsAndKeysAndDoesNotCreateClients() {
        contextRunner.withPropertyValues("codereviewx.rag.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(EmbeddingClient.class);
            assertThat(context).doesNotHaveBean(RerankClient.class);
        });
    }

    @Test
    void enabledRequiresEveryEndpointWithoutLeakingConfiguredSecrets() {
        contextRunner.withPropertyValues(
                        "codereviewx.rag.enabled=true",
                        "codereviewx.rag.embedding-api-key=never-print-embedding",
                        "codereviewx.rag.rerank-api-key=never-print-rerank")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootMessage(context.getStartupFailure()))
                            .contains("endpoint")
                            .doesNotContain("never-print-embedding", "never-print-rerank");
                });
    }

    @Test
    void enabledRequiresEveryApiKey() {
        contextRunner.withPropertyValues(
                        "codereviewx.rag.enabled=true",
                        "codereviewx.rag.embedding-base-url=https://embedding.example/v1",
                        "codereviewx.rag.rerank-base-url=https://rerank.example/v1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootMessage(context.getStartupFailure())).contains("API keys");
                });
    }

    @Test
    void enabledRejectsMalformedEndpointWithoutEchoingIt() {
        completeEnabledContext()
                .withPropertyValues("codereviewx.rag.embedding-base-url=private-malformed-endpoint")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootMessage(context.getStartupFailure()))
                            .contains("endpoint")
                            .doesNotContain("private-malformed-endpoint");
                });
    }

    @Test
    void enabledWithCompleteConfigurationCreatesBothClients() {
        completeEnabledContext().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EmbeddingClient.class);
            assertThat(context).hasSingleBean(RerankClient.class);
        });
    }

    @Test
    void startupRejectsNonV1DimensionAndInvalidOperationalLimits() {
        assertStartupFailure("codereviewx.rag.embedding-dimensions=768", "1024");
        assertStartupFailure("codereviewx.rag.embedding-batch-size=0", "batch");
        assertStartupFailure("codereviewx.rag.timeout-seconds=0", "timeout");
        assertStartupFailure("codereviewx.rag.max-retries=-1", "retries");
    }

    private ApplicationContextRunner completeEnabledContext() {
        return contextRunner.withPropertyValues(
                "codereviewx.rag.enabled=true",
                "codereviewx.rag.embedding-base-url=https://embedding.example/v1",
                "codereviewx.rag.embedding-api-key=embedding-secret",
                "codereviewx.rag.rerank-base-url=https://rerank.example/v1",
                "codereviewx.rag.rerank-api-key=rerank-secret");
    }

    private void assertStartupFailure(String property, String expectedMessage) {
        completeEnabledContext().withPropertyValues(property).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessage(context.getStartupFailure()))
                    .containsIgnoringCase(expectedMessage)
                    .doesNotContain("embedding-secret", "rerank-secret");
        });
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        StringBuilder messages = new StringBuilder();
        while (current != null) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return messages.toString();
    }
}

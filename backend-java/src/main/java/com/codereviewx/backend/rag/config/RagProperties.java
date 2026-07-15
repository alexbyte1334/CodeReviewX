package com.codereviewx.backend.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codereviewx.rag")
public class RagProperties {

    public static final int V1_EMBEDDING_DIMENSIONS = 1024;

    private boolean enabled;
    private String embeddingBaseUrl = "";
    private String embeddingApiKey = "";
    private String embeddingModel = "BAAI/bge-m3";
    private int embeddingDimensions = V1_EMBEDDING_DIMENSIONS;
    private int embeddingBatchSize = 32;
    private String rerankBaseUrl = "";
    private String rerankApiKey = "";
    private String rerankModel = "BAAI/bge-reranker-v2-m3";
    private int timeoutSeconds = 30;
    private int maxRetries = 2;

    public void validate() {
        if (embeddingDimensions != V1_EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException("RAG embedding dimensions must be 1024 for the V1 schema");
        }
        if (embeddingBatchSize <= 0) {
            throw new IllegalStateException("RAG embedding batch size must be positive");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalStateException("RAG model timeout must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalStateException("RAG model retries must not be negative");
        }
        if (enabled && (isBlank(embeddingBaseUrl) || isBlank(rerankBaseUrl))) {
            throw new IllegalStateException("RAG embedding and rerank endpoints are required when enabled");
        }
        if (enabled && (isBlank(embeddingApiKey) || isBlank(rerankApiKey))) {
            throw new IllegalStateException("RAG embedding and rerank API keys are required when enabled");
        }
        if (enabled && (isBlank(embeddingModel) || isBlank(rerankModel))) {
            throw new IllegalStateException("RAG embedding and rerank models are required when enabled");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmbeddingBaseUrl() {
        return embeddingBaseUrl;
    }

    public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
        this.embeddingBaseUrl = embeddingBaseUrl;
    }

    public String getEmbeddingApiKey() {
        return embeddingApiKey;
    }

    public void setEmbeddingApiKey(String embeddingApiKey) {
        this.embeddingApiKey = embeddingApiKey;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public int getEmbeddingBatchSize() {
        return embeddingBatchSize;
    }

    public void setEmbeddingBatchSize(int embeddingBatchSize) {
        this.embeddingBatchSize = embeddingBatchSize;
    }

    public String getRerankBaseUrl() {
        return rerankBaseUrl;
    }

    public void setRerankBaseUrl(String rerankBaseUrl) {
        this.rerankBaseUrl = rerankBaseUrl;
    }

    public String getRerankApiKey() {
        return rerankApiKey;
    }

    public void setRerankApiKey(String rerankApiKey) {
        this.rerankApiKey = rerankApiKey;
    }

    public String getRerankModel() {
        return rerankModel;
    }

    public void setRerankModel(String rerankModel) {
        this.rerankModel = rerankModel;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public String toString() {
        return "RagProperties{"
                + "enabled=" + enabled
                + ", embeddingModel='" + embeddingModel + '\''
                + ", embeddingDimensions=" + embeddingDimensions
                + ", embeddingBatchSize=" + embeddingBatchSize
                + ", rerankModel='" + rerankModel + '\''
                + ", timeoutSeconds=" + timeoutSeconds
                + ", maxRetries=" + maxRetries
                + '}';
    }
}

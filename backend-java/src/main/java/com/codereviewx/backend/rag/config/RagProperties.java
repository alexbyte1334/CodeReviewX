package com.codereviewx.backend.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

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
    private Path workRoot = Path.of(System.getProperty("user.home"), ".codereviewx", "rag-work");
    private int fetchDepth = 50;
    private long maxFileBytes = 1024L * 1024L;
    private int maxFiles = 5000;
    private long maxTextBytes = 100L * 1024L * 1024L;
    private int maxScannedEntries = 50_000;
    private long maxScannedBytes = 500L * 1024L * 1024L;

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
        if (workRoot == null || fetchDepth <= 0 || maxFileBytes <= 0 || maxFiles <= 0 || maxTextBytes <= 0
                || maxScannedEntries <= 0 || maxScannedBytes <= 0) {
            throw new IllegalStateException("RAG indexing limits must be positive");
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

    public Path getWorkRoot() {
        return workRoot;
    }

    public void setWorkRoot(Path workRoot) {
        this.workRoot = workRoot;
    }

    public int getFetchDepth() {
        return fetchDepth;
    }

    public void setFetchDepth(int fetchDepth) {
        this.fetchDepth = fetchDepth;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public long getMaxTextBytes() {
        return maxTextBytes;
    }

    public void setMaxTextBytes(long maxTextBytes) {
        this.maxTextBytes = maxTextBytes;
    }

    public int getMaxScannedEntries() {
        return maxScannedEntries;
    }

    public void setMaxScannedEntries(int maxScannedEntries) {
        this.maxScannedEntries = maxScannedEntries;
    }

    public long getMaxScannedBytes() {
        return maxScannedBytes;
    }

    public void setMaxScannedBytes(long maxScannedBytes) {
        this.maxScannedBytes = maxScannedBytes;
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
                + ", fetchDepth=" + fetchDepth
                + ", maxFileBytes=" + maxFileBytes
                + ", maxFiles=" + maxFiles
                + ", maxTextBytes=" + maxTextBytes
                + ", maxScannedEntries=" + maxScannedEntries
                + ", maxScannedBytes=" + maxScannedBytes
                + '}';
    }
}

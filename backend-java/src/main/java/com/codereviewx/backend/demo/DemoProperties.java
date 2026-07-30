package com.codereviewx.backend.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "codereviewx.demo")
public class DemoProperties {
    private boolean enabled = true;
    private String scenarioId = "sql-injection-pr";
    private String repoUrl = "https://github.com/alexbyte1334/CodeReviewX-DemoTarget";
    private int prNumber;
    private String expectedHeadSha = "";
    private String adminToken = "";
    private String ipHashSalt = "change-me";
    private int requestsPerHour = 3;
    private int concurrentPerIp = 1;
    private int globalConcurrency = 2;
    private Duration leaseDuration = Duration.ofSeconds(120);
    private Duration executionDeadline = Duration.ofSeconds(90);
    private List<String> allowedOrigins = List.of(
            "https://alexbyte1334.github.io",
            "http://localhost:5173"
    );

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public int getPrNumber() { return prNumber; }
    public void setPrNumber(int prNumber) { this.prNumber = prNumber; }
    public String getExpectedHeadSha() { return expectedHeadSha; }
    public void setExpectedHeadSha(String expectedHeadSha) { this.expectedHeadSha = expectedHeadSha; }
    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String adminToken) { this.adminToken = adminToken; }
    public String getIpHashSalt() { return ipHashSalt; }
    public void setIpHashSalt(String ipHashSalt) { this.ipHashSalt = ipHashSalt; }
    public int getRequestsPerHour() { return requestsPerHour; }
    public void setRequestsPerHour(int requestsPerHour) { this.requestsPerHour = requestsPerHour; }
    public int getConcurrentPerIp() { return concurrentPerIp; }
    public void setConcurrentPerIp(int concurrentPerIp) { this.concurrentPerIp = concurrentPerIp; }
    public int getGlobalConcurrency() { return globalConcurrency; }
    public void setGlobalConcurrency(int globalConcurrency) { this.globalConcurrency = globalConcurrency; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public Duration getExecutionDeadline() { return executionDeadline; }
    public void setExecutionDeadline(Duration executionDeadline) { this.executionDeadline = executionDeadline; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public boolean isLiveReady() {
        return enabled && prNumber > 0 && expectedHeadSha != null && !expectedHeadSha.isBlank();
    }
}

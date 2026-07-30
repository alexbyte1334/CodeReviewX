package com.codereviewx.backend.demo;

import com.codereviewx.backend.review.github.GithubPrMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DemoScenarioGuard {
    private final DemoProperties properties;
    private final DemoStore store;

    public DemoScenarioGuard(DemoProperties properties, DemoStore store) {
        this.properties = properties;
        this.store = store;
    }

    public void validate(long reviewRunId, GithubPrMetadata metadata) {
        if (store.findByReviewRunId(reviewRunId).isEmpty()) return;
        String expected = properties.getExpectedHeadSha();
        if (!properties.getRepoUrl().endsWith("/" + metadata.owner() + "/" + metadata.repo())
                || properties.getPrNumber() != metadata.prNumber()
                || expected == null || expected.isBlank()
                || !expected.equalsIgnoreCase(metadata.headSha())) {
            throw new DemoApiException(HttpStatus.CONFLICT, "DEMO_TARGET_DRIFT",
                    "The whitelisted demo pull request no longer matches its pinned head SHA.");
        }
    }
}

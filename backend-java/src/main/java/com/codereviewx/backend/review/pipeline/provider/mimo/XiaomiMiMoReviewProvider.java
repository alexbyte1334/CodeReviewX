package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewProvider;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Xiaomi MiMo AI review provider.
 * Builds prompts, calls MiMo, and parses structured JSON findings.
 */
@Component
public class XiaomiMiMoReviewProvider implements ReviewProvider {

    public static final String PROVIDER_NAME = "XiaomiMiMoReviewProvider";

    private final ReviewPromptBuilder promptBuilder;
    private final XiaomiMiMoClient client;
    private final XiaomiMiMoFindingParser parser;

    public XiaomiMiMoReviewProvider(ReviewPromptBuilder promptBuilder,
                                    XiaomiMiMoClient client,
                                    XiaomiMiMoFindingParser parser) {
        this.promptBuilder = promptBuilder;
        this.client = client;
        this.parser = parser;
    }

    @Override
    public ReviewProviderResult review(ReviewContext context) {
        String userPrompt = promptBuilder.buildUserPrompt(context);
        String modelOutput = client.complete(ReviewPromptBuilder.SYSTEM_PROMPT, userPrompt);
        List<ReviewFinding> findings = parser.parse(modelOutput);
        return new ReviewProviderResult(findings, PROVIDER_NAME, true, null);
    }
}

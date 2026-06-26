package com.codereviewx.backend.review.pipeline.provider;

import com.codereviewx.backend.review.config.ReviewProperties;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewProvider;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoClientException;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoParseException;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoProperties;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoReviewProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Configuration-driven review provider with safe fallback to mock mode.
 */
@Component
@Primary
public class ConfigurableReviewProvider implements ReviewProvider {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableReviewProvider.class);

    private final ReviewProperties reviewProperties;
    private final XiaomiMiMoProperties mimoProperties;
    private final MockReviewProvider mockReviewProvider;
    private final XiaomiMiMoReviewProvider xiaomiMiMoReviewProvider;

    public ConfigurableReviewProvider(ReviewProperties reviewProperties,
                                      XiaomiMiMoProperties mimoProperties,
                                      MockReviewProvider mockReviewProvider,
                                      XiaomiMiMoReviewProvider xiaomiMiMoReviewProvider) {
        this.reviewProperties = reviewProperties;
        this.mimoProperties = mimoProperties;
        this.mockReviewProvider = mockReviewProvider;
        this.xiaomiMiMoReviewProvider = xiaomiMiMoReviewProvider;
    }

    @Override
    public ReviewProviderResult review(ReviewContext context) {
        if (reviewProperties.isMockMode()) {
            return mockReviewProvider.review(context);
        }

        if (!mimoProperties.hasApiKey()) {
            log.warn("Review provider mode is mimo but MIMO_API_KEY is missing; falling back to mock provider");
            return mockReviewProvider.review(context);
        }

        try {
            return xiaomiMiMoReviewProvider.review(context);
        } catch (XiaomiMiMoClientException | XiaomiMiMoParseException ex) {
            log.warn("Xiaomi MiMo review failed; falling back to mock provider: {}", ex.getMessage());
            return mockReviewProvider.review(context);
        } catch (RuntimeException ex) {
            log.warn("Unexpected Xiaomi MiMo review failure; falling back to mock provider: {}", ex.getMessage());
            return mockReviewProvider.review(context);
        }
    }
}

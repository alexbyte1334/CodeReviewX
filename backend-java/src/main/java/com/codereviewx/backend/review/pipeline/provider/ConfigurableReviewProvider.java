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
        String requestedProvider = resolveRequestedProvider(context);

        if (!shouldUseMimo(context)) {
            return annotate(mockReviewProvider.review(context), requestedProvider);
        }

        if (!mimoProperties.hasApiKey()) {
            log.warn("Review provider mode is mimo but MIMO_API_KEY is missing; falling back to mock provider");
            return annotate(mockReviewProvider.review(context), requestedProvider);
        }

        try {
            return annotate(xiaomiMiMoReviewProvider.review(context), requestedProvider);
        } catch (XiaomiMiMoClientException | XiaomiMiMoParseException ex) {
            log.warn("Xiaomi MiMo review failed; falling back to mock provider: {}", ex.getMessage());
            return annotate(mockReviewProvider.review(context), requestedProvider);
        } catch (RuntimeException ex) {
            log.warn("Unexpected Xiaomi MiMo review failure; falling back to mock provider: {}", ex.getMessage());
            return annotate(mockReviewProvider.review(context), requestedProvider);
        }
    }

    private ReviewProviderResult annotate(ReviewProviderResult result, String requestedProvider) {
        String providerUsed = result.getProviderUsed();
        boolean providerHit = requestedProvider != null && requestedProvider.equals(providerUsed);
        return new ReviewProviderResult(
                result.getFindings(),
                result.getProviderName(),
                result.isSuccessful(),
                result.getMessage(),
                requestedProvider,
                providerHit
        );
    }

    private String resolveRequestedProvider(ReviewContext context) {
        String requested = context.getRequestedProvider();
        if (requested != null && !requested.isBlank()) {
            return requested.trim().toLowerCase();
        }
        return reviewProperties.isMimoMode() ? "mimo" : "mock";
    }

    private boolean shouldUseMimo(ReviewContext context) {
        String requested = context.getRequestedProvider();
        if (requested != null && !requested.isBlank()) {
            return "mimo".equalsIgnoreCase(requested.trim());
        }
        return reviewProperties.isMimoMode();
    }
}

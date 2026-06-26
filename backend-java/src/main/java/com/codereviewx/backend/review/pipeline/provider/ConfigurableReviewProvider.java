package com.codereviewx.backend.review.pipeline.provider;

import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewProvider;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoReviewProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * MiMo-only review provider adapter.
 */
@Component
@Primary
public class ConfigurableReviewProvider implements ReviewProvider {

    private final XiaomiMiMoReviewProvider xiaomiMiMoReviewProvider;

    public ConfigurableReviewProvider(XiaomiMiMoReviewProvider xiaomiMiMoReviewProvider) {
        this.xiaomiMiMoReviewProvider = xiaomiMiMoReviewProvider;
    }

    @Override
    public ReviewProviderResult review(ReviewContext context) {
        ReviewProviderResult result = xiaomiMiMoReviewProvider.review(context);
        String providerUsed = result.getProviderUsed();
        boolean providerHit = "mimo".equals(providerUsed);
        return new ReviewProviderResult(
                result.getFindings(),
                result.getProviderName(),
                result.isSuccessful(),
                result.getMessage(),
                "mimo",
                providerHit
        );
    }
}

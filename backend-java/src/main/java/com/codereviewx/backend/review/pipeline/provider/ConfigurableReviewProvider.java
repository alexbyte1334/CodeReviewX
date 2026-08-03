package com.codereviewx.backend.review.pipeline.provider;

import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewProvider;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoReviewProvider;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * OpenAI-compatible review provider adapter. The legacy class name is retained
 * to keep existing serialized trace and test compatibility.
 */
@Component
@Primary
public class ConfigurableReviewProvider implements ReviewProvider {

    private final XiaomiMiMoReviewProvider xiaomiMiMoReviewProvider;
    private final XiaomiMiMoProperties properties;

    public ConfigurableReviewProvider(XiaomiMiMoReviewProvider xiaomiMiMoReviewProvider) {
        this(xiaomiMiMoReviewProvider, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ConfigurableReviewProvider(XiaomiMiMoReviewProvider xiaomiMiMoReviewProvider,
                                      XiaomiMiMoProperties properties) {
        this.xiaomiMiMoReviewProvider = xiaomiMiMoReviewProvider;
        this.properties = properties;
    }

    @Override
    public ReviewProviderResult review(ReviewContext context) {
        ReviewProviderResult result = xiaomiMiMoReviewProvider.review(context);
        String providerUsed = result.getProviderUsed();
        String requestedProvider = properties == null || properties.getProvider() == null
                || properties.getProvider().isBlank() ? "mimo" : properties.getProvider();
        boolean providerHit = providerUsed != null;
        return new ReviewProviderResult(
                result.getFindings(),
                result.getProviderName(),
                result.isSuccessful(),
                result.getMessage(),
                requestedProvider,
                providerHit
        );
    }
}

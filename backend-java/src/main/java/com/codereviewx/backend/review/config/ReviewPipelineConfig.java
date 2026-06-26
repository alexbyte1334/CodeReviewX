package com.codereviewx.backend.review.config;

import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ReviewProperties.class, XiaomiMiMoProperties.class})
public class ReviewPipelineConfig {
}

package com.codereviewx.backend.review.config;

import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoProperties;
import com.codereviewx.backend.review.github.GithubProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ReviewProperties.class, XiaomiMiMoProperties.class, GithubProperties.class})
public class ReviewPipelineConfig {
}

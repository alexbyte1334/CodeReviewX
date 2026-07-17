package com.codereviewx.backend.rag.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DeliveryHealthConfiguration {

    @Bean("database")
    HealthIndicator databaseHealthIndicator(DeliveryReadinessService readiness) {
        return indicator(readiness, Component.DATABASE);
    }

    @Bean("github")
    HealthIndicator githubHealthIndicator(DeliveryReadinessService readiness) {
        return indicator(readiness, Component.GITHUB);
    }

    @Bean("embedding")
    HealthIndicator embeddingHealthIndicator(DeliveryReadinessService readiness) {
        return indicator(readiness, Component.EMBEDDING);
    }

    @Bean("rerank")
    HealthIndicator rerankHealthIndicator(DeliveryReadinessService readiness) {
        return indicator(readiness, Component.RERANK);
    }

    private HealthIndicator indicator(DeliveryReadinessService readiness, Component component) {
        return () -> component.ready(readiness.snapshot())
                ? Health.up().build()
                : Health.down().withDetail("reason", "dependency unavailable or unconfigured").build();
    }

    private enum Component {
        DATABASE { boolean ready(DeliveryReadinessService.Snapshot value) { return value.database(); } },
        GITHUB { boolean ready(DeliveryReadinessService.Snapshot value) { return value.github(); } },
        EMBEDDING { boolean ready(DeliveryReadinessService.Snapshot value) { return value.embedding(); } },
        RERANK { boolean ready(DeliveryReadinessService.Snapshot value) { return value.rerank(); } };

        abstract boolean ready(DeliveryReadinessService.Snapshot value);
    }
}

package com.codereviewx.backend.rag.config;

import com.codereviewx.backend.rag.indexing.CodeChunker;
import com.codereviewx.backend.rag.indexing.LineWindowCodeChunker;
import com.codereviewx.backend.rag.indexing.RagIndexLifecycleCoordinator;
import com.codereviewx.backend.rag.indexing.RagIndexTaskExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public class RagIndexingConfiguration {

    @Bean
    @DependsOn("ragHeartbeatExecutor")
    public RagIndexTaskExecutor ragIndexExecutor(
            RagIndexLifecycleCoordinator coordinator,
            RagProperties properties
    ) {
        RagIndexTaskExecutor executor = new RagIndexTaskExecutor(
                coordinator, Duration.ofSeconds(properties.getShutdownGraceSeconds()));
        executor.setThreadNamePrefix("rag-index-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    @Bean
    CodeChunker ragCodeChunker() {
        return new LineWindowCodeChunker();
    }

    @Bean
    TransactionTemplate ragTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService ragHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rag-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
}

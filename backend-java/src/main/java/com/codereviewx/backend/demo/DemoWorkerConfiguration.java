package com.codereviewx.backend.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class DemoWorkerConfiguration {
    @Bean(destroyMethod = "shutdown")
    ExecutorService demoWorkerExecutor(DemoProperties properties) {
        return Executors.newFixedThreadPool(properties.getGlobalConcurrency(), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("demo-review-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService demoPipelineExecutor(DemoProperties properties) {
        return Executors.newFixedThreadPool(properties.getGlobalConcurrency(), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("demo-review-pipeline");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService demoHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("demo-review-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(name = "taskScheduler", destroyMethod = "shutdown")
    ScheduledExecutorService taskScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("application-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }
}

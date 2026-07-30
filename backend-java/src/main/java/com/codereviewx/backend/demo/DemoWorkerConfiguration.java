package com.codereviewx.backend.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
}

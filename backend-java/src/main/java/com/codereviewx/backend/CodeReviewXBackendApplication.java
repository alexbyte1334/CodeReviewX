package com.codereviewx.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CodeReviewXBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeReviewXBackendApplication.class, args);
    }
}

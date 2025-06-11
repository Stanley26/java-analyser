package com.legacy.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LegacyAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegacyAnalyzerApplication.class, args);
    }
}
package com.dockmind.config;

import com.dockmind.matching.Matcher;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    Matcher matcher(@Value("${matching.review-threshold:0.80}") double reviewThreshold) {
        return new Matcher(reviewThreshold);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService extractionExecutor(@Value("${extractor.threads:2}") int threads) {
        return Executors.newFixedThreadPool(threads);
    }
}

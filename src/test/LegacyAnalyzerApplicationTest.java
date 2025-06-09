package com.legacy.analyzer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.main.web-application-type=none",
    "analyzer.source.root-directory=./test-data"
})
class LegacyAnalyzerApplicationTest {
    
    @Test
    void contextLoads() {
        // Test que le contexte Spring se charge correctement
    }
}
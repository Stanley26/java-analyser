package com.legacy.analyzer.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.time.Duration;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisResult {
    
    private WebLogicApplication application;
    private boolean success;
    private String error;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Duration duration;
    private int endpointsCount;
    private AnalysisMetrics metrics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisMetrics {
        private int classesAnalyzed;
        private int methodsAnalyzed;
        private int filesProcessed;
        private long memoryUsed;
        private double analysisSpeed; // files per second
    }
}
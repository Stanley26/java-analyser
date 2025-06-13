package com.analyzer.model.technical;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnalysisReport {
    public String applicationName;
    public String analysisTimestamp;
    public String sourcePath;
    public Map<String, String> configuration = new ConcurrentHashMap<>();
    public int endpointCount = 0;
    public List<Endpoint> endpoints = new ArrayList<>();

    public AnalysisReport(String applicationName) {
        this.applicationName = applicationName;
        this.analysisTimestamp = Instant.now().toString();
    }

    public AnalysisReport() {}
}

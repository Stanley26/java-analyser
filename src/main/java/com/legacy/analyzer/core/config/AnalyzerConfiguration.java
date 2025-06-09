package com.legacy.analyzer.core.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "analyzer")
public class AnalyzerConfiguration {
    
    @NotNull
    private SourceConfig source = new SourceConfig();
    
    private WebLogicConfig weblogic = new WebLogicConfig();
    
    private AnalysisConfig analysis = new AnalysisConfig();
    
    private OutputConfig output = new OutputConfig();
    
    private PerformanceConfig performance = new PerformanceConfig();
    
    // Runtime configuration
    private Path sourceDirectory;
    private Path outputDirectory;
    private boolean parallelAnalysis = true;
    private boolean deepAnalysis = false;
    private String targetApplication;
    
    @Data
    public static class SourceConfig {
        private String rootDirectory;
        private List<String> includePatterns = List.of("*.ear", "*.war", "*.jar");
        private List<String> excludePatterns = List.of("*-test.ear", "backup/*", "*.bak");
    }
    
    @Data
    public static class WebLogicConfig {
        private boolean analyzeDeployments = true;
        private boolean extractDatasources = true;
    }
    
    @Data
    public static class AnalysisConfig {
        private List<FrameworkConfig> frameworks;
        private DepthConfig depth = new DepthConfig();
        private DatabaseConfig database = new DatabaseConfig();
        private IntegrationsConfig integrations = new IntegrationsConfig();
    }
    
    @Data
    public static class FrameworkConfig {
        private String name;
        private boolean enabled = true;
        private List<String> versions;
    }
    
    @Data
    public static class DepthConfig {
        private int followMethodCalls = 5;
        private boolean analyzeInnerClasses = true;
        private boolean includeAnonymousClasses = false;
    }
    
    @Data
    public static class DatabaseConfig {
        private boolean extractQueries = true;
        private boolean parseSql = true;
        private boolean analyzeStoredProcedures = true;
        private boolean detectDynamicQueries = true;
    }
    
    @Data
    public static class IntegrationsConfig {
        private EJBConfig ejb = new EJBConfig();
        private CobolConfig cobol = new CobolConfig();
        private WebServicesConfig webservices = new WebServicesConfig();
    }
    
    @Data
    public static class EJBConfig {
        private boolean analyzeRemoteCalls = true;
        private boolean trackTransactions = true;
    }
    
    @Data
    public static class CobolConfig {
        private boolean detectSocketCalls = true;
        private boolean detectJni = true;
        private boolean detectFileExchange = true;
    }
    
    @Data
    public static class WebServicesConfig {
        private boolean analyzeSoap = true;
        private boolean analyzeRest = true;
    }
    
    @Data
    public static class OutputConfig {
        private String directory = "./analysis-output";
        private FormatsConfig formats = new FormatsConfig();
        private ReportsConfig reports = new ReportsConfig();
    }
    
    @Data
    public static class FormatsConfig {
        private JsonConfig json = new JsonConfig();
        private ExcelConfig excel = new ExcelConfig();
    }
    
    @Data
    public static class JsonConfig {
        private boolean prettyPrint = true;
        private boolean compress = false;
    }
    
    @Data
    public static class ExcelConfig {
        private boolean includeCharts = true;
        private boolean includeStatistics = true;
    }
    
    @Data
    public static class ReportsConfig {
        private boolean globalSummary = true;
        private boolean perApplication = true;
        private boolean dependencyMatrix = true;
    }
    
    @Data
    public static class PerformanceConfig {
        private boolean parallelAnalysis = true;
        private int maxThreads = 8;
        private String memoryLimit = "4G";
        private int chunkSize = 100;
    }
    
    public void loadFromFile(Path configFile) throws IOException {
        log.info("Chargement de la configuration depuis: {}", configFile);
        // La configuration est automatiquement chargée par Spring Boot
        // Cette méthode peut être utilisée pour des configurations additionnelles
    }
    
    public void validate() {
        if (sourceDirectory != null && !Files.exists(sourceDirectory)) {
            throw new IllegalArgumentException("Le répertoire source n'existe pas: " + sourceDirectory);
        }
        
        if (outputDirectory == null) {
            outputDirectory = Paths.get(output.getDirectory());
        }
        
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            throw new IllegalArgumentException("Impossible de créer le répertoire de sortie: " + outputDirectory, e);
        }
    }
    
    public boolean isFrameworkEnabled(String frameworkName) {
        return analysis.getFrameworks().stream()
                .filter(f -> f.getName().equalsIgnoreCase(frameworkName))
                .findFirst()
                .map(FrameworkConfig::isEnabled)
                .orElse(false);
    }
}
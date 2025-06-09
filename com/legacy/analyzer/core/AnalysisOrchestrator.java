package com.legacy.analyzer.core;

import com.legacy.analyzer.core.config.AnalyzerConfiguration;
import com.legacy.analyzer.extractors.EndpointExtractorManager;
import com.legacy.analyzer.extractors.DependencyExtractorManager;
import com.legacy.analyzer.generators.PseudoCodeGenerator;
import com.legacy.analyzer.generators.ReportGenerator;
import com.legacy.analyzer.model.AnalysisResult;
import com.legacy.analyzer.model.WebLogicApplication;
import com.legacy.analyzer.persistence.ResultsPersistence;
import com.legacy.analyzer.scanner.WebLogicProjectScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisOrchestrator {
    
    private final AnalyzerConfiguration configuration;
    private final WebLogicProjectScanner projectScanner;
    private final EndpointExtractorManager endpointExtractor;
    private final DependencyExtractorManager dependencyExtractor;
    private final PseudoCodeGenerator pseudoCodeGenerator;
    private final ResultsPersistence resultsPersistence;
    private final ReportGenerator reportGenerator;
    
    public void performAnalysis() throws IOException {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("=== Démarrage de l'analyse complète ===");
        log.info("Répertoire source: {}", configuration.getSourceDirectory());
        log.info("Répertoire de sortie: {}", configuration.getOutputDirectory());
        
        try {
            // Validation de la configuration
            configuration.validate();
            
            // Phase 1: Scanner les projets
            log.info("Phase 1: Scan des projets WebLogic...");
            List<WebLogicApplication> applications = projectScanner.scanDirectory(
                configuration.getSourceDirectory()
            );
            
            if (applications.isEmpty()) {
                log.warn("Aucune application trouvée dans le répertoire source");
                return;
            }
            
            log.info("Nombre d'applications trouvées: {}", applications.size());
            
            // Filtrer par application si spécifié
            if (configuration.getTargetApplication() != null) {
                applications = applications.stream()
                        .filter(app -> app.getName().equals(configuration.getTargetApplication()))
                        .collect(Collectors.toList());
                log.info("Analyse limitée à l'application: {}", configuration.getTargetApplication());
            }
            
            // Phase 2: Analyser les applications
            log.info("Phase 2: Analyse des applications...");
            List<AnalysisResult> results;
            
            if (configuration.isParallelAnalysis()) {
                results = analyzeApplicationsParallel(applications);
            } else {
                results = analyzeApplicationsSequential(applications);
            }
            
            // Phase 3: Générer les rapports
            log.info("Phase 3: Génération des rapports...");
            generateReports(results);
            
            // Afficher les statistiques
            LocalDateTime endTime = LocalDateTime.now();
            Duration duration = Duration.between(startTime, endTime);
            displayStatistics(results, duration);
            
            log.info("=== Analyse terminée avec succès ===");
            
        } catch (Exception e) {
            log.error("Erreur fatale lors de l'analyse", e);
            throw new RuntimeException("Analyse échouée", e);
        }
    }
    
    private List<AnalysisResult> analyzeApplicationsSequential(List<WebLogicApplication> applications) {
        List<AnalysisResult> results = new ArrayList<>();
        
        for (int i = 0; i < applications.size(); i++) {
            WebLogicApplication app = applications.get(i);
            log.info("Analyse de l'application {}/{}: {}", i + 1, applications.size(), app.getName());
            
            try {
                AnalysisResult result = analyzeApplication(app);
                results.add(result);
                
                // Sauvegarder au fur et à mesure
                resultsPersistence.saveApplicationResult(result);
                
            } catch (Exception e) {
                log.error("Erreur lors de l'analyse de l'application: {}", app.getName(), e);
                AnalysisResult errorResult = AnalysisResult.builder()
                        .application(app)
                        .success(false)
                        .error(e.getMessage())
                        .build();
                results.add(errorResult);
            }
        }
        
        return results;
    }
    
    private List<AnalysisResult> analyzeApplicationsParallel(List<WebLogicApplication> applications) {
        int threadCount = Math.min(configuration.getPerformance().getMaxThreads(), applications.size());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        log.info("Analyse parallèle avec {} threads", threadCount);
        
        try {
            List<CompletableFuture<AnalysisResult>> futures = applications.stream()
                    .map(app -> CompletableFuture.supplyAsync(() -> {
                        try {
                            log.info("Début de l'analyse de: {}", app.getName());
                            AnalysisResult result = analyzeApplication(app);
                            resultsPersistence.saveApplicationResult(result);
                            return result;
                        } catch (Exception e) {
                            log.error("Erreur lors de l'analyse de: {}", app.getName(), e);
                            return AnalysisResult.builder()
                                    .application(app)
                                    .success(false)
                                    .error(e.getMessage())
                                    .build();
                        }
                    }, executor))
                    .collect(Collectors.toList());
            
            // Attendre la fin de toutes les analyses
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );
            
            allFutures.join();
            
            // Collecter les résultats
            return futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                    
        } finally {
            executor.shutdown();
        }
    }
    
    private AnalysisResult analyzeApplication(WebLogicApplication application) throws Exception {
        log.debug("Analyse détaillée de l'application: {}", application.getName());
        LocalDateTime startTime = LocalDateTime.now();
        
        AnalysisResult.AnalysisResultBuilder resultBuilder = AnalysisResult.builder()
                .application(application)
                .startTime(startTime);
        
        try {
            // Extraction des endpoints
            log.debug("Extraction des endpoints...");
            endpointExtractor.extractEndpoints(application);
            
            // Extraction des dépendances
            log.debug("Extraction des dépendances...");
            dependencyExtractor.extractDependencies(application);
            
            // Génération du pseudo-code
            if (configuration.getOutput().getFormats().getJson().isPrettyPrint()) {
                log.debug("Génération du pseudo-code...");
                pseudoCodeGenerator.generatePseudoCode(application);
            }
            
            // Calcul des statistiques
            application.setStatistics(calculateStatistics(application));
            
            resultBuilder.success(true);
            resultBuilder.endpointsCount(application.getEndpoints() != null ? 
                    application.getEndpoints().size() : 0);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'analyse de l'application: {}", application.getName(), e);
            resultBuilder.success(false);
            resultBuilder.error(e.getMessage());
            throw e;
        } finally {
            resultBuilder.endTime(LocalDateTime.now());
            resultBuilder.duration(Duration.between(startTime, LocalDateTime.now()));
        }
        
        return resultBuilder.build();
    }
    
    private WebLogicApplication.Statistics calculateStatistics(WebLogicApplication application) {
        WebLogicApplication.Statistics.StatisticsBuilder statsBuilder = 
                WebLogicApplication.Statistics.builder();
        
        // Compter les endpoints
        int totalEndpoints = 0;
        if (application.getEndpoints() != null) {
            totalEndpoints = application.getEndpoints().size();
        }
        
        // Compter par framework
        if (application.getModules() != null) {
            for (WebLogicApplication.Module module : application.getModules()) {
                if (module.getEndpoints() != null) {
                    totalEndpoints += module.getEndpoints().size();
                }
            }
        }
        
        statsBuilder.totalEndpoints(totalEndpoints);
        
        // Calculer les dépendances
        if (application.getGlobalDependencies() != null) {
            int dbCount = application.getGlobalDependencies().getDatabases() != null ? 
                    application.getGlobalDependencies().getDatabases().size() : 0;
            int ejbCount = application.getGlobalDependencies().getEjbs() != null ? 
                    application.getGlobalDependencies().getEjbs().size() : 0;
            int cobolCount = application.getGlobalDependencies().getCobolPrograms() != null ? 
                    application.getGlobalDependencies().getCobolPrograms().size() : 0;
            
            statsBuilder.dependenciesByType(java.util.Map.of(
                    "database", dbCount,
                    "ejb", ejbCount,
                    "cobol", cobolCount
            ));
        }
        
        return statsBuilder.build();
    }
    
    public void generateReports(Path inputDir, String format) throws IOException {
        log.info("Génération des rapports depuis: {}", inputDir);
        
        // Charger les résultats
        List<AnalysisResult> results = resultsPersistence.loadAllResults(inputDir);
        
        // Générer les rapports selon le format
        generateReports(results, format);
    }
    
    private void generateReports(List<AnalysisResult> results) throws IOException {
        generateReports(results, "all");
    }
    
    private void generateReports(List<AnalysisResult> results, String format) throws IOException {
        if ("all".equals(format) || "excel".equals(format)) {
            log.info("Génération des rapports Excel...");
            reportGenerator.generateExcelReports(results);
        }
        
        if ("all".equals(format) || "json".equals(format)) {
            log.info("Génération du rapport JSON global...");
            resultsPersistence.saveGlobalResults(results);
        }
    }
    
    private void displayStatistics(List<AnalysisResult> results, Duration totalDuration) {
        log.info("=== Statistiques de l'analyse ===");
        log.info("Durée totale: {} minutes {} secondes", 
                totalDuration.toMinutes(), 
                totalDuration.getSeconds() % 60);
        
        int successCount = (int) results.stream().filter(AnalysisResult::isSuccess).count();
        int failureCount = results.size() - successCount;
        
        log.info("Applications analysées: {}", results.size());
        log.info("Succès: {}", successCount);
        log.info("Échecs: {}", failureCount);
        
        int totalEndpoints = results.stream()
                .filter(AnalysisResult::isSuccess)
                .mapToInt(AnalysisResult::getEndpointsCount)
                .sum();
        
        log.info("Total des endpoints trouvés: {}", totalEndpoints);
        
        // Afficher les applications en erreur
        if (failureCount > 0) {
            log.warn("Applications en erreur:");
            results.stream()
                    .filter(r -> !r.isSuccess())
                    .forEach(r -> log.warn("  - {}: {}", 
                            r.getApplication().getName(), 
                            r.getError()));
        }
    }
}
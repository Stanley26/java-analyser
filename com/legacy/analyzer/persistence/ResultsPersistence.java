package com.legacy.analyzer.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.legacy.analyzer.core.config.AnalyzerConfiguration;
import com.legacy.analyzer.model.AnalysisResult;
import com.legacy.analyzer.model.WebLogicApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResultsPersistence {
    
    private final AnalyzerConfiguration configuration;
    private final ObjectMapper objectMapper;
    
    public ResultsPersistence(AnalyzerConfiguration configuration) {
        this.configuration = configuration;
        this.objectMapper = createObjectMapper();
    }
    
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    
    public void saveApplicationResult(AnalysisResult result) throws IOException {
        WebLogicApplication application = result.getApplication();
        Path appDir = createApplicationDirectory(application.getName());
        
        // Sauvegarder les informations de l'application
        saveApplicationInfo(appDir, application);
        
        // Sauvegarder les endpoints
        if (application.getEndpoints() != null && !application.getEndpoints().isEmpty()) {
            saveEndpoints(appDir, application.getEndpoints());
        }
        
        // Sauvegarder les dépendances
        if (application.getGlobalDependencies() != null) {
            saveDependencies(appDir, application.getGlobalDependencies());
        }
        
        // Sauvegarder les modules
        if (application.getModules() != null) {
            saveModules(appDir, application.getModules());
        }
        
        // Sauvegarder le pseudo-code
        savePseudoCode(appDir, application);
        
        // Sauvegarder les statistiques
        if (application.getStatistics() != null) {
            saveStatistics(appDir, application.getStatistics());
        }
        
        // Mettre à jour l'index
        updateAnalysisIndex(result);
    }
    
    private Path createApplicationDirectory(String applicationName) throws IOException {
        Path appDir = configuration.getOutputDirectory()
                .resolve("applications")
                .resolve(sanitizeFileName(applicationName));
        
        Files.createDirectories(appDir);
        
        // Créer les sous-répertoires
        Files.createDirectories(appDir.resolve("modules"));
        Files.createDirectories(appDir.resolve("database"));
        Files.createDirectories(appDir.resolve("integrations"));
        Files.createDirectories(appDir.resolve("pseudocode"));
        
        return appDir;
    }
    
    private void saveApplicationInfo(Path appDir, WebLogicApplication application) 
            throws IOException {
        Path infoFile = appDir.resolve("application-info.json");
        
        Map<String, Object> info = new HashMap<>();
        info.put("id", application.getId());
        info.put("name", application.getName());
        info.put("version", application.getVersion());
        info.put("type", application.getType());
        info.put("sourcePath", application.getSourcePath());
        info.put("frameworks", application.getFrameworks());
        info.put("deploymentInfo", application.getDeploymentInfo());
        info.put("dataSources", application.getDataSources());
        info.put("libraries", application.getLibraries());
        
        objectMapper.writeValue(infoFile.toFile(), info);
    }
    
    private void saveEndpoints(Path appDir, List<com.legacy.analyzer.model.Endpoint> endpoints) 
            throws IOException {
        Path endpointsFile = appDir.resolve("endpoints.json");
        objectMapper.writeValue(endpointsFile.toFile(), endpoints);
    }
    
    private void saveDependencies(Path appDir, com.legacy.analyzer.model.Dependencies dependencies) 
            throws IOException {
        Path depsFile = appDir.resolve("dependencies.json");
        objectMapper.writeValue(depsFile.toFile(), dependencies);
        
        // Sauvegarder aussi par type
        if (dependencies.getDatabases() != null && !dependencies.getDatabases().isEmpty()) {
            Path dbFile = appDir.resolve("database/queries.json");
            objectMapper.writeValue(dbFile.toFile(), dependencies.getDatabases());
        }
        
        if (dependencies.getEjbs() != null && !dependencies.getEjbs().isEmpty()) {
            Path ejbFile = appDir.resolve("integrations/ejb-calls.json");
            objectMapper.writeValue(ejbFile.toFile(), dependencies.getEjbs());
        }
        
        if (dependencies.getCobolPrograms() != null && !dependencies.getCobolPrograms().isEmpty()) {
            Path cobolFile = appDir.resolve("integrations/cobol-connections.json");
            objectMapper.writeValue(cobolFile.toFile(), dependencies.getCobolPrograms());
        }
        
        if (dependencies.getWebServices() != null && !dependencies.getWebServices().isEmpty()) {
            Path wsFile = appDir.resolve("integrations/webservices.json");
            objectMapper.writeValue(wsFile.toFile(), dependencies.getWebServices());
        }
        
        if (dependencies.getJmsQueues() != null && !dependencies.getJmsQueues().isEmpty()) {
            Path jmsFile = appDir.resolve("integrations/jms-queues.json");
            objectMapper.writeValue(jmsFile.toFile(), dependencies.getJmsQueues());
        }
        
        if (dependencies.getFiles() != null && !dependencies.getFiles().isEmpty()) {
            Path filesFile = appDir.resolve("integrations/file-dependencies.json");
            objectMapper.writeValue(filesFile.toFile(), dependencies.getFiles());
        }
    }
    
    private void saveModules(Path appDir, List<WebLogicApplication.Module> modules) 
            throws IOException {
        for (WebLogicApplication.Module module : modules) {
            Path moduleDir = appDir.resolve("modules").resolve(sanitizeFileName(module.getName()));
            Files.createDirectories(moduleDir);
            
            // Sauvegarder les infos du module
            Map<String, Object> moduleInfo = new HashMap<>();
            moduleInfo.put("name", module.getName());
            moduleInfo.put("type", module.getType());
            moduleInfo.put("contextRoot", module.getContextRoot());
            moduleInfo.put("frameworks", module.getFrameworks());
            
            Path moduleInfoFile = moduleDir.resolve("module-info.json");
            objectMapper.writeValue(moduleInfoFile.toFile(), moduleInfo);
            
            // Sauvegarder les endpoints du module
            if (module.getEndpoints() != null && !module.getEndpoints().isEmpty()) {
                Path endpointsFile = moduleDir.resolve("endpoints.json");
                objectMapper.writeValue(endpointsFile.toFile(), module.getEndpoints());
            }
            
            // Sauvegarder les dépendances du module
            if (module.getDependencies() != null) {
                Path depsFile = moduleDir.resolve("dependencies.json");
                objectMapper.writeValue(depsFile.toFile(), module.getDependencies());
            }
        }
    }
    
    private void savePseudoCode(Path appDir, WebLogicApplication application) 
            throws IOException {
        Path pseudoCodeDir = appDir.resolve("pseudocode");
        
        // Sauvegarder le pseudo-code de tous les endpoints
        List<com.legacy.analyzer.model.Endpoint> allEndpoints = new ArrayList<>();
        
        if (application.getEndpoints() != null) {
            allEndpoints.addAll(application.getEndpoints());
        }
        
        if (application.getModules() != null) {
            for (WebLogicApplication.Module module : application.getModules()) {
                if (module.getEndpoints() != null) {
                    allEndpoints.addAll(module.getEndpoints());
                }
            }
        }
        
        // Grouper par classe
        Map<String, List<com.legacy.analyzer.model.Endpoint>> endpointsByClass = 
                allEndpoints.stream()
                        .filter(e -> e.getClassName() != null)
                        .collect(Collectors.groupingBy(com.legacy.analyzer.model.Endpoint::getClassName));
        
        for (Map.Entry<String, List<com.legacy.analyzer.model.Endpoint>> entry : 
             endpointsByClass.entrySet()) {
            
            String className = entry.getKey();
            List<com.legacy.analyzer.model.Endpoint> classEndpoints = entry.getValue();
            
            Map<String, Object> classPseudoCode = new HashMap<>();
            classPseudoCode.put("className", className);
            
            List<Map<String, Object>> methods = new ArrayList<>();
            for (com.legacy.analyzer.model.Endpoint endpoint : classEndpoints) {
                if (endpoint.getPseudoCode() != null) {
                    Map<String, Object> methodInfo = new HashMap<>();
                    methodInfo.put("methodName", endpoint.getMethodName());
                    methodInfo.put("url", endpoint.getUrl());
                    methodInfo.put("pseudoCode", endpoint.getPseudoCode());
                    methods.add(methodInfo);
                }
            }
            
            classPseudoCode.put("methods", methods);
            
            Path pseudoCodeFile = pseudoCodeDir.resolve(className + ".json");
            objectMapper.writeValue(pseudoCodeFile.toFile(), classPseudoCode);
        }
    }
    
    private void saveStatistics(Path appDir, WebLogicApplication.Statistics statistics) 
            throws IOException {
        Path statsFile = appDir.resolve("statistics.json");
        objectMapper.writeValue(statsFile.toFile(), statistics);
    }
    
    private void updateAnalysisIndex(AnalysisResult result) throws IOException {
        Path indexFile = configuration.getOutputDirectory().resolve("analysis-index.json");
        
        AnalysisIndex index;
        if (Files.exists(indexFile)) {
            index = objectMapper.readValue(indexFile.toFile(), AnalysisIndex.class);
        } else {
            index = new AnalysisIndex();
            index.setAnalysisId("analysis-" + 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            index.setStartTime(LocalDateTime.now());
            index.setApplicationsAnalyzed(new ArrayList<>());
        }
        
        // Ajouter ou mettre à jour l'application
        ApplicationSummary summary = new ApplicationSummary();
        summary.setName(result.getApplication().getName());
        summary.setPath(result.getApplication().getSourcePath().toString());
        summary.setStatus(result.isSuccess() ? "completed" : "failed");
        summary.setEndpointsCount(result.getEndpointsCount());
        summary.setErrors(result.isSuccess() ? Collections.emptyList() : 
                        Collections.singletonList(result.getError()));
        
        // Remplacer si déjà présent
        index.getApplicationsAnalyzed().removeIf(app -> 
                app.getName().equals(summary.getName()));
        index.getApplicationsAnalyzed().add(summary);
        
        // Mettre à jour les totaux
        index.setTotalApplications(index.getApplicationsAnalyzed().size());
        index.setTotalEndpoints(index.getApplicationsAnalyzed().stream()
                .mapToInt(ApplicationSummary::getEndpointsCount)
                .sum());
        
        // Sauvegarder l'index
        objectMapper.writeValue(indexFile.toFile(), index);
    }
    
    public void saveGlobalResults(List<AnalysisResult> results) throws IOException {
        Path globalDir = configuration.getOutputDirectory().resolve("global");
        Files.createDirectories(globalDir);
        
        // Vue d'ensemble de l'écosystème
        saveEcosystemOverview(globalDir, results);
        
        // Graphe de dépendances global
        saveDependenciesGraph(globalDir, results);
        
        // Statistiques globales
        saveGlobalStatistics(globalDir, results);
        
        // Configuration WebLogic globale
        saveWebLogicConfig(globalDir, results);
    }
    
    private void saveEcosystemOverview(Path globalDir, List<AnalysisResult> results) 
            throws IOException {
        Map<String, Object> overview = new HashMap<>();
        
        overview.put("totalApplications", results.size());
        overview.put("successfulAnalyses", results.stream().filter(AnalysisResult::isSuccess).count());
        overview.put("failedAnalyses", results.stream().filter(r -> !r.isSuccess()).count());
        
        // Frameworks utilisés
        Map<String, Integer> frameworkCount = new HashMap<>();
        results.stream()
                .filter(AnalysisResult::isSuccess)
                .map(AnalysisResult::getApplication)
                .flatMap(app -> app.getFrameworks().stream())
                .forEach(framework -> frameworkCount.merge(framework, 1, Integer::sum));
        
        overview.put("frameworksUsed", frameworkCount);
        
        // Types d'applications
        Map<String, Integer> appTypes = new HashMap<>();
        results.stream()
                .filter(AnalysisResult::isSuccess)
                .map(r -> r.getApplication().getType().toString())
                .forEach(type -> appTypes.merge(type, 1, Integer::sum));
        
        overview.put("applicationTypes", appTypes);
        
        Path overviewFile = globalDir.resolve("ecosystem-overview.json");
        objectMapper.writeValue(overviewFile.toFile(), overview);
    }
    
    private void saveDependenciesGraph(Path globalDir, List<AnalysisResult> results) 
            throws IOException {
        Map<String, Object> graph = new HashMap<>();
        
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        
        // Créer les nœuds pour chaque application
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            Map<String, Object> node = new HashMap<>();
            node.put("id", app.getName());
            node.put("type", "application");
            node.put("frameworks", app.getFrameworks());
            nodes.add(node);
            
            // Ajouter les dépendances comme nœuds et arêtes
            if (app.getGlobalDependencies() != null) {
                // EJB
                if (app.getGlobalDependencies().getEjbs() != null) {
                    for (com.legacy.analyzer.model.Dependencies.EJBDependency ejb : 
                         app.getGlobalDependencies().getEjbs()) {
                        
                        Map<String, Object> ejbNode = new HashMap<>();
                        ejbNode.put("id", ejb.getEjbName());
                        ejbNode.put("type", "ejb");
                        nodes.add(ejbNode);
                        
                        Map<String, Object> edge = new HashMap<>();
                        edge.put("source", app.getName());
                        edge.put("target", ejb.getEjbName());
                        edge.put("type", "uses_ejb");
                        edges.add(edge);
                    }
                }
                
                // Bases de données
                if (app.getGlobalDependencies().getDatabases() != null) {
                    for (com.legacy.analyzer.model.Dependencies.DatabaseDependency db : 
                         app.getGlobalDependencies().getDatabases()) {
                        
                        Map<String, Object> dbNode = new HashMap<>();
                        dbNode.put("id", db.getDataSourceName());
                        dbNode.put("type", "database");
                        dbNode.put("dbType", db.getDatabaseType());
                        nodes.add(dbNode);
                        
                        Map<String, Object> edge = new HashMap<>();
                        edge.put("source", app.getName());
                        edge.put("target", db.getDataSourceName());
                        edge.put("type", "uses_database");
                        edges.add(edge);
                    }
                }
            }
        }
        
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        
        Path graphFile = globalDir.resolve("dependencies-graph.json");
        objectMapper.writeValue(graphFile.toFile(), graph);
    }
    
    private void saveGlobalStatistics(Path globalDir, List<AnalysisResult> results) 
            throws IOException {
        Map<String, Object> stats = new HashMap<>();
        
        // Totaux
        int totalEndpoints = results.stream()
                .filter(AnalysisResult::isSuccess)
                .mapToInt(AnalysisResult::getEndpointsCount)
                .sum();
        
        stats.put("totalEndpoints", totalEndpoints);
        
        // Par framework
        Map<String, Integer> endpointsByFramework = new HashMap<>();
        // TODO: Calculer les endpoints par framework
        
        stats.put("endpointsByFramework", endpointsByFramework);
        
        // Complexité moyenne
        // TODO: Calculer les métriques de complexité
        
        Path statsFile = globalDir.resolve("statistics.json");
        objectMapper.writeValue(statsFile.toFile(), stats);
    }
    
    private void saveWebLogicConfig(Path globalDir, List<AnalysisResult> results) 
            throws IOException {
        Map<String, Object> config = new HashMap<>();
        
        // Collecter toutes les datasources
        Set<Map<String, Object>> allDataSources = new HashSet<>();
        
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            if (app.getDataSources() != null) {
                for (WebLogicApplication.DataSource ds : app.getDataSources()) {
                    Map<String, Object> dsMap = new HashMap<>();
                    dsMap.put("name", ds.getName());
                    dsMap.put("jndiName", ds.getJndiName());
                    dsMap.put("driverClass", ds.getDriverClass());
                    dsMap.put("url", ds.getUrl());
                    allDataSources.add(dsMap);
                }
            }
        }
        
        config.put("dataSources", allDataSources);
        
        Path configFile = globalDir.resolve("weblogic-config.json");
        objectMapper.writeValue(configFile.toFile(), config);
    }
    
    public List<AnalysisResult> loadAllResults(Path inputDir) throws IOException {
        List<AnalysisResult> results = new ArrayList<>();
        
        Path applicationsDir = inputDir.resolve("applications");
        if (!Files.exists(applicationsDir)) {
            return results;
        }
        
        try (Stream<Path> appDirs = Files.list(applicationsDir)) {
            appDirs.filter(Files::isDirectory)
                   .forEach(appDir -> {
                       try {
                           AnalysisResult result = loadApplicationResult(appDir);
                           if (result != null) {
                               results.add(result);
                           }
                       } catch (Exception e) {
                           log.error("Erreur lors du chargement des résultats de: {}", appDir, e);
                       }
                   });
        }
        
        return results;
    }
    
    private AnalysisResult loadApplicationResult(Path appDir) throws IOException {
        // Charger les informations de l'application
        Path infoFile = appDir.resolve("application-info.json");
        if (!Files.exists(infoFile)) {
            return null;
        }
        
        // TODO: Implémenter le chargement complet
        // Pour l'instant, créer un résultat basique
        Map<String, Object> info = objectMapper.readValue(infoFile.toFile(), Map.class);
        
        WebLogicApplication app = WebLogicApplication.builder()
                .name((String) info.get("name"))
                .build();
        
        return AnalysisResult.builder()
                .application(app)
                .success(true)
                .build();
    }
    
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
    
    // Classes internes pour l'index
    private static class AnalysisIndex {
        private String analysisId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int totalApplications;
        private int totalEndpoints;
        private List<ApplicationSummary> applicationsAnalyzed;
        private Map<String, Object> globalStatistics;
        
        // Getters et setters
        public String getAnalysisId() { return analysisId; }
        public void setAnalysisId(String analysisId) { this.analysisId = analysisId; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public int getTotalApplications() { return totalApplications; }
        public void setTotalApplications(int totalApplications) { this.totalApplications = totalApplications; }
        
        public int getTotalEndpoints() { return totalEndpoints; }
        public void setTotalEndpoints(int totalEndpoints) { this.totalEndpoints = totalEndpoints; }
        
        public List<ApplicationSummary> getApplicationsAnalyzed() { return applicationsAnalyzed; }
        public void setApplicationsAnalyzed(List<ApplicationSummary> applicationsAnalyzed) { 
            this.applicationsAnalyzed = applicationsAnalyzed; 
        }
        
        public Map<String, Object> getGlobalStatistics() { return globalStatistics; }
        public void setGlobalStatistics(Map<String, Object> globalStatistics) { 
            this.globalStatistics = globalStatistics; 
        }
    }
    
    private static class ApplicationSummary {
        private String name;
        private String path;
        private String status;
        private int endpointsCount;
        private List<String> errors;
        
        // Getters et setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public int getEndpointsCount() { return endpointsCount; }
        public void setEndpointsCount(int endpointsCount) { this.endpointsCount = endpointsCount; }
        
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }
}
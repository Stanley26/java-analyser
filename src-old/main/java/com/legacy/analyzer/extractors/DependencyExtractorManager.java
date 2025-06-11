package com.legacy.analyzer.extractors;

import com.legacy.analyzer.core.config.AnalyzerConfiguration;
import com.legacy.analyzer.extractors.dependencies.*;
import com.legacy.analyzer.model.Dependencies;
import com.legacy.analyzer.model.WebLogicApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DependencyExtractorManager {
    
    private final AnalyzerConfiguration configuration;
    private final DatabaseDependencyExtractor databaseExtractor;
    private final EJBDependencyExtractor ejbExtractor;
    private final CobolDependencyExtractor cobolExtractor;
    private final WebServiceDependencyExtractor webServiceExtractor;
    private final JMSDependencyExtractor jmsExtractor;
    private final FileDependencyExtractor fileExtractor;
    
    public void extractDependencies(WebLogicApplication application) throws IOException {
        log.info("Extraction des dépendances pour l'application: {}", application.getName());
        
        Dependencies globalDependencies = new Dependencies();
        
        // Analyser chaque module
        if (application.getModules() != null) {
            for (WebLogicApplication.Module module : application.getModules()) {
                Dependencies moduleDependencies = extractModuleDependencies(
                        application, module
                );
                module.setDependencies(moduleDependencies);
                
                // Agréger au niveau global
                mergeDependencies(globalDependencies, moduleDependencies);
            }
        } else {
            // Application simple (pas de modules)
            Dependencies dependencies = extractFromPath(
                    application, application.getExtractedPath()
            );
            globalDependencies = dependencies;
        }
        
        application.setGlobalDependencies(globalDependencies);
        logDependencySummary(globalDependencies);
    }
    
    private Dependencies extractModuleDependencies(WebLogicApplication application,
                                                 WebLogicApplication.Module module) 
            throws IOException {
        log.debug("Extraction des dépendances du module: {}", module.getName());
        
        Path modulePath = module.getPath();
        if (modulePath == null) {
            modulePath = application.getExtractedPath().resolve(module.getName());
        }
        
        return extractFromPath(application, modulePath);
    }
    
    private Dependencies extractFromPath(WebLogicApplication application, Path path) 
            throws IOException {
        Dependencies dependencies = new Dependencies();
        
        // Base de données
        if (configuration.getAnalysis().getDatabase().isExtractQueries()) {
            log.debug("Extraction des dépendances base de données...");
            List<Dependencies.DatabaseDependency> dbDeps = 
                    databaseExtractor.extractDependencies(path, application);
            dependencies.setDatabases(dbDeps);
        }
        
        // EJB
        if (configuration.getAnalysis().getIntegrations().getEjb().isAnalyzeRemoteCalls()) {
            log.debug("Extraction des dépendances EJB...");
            List<Dependencies.EJBDependency> ejbDeps = 
                    ejbExtractor.extractDependencies(path, application);
            dependencies.setEjbs(ejbDeps);
        }
        
        // Cobol
        if (configuration.getAnalysis().getIntegrations().getCobol().isDetectSocketCalls() ||
            configuration.getAnalysis().getIntegrations().getCobol().isDetectJni() ||
            configuration.getAnalysis().getIntegrations().getCobol().isDetectFileExchange()) {
            log.debug("Extraction des dépendances Cobol...");
            List<Dependencies.CobolDependency> cobolDeps = 
                    cobolExtractor.extractDependencies(path, application);
            dependencies.setCobolPrograms(cobolDeps);
        }
        
        // Web Services
        if (configuration.getAnalysis().getIntegrations().getWebservices().isAnalyzeSoap() ||
            configuration.getAnalysis().getIntegrations().getWebservices().isAnalyzeRest()) {
            log.debug("Extraction des dépendances Web Services...");
            List<Dependencies.WebServiceDependency> wsDeps = 
                    webServiceExtractor.extractDependencies(path, application);
            dependencies.setWebServices(wsDeps);
        }
        
        // JMS
        log.debug("Extraction des dépendances JMS...");
        List<Dependencies.JMSDependency> jmsDeps = 
                jmsExtractor.extractDependencies(path, application);
        dependencies.setJmsQueues(jmsDeps);
        
        // Fichiers
        log.debug("Extraction des dépendances fichiers...");
        List<Dependencies.FileDependency> fileDeps = 
                fileExtractor.extractDependencies(path, application);
        dependencies.setFiles(fileDeps);
        
        return dependencies;
    }
    
    private void mergeDependencies(Dependencies target, Dependencies source) {
        // Fusionner les bases de données
        if (source.getDatabases() != null) {
            if (target.getDatabases() == null) {
                target.setDatabases(new ArrayList<>());
            }
            target.getDatabases().addAll(source.getDatabases());
        }
        
        // Fusionner les EJB
        if (source.getEjbs() != null) {
            if (target.getEjbs() == null) {
                target.setEjbs(new ArrayList<>());
            }
            target.getEjbs().addAll(source.getEjbs());
        }
        
        // Fusionner les programmes Cobol
        if (source.getCobolPrograms() != null) {
            if (target.getCobolPrograms() == null) {
                target.setCobolPrograms(new ArrayList<>());
            }
            target.getCobolPrograms().addAll(source.getCobolPrograms());
        }
        
        // Fusionner les Web Services
        if (source.getWebServices() != null) {
            if (target.getWebServices() == null) {
                target.setWebServices(new ArrayList<>());
            }
            target.getWebServices().addAll(source.getWebServices());
        }
        
        // Fusionner les queues JMS
        if (source.getJmsQueues() != null) {
            if (target.getJmsQueues() == null) {
                target.setJmsQueues(new ArrayList<>());
            }
            target.getJmsQueues().addAll(source.getJmsQueues());
        }
        
        // Fusionner les fichiers
        if (source.getFiles() != null) {
            if (target.getFiles() == null) {
                target.setFiles(new ArrayList<>());
            }
            target.getFiles().addAll(source.getFiles());
        }
        
        // Fusionner les systèmes externes
        if (source.getExternalSystems() != null) {
            if (target.getExternalSystems() == null) {
                target.setExternalSystems(new ArrayList<>());
            }
            target.getExternalSystems().addAll(source.getExternalSystems());
        }
    }
    
    private void logDependencySummary(Dependencies dependencies) {
        log.info("=== Résumé des dépendances ===");
        
        if (dependencies.getDatabases() != null && !dependencies.getDatabases().isEmpty()) {
            log.info("Bases de données: {}", dependencies.getDatabases().size());
            dependencies.getDatabases().forEach(db -> 
                log.debug("  - {} ({})", db.getDataSourceName(), db.getDatabaseType())
            );
        }
        
        if (dependencies.getEjbs() != null && !dependencies.getEjbs().isEmpty()) {
            log.info("EJB: {}", dependencies.getEjbs().size());
            dependencies.getEjbs().forEach(ejb -> 
                log.debug("  - {} ({})", ejb.getEjbName(), ejb.getJndiName())
            );
        }
        
        if (dependencies.getCobolPrograms() != null && !dependencies.getCobolPrograms().isEmpty()) {
            log.info("Programmes Cobol: {}", dependencies.getCobolPrograms().size());
            dependencies.getCobolPrograms().forEach(cobol -> 
                log.debug("  - {} ({})", cobol.getProgramName(), cobol.getConnectionType())
            );
        }
        
        if (dependencies.getWebServices() != null && !dependencies.getWebServices().isEmpty()) {
            log.info("Web Services: {}", dependencies.getWebServices().size());
            dependencies.getWebServices().forEach(ws -> 
                log.debug("  - {} ({}) - {}", ws.getServiceName(), ws.getType(), ws.getUrl())
            );
        }
        
        if (dependencies.getJmsQueues() != null && !dependencies.getJmsQueues().isEmpty()) {
            log.info("Queues JMS: {}", dependencies.getJmsQueues().size());
            dependencies.getJmsQueues().forEach(jms -> {
                String name = jms.getQueueName() != null ? jms.getQueueName() : jms.getTopicName();
                log.debug("  - {} ({})", name, jms.getConnectionFactory());
            });
        }
        
        if (dependencies.getFiles() != null && !dependencies.getFiles().isEmpty()) {
            log.info("Fichiers: {}", dependencies.getFiles().size());
            dependencies.getFiles().forEach(file -> 
                log.debug("  - {} ({}) - {}", file.getFilePath(), file.getFileType(), 
                         file.getAccessType())
            );
        }
    }
}
package com.legacy.analyzer.extractors;

import com.legacy.analyzer.core.config.AnalyzerConfiguration;
import com.legacy.analyzer.extractors.endpoints.*;
import com.legacy.analyzer.model.Endpoint;
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
public class EndpointExtractorManager {
    
    private final AnalyzerConfiguration configuration;
    private final ServletEndpointExtractor servletExtractor;
    private final StrutsEndpointExtractor strutsExtractor;
    private final SpringEndpointExtractor springExtractor;
    private final JaxRsEndpointExtractor jaxRsExtractor;
    private final JsfEndpointExtractor jsfExtractor;
    
    public void extractEndpoints(WebLogicApplication application) throws IOException {
        log.info("Extraction des endpoints pour l'application: {}", application.getName());
        
        List<Endpoint> allEndpoints = new ArrayList<>();
        
        // Analyser chaque module
        if (application.getModules() != null) {
            for (WebLogicApplication.Module module : application.getModules()) {
                List<Endpoint> moduleEndpoints = extractModuleEndpoints(application, module);
                module.setEndpoints(moduleEndpoints);
                allEndpoints.addAll(moduleEndpoints);
            }
        } else {
            // Application simple (pas de modules)
            List<Endpoint> endpoints = extractFromPath(application, application.getExtractedPath());
            allEndpoints.addAll(endpoints);
        }
        
        application.setEndpoints(allEndpoints);
        log.info("Total des endpoints extraits: {}", allEndpoints.size());
    }
    
    private List<Endpoint> extractModuleEndpoints(WebLogicApplication application, 
                                                  WebLogicApplication.Module module) 
            throws IOException {
        log.debug("Extraction des endpoints du module: {}", module.getName());
        
        Path modulePath = module.getPath();
        if (modulePath == null) {
            modulePath = application.getExtractedPath().resolve(module.getName());
        }
        
        return extractFromPath(application, modulePath, module.getName());
    }
    
    private List<Endpoint> extractFromPath(WebLogicApplication application, Path path) 
            throws IOException {
        return extractFromPath(application, path, null);
    }
    
    private List<Endpoint> extractFromPath(WebLogicApplication application, Path path, 
                                         String moduleName) throws IOException {
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Extraire selon les frameworks détectés
        if (application.getFrameworks() != null) {
            for (String framework : application.getFrameworks()) {
                if (!configuration.isFrameworkEnabled(framework)) {
                    log.debug("Framework {} désactivé dans la configuration", framework);
                    continue;
                }
                
                try {
                    List<Endpoint> frameworkEndpoints = extractByFramework(
                            application, path, moduleName, framework
                    );
                    endpoints.addAll(frameworkEndpoints);
                } catch (Exception e) {
                    log.error("Erreur lors de l'extraction des endpoints {}", framework, e);
                }
            }
        }
        
        // Toujours extraire les servlets de base
        if (configuration.isFrameworkEnabled("servlet")) {
            try {
                List<Endpoint> servletEndpoints = servletExtractor.extractEndpoints(
                        path, application.getName(), moduleName
                );
                endpoints.addAll(servletEndpoints);
            } catch (Exception e) {
                log.error("Erreur lors de l'extraction des endpoints Servlet", e);
            }
        }
        
        return endpoints;
    }
    
    private List<Endpoint> extractByFramework(WebLogicApplication application, Path path, 
                                            String moduleName, String framework) 
            throws IOException {
        
        String appName = application.getName();
        
        switch (framework.toLowerCase()) {
            case "struts":
            case "struts-1.x":
            case "struts-2.x":
                return strutsExtractor.extractEndpoints(path, appName, moduleName);
                
            case "spring":
            case "spring-mvc":
                return springExtractor.extractEndpoints(path, appName, moduleName);
                
            case "jax-rs":
                return jaxRsExtractor.extractEndpoints(path, appName, moduleName);
                
            case "jsf":
                return jsfExtractor.extractEndpoints(path, appName, moduleName);
                
            default:
                log.debug("Pas d'extracteur spécifique pour le framework: {}", framework);
                return new ArrayList<>();
        }
    }
}
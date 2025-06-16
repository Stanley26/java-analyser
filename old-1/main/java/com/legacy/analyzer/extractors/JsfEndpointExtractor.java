package com.legacy.analyzer.extractors.endpoints;

import com.legacy.analyzer.model.Endpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class JsfEndpointExtractor {
    
    public List<Endpoint> extractEndpoints(Path modulePath, String applicationName,
                                         String moduleName) throws IOException {
        log.debug("Extraction des endpoints JSF depuis: {}", modulePath);
        
        // JSF est plus complexe car basé sur des pages XHTML/JSP et faces-config.xml
        // Cette implémentation est un placeholder pour l'instant
        List<Endpoint> endpoints = new ArrayList<>();
        
        // TODO: Implémenter l'extraction JSF
        // 1. Parser faces-config.xml pour les navigation rules
        // 2. Scanner les pages .xhtml/.jsp
        // 3. Analyser les managed beans
        // 4. Extraire les actions et navigation
        
        log.info("Extraction JSF non encore implémentée");
        
        return endpoints;
    }
}
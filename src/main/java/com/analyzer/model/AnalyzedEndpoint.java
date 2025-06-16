// Fichier: src/main/java/com/votre_entreprise/analyzer/model/AnalyzedEndpoint.java
package com.votre_entreprise.analyzer.model;

import java.util.List;

/**
 * Représente le rapport complet pour un seul endpoint analysé.
 * Un 'record' Java est utilisé pour une classe de données immuable et concise.
 */
public record AnalyzedEndpoint(
    String endpointPath,
    String httpMethod,
    String framework,
    String entryPointSignature,
    List<Dependency> dependencies,
    List<BusinessRule> businessRules
) {}
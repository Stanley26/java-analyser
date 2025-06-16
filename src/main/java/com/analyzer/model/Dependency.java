// Fichier: src/main/java/com/votre_entreprise/analyzer/model/Dependency.java
package com.votre_entreprise.analyzer.model;

/**
 * Représente une dépendance (un appel de méthode) dans la chaîne logique.
 */
public record Dependency(
    String type, // Ex: "Service", "Repository", "External API", "Component"
    String className,
    String methodCalled,
    int callDepth
) {}

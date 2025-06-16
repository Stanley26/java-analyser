// Fichier: src/main/java/com/votre_entreprise/analyzer/model/BusinessRule.java
package com.votre_entreprise.analyzer.model;

/**
 * Représente une règle métier ou une condition extraite du code.
 * (Actuellement non implémenté en profondeur mais le modèle est prêt).
 */
public record BusinessRule(
    String type, // Ex: "Validation", "Condition", "Security"
    String description,
    String codeSnippet
) {}

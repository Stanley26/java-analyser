package com.analyzer.model.technical;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente une règle de sécurité unique appliquée à un point d'entrée.
 * Elle capture l'expression de sécurité brute ainsi qu'une interprétation
 * (comme les rôles requis) pour faciliter l'analyse.
 */
public class SecurityRule {

    /**
     * L'expression de sécurité complète telle qu'elle est définie dans l'annotation.
     * Exemples: "hasRole('ADMIN')", "isAuthenticated() and hasScope('read')"
     */
    public String expression;

    /**
     * Une liste des rôles extraits de l'expression.
     * Ceci est une simplification pour un accès rapide aux exigences de rôle.
     * Exemple: ["ROLE_ADMIN", "ROLE_USER"]
     */
    public List<String> requiredRoles = new ArrayList<>();
}

// model/technical/EndpointDetails.java
package com.analyzer.model.technical;

import java.util.ArrayList;
import java.util.List;

/**
 * Contient les détails techniques approfondis d'un point d'entrée (Endpoint).
 * Cette classe est enrichie au fur et à mesure que l'analyse progresse.
 */
public class EndpointDetails {

    /** Le nom complet de la classe qui gère l'endpoint (ex: com.example.MyController). */
    public String controllerClass;

    /** La signature de la méthode qui gère la requête (ex: getUserById(long)). */
    public String handlerMethod;

    /** Le type de retour de la méthode. */
    public String returnType;
    
    /** Le nom du Form Bean associé (spécifique à Struts). */
    public String formBean;

    /** La localisation précise du point d'entrée dans le code source. */
    public SourceLocation sourceLocation;

    /**
     * NOUVEAU: La liste des règles de sécurité qui s'appliquent à cet endpoint.
     * Un endpoint peut avoir plusieurs règles (ex: une pour le rôle, une pour le scope).
     */
    public List<SecurityRule> securityRules = new ArrayList<>();

    /** La séquence des appels de méthodes internes explorés par le graphe d'appels. */
    public List<String> internalCalls = new ArrayList<>();
    
    /** La liste des dépendances externes (BD, EJB, etc.) trouvées le long du graphe d'appels. */
    public List<ExternalCall> externalCalls = new ArrayList<>();
}
```java
// model/technical/SecurityRule.java (Nouveau)
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

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
     * La liste des règles de sécurité qui s'appliquent à cet endpoint.
     * Un endpoint peut avoir plusieurs règles (ex: une pour le rôle, une pour le scope).
     */
    public List<SecurityRule> securityRules = new ArrayList<>();

    /** La séquence des appels de méthodes internes explorés par le graphe d'appels. */
    public List<String> internalCalls = new ArrayList<>();
    
    /** La liste des dépendances externes (BD, EJB, etc.) trouvées le long du graphe d'appels. */
    public List<ExternalCall> externalCalls = new ArrayList<>();
}

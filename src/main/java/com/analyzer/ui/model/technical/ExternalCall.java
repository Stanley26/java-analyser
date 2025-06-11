package com.analyzer.model.technical;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Classe de base abstraite pour représenter un appel à un système externe.
 * Elle sert de parent commun pour tous les types d'appels spécifiques
 * (base de données, EJB, service web, etc.).
 *
 * Les annotations @JsonTypeInfo et @JsonSubTypes sont utilisées par la librairie Jackson
 * pour gérer correctement le polymorphisme lors de la sérialisation et de la
 * désérialisation en JSON. Elles garantissent que chaque objet est écrit
 * avec un champ "type" qui identifie sa classe concrète.
 */
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,           // Utilise un nom logique (ex: "DATABASE_QUERY") comme identifiant de type.
  include = JsonTypeInfo.As.PROPERTY,   // Inclut cet identifiant comme une propriété JSON.
  property = "type"                     // Le nom de la propriété JSON sera "type".
)
@JsonSubTypes({
  // Liste toutes les sous-classes possibles et le nom à utiliser pour chacune.
  @JsonSubTypes.Type(value = DatabaseCall.class, name = "DATABASE_QUERY"),
  @JsonSubTypes.Type(value = EjbCall.class, name = "EJB_LOOKUP")
  // Pour ajouter un nouveau type d'appel (ex: WebServiceCall), ajoutez une nouvelle ligne ici.
  // @JsonSubTypes.Type(value = WebServiceCall.class, name = "WEB_SERVICE_CALL")
})
public abstract class ExternalCall {

    /**
     * Localisation dans le code source où l'appel externe a été trouvé.
     * Ce champ est commun à tous les types d'appels.
     */
    public SourceLocation sourceLocation;
}

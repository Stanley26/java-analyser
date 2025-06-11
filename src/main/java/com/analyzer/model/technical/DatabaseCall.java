package com.analyzer.model.technical;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente un appel spécifique à une base de données.
 * Cette classe hérite de ExternalCall et ajoute des champs
 * pertinents pour une interaction JDBC, comme la requête SQL elle-même.
 */
public class DatabaseCall extends ExternalCall {

    /**
     * La cible de la connexion, qui pourrait être une URL JDBC ou
     * un nom de DataSource JNDI. Laissé null pour l'instant.
     */
    public String target;

    /**
     * La requête SQL exacte qui a été détectée dans le code source.
     */
    public String query;

    /**
     * Une liste des tables qui semblent être affectées par la requête.
     * Note: Cette liste n'est pas encore remplie par les parseurs actuels
     * et représente une future amélioration possible.
     */
    public List<String> tables = new ArrayList<>();
}

package com.analyzer.parsers.common;

import com.analyzer.model.technical.ExternalCall;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.List;

/**
 * Définit le contrat pour tout parseur dont la mission est de découvrir
 * des dépendances externes spécifiques (BDD, EJB, etc.) à l'intérieur
 * d'une méthode. Ces parseurs sont utilisés par le CallGraphResolver
 * lors de l'exploration du code.
 */
public interface DependencyParser {

    /**
     * Analyse une méthode donnée et recherche des appels à des dépendances externes.
     *
     * @param method La déclaration de la méthode en cours d'analyse.
     * @param enclosingClass La déclaration de la classe contenant la méthode, utile
     * pour obtenir des informations contextuelles comme le nom complet.
     * @return Une liste d'objets {@link ExternalCall} trouvés dans cette méthode.
     * Retourne une liste vide si aucune dépendance pertinente n'est trouvée.
     */
    List<ExternalCall> findDependencies(MethodDeclaration method, TypeDeclaration<?> enclosingClass);
}

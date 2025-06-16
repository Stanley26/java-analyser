package com.analyzer.parsers.common;

import com.analyzer.model.technical.Endpoint;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Définit le contrat pour tout parseur dont la mission est de découvrir
 * les points d'entrée d'une application.
 *
 * Un "point d'entrée" est un composant qui peut être appelé de l'extérieur,
 * comme un endpoint REST, une action Struts, ou un Servlet.
 *
 * Chaque classe qui implémente cette interface est spécialisée dans la
 * détection des points d'entrée pour une technologie spécifique.
 */
public interface EntryPointParser {

    /**
     * Détermine si ce parseur est capable et intéressé par l'analyse
     * du fichier fourni.
     *
     * <p>Exemple: Un {@code SpringAnnotationParser} retournerait {@code true} pour un fichier
     * {@code .java}, tandis qu'un {@code StrutsXmlParser} retournerait {@code true}
     * pour un fichier nommé {@code struts-config.xml}.</p>
     *
     * @param file Le fichier à tester.
     * @return {@code true} si le parseur doit analyser ce fichier, {@code false} sinon.
     */
    boolean supports(File file);

    /**
     * Analyse un fichier et en extrait une liste de points d'entrée.
     * Si le fichier ne contient aucun point d'entrée pertinent pour ce parseur,
     * la méthode retourne une liste vide.
     *
     * @param file Le fichier à analyser.
     * @param projectRoot Le chemin racine du projet en cours d'analyse, utile pour
     * calculer les chemins de fichiers relatifs.
     * @return Une liste d'objets {@link Endpoint} trouvés dans ce fichier. Ne retourne jamais {@code null}.
     */
    List<Endpoint> parse(File file, Path projectRoot);
}

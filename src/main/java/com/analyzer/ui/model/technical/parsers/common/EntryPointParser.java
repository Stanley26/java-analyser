// parsers/common/EntryPointParser.java
package com.analyzer.parsers.common;

import com.analyzer.model.technical.AnalysisReport;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface pour les parseurs qui découvrent les points d'entrée d'une application
 * (ex: Endpoints Spring, Actions Struts).
 */
public interface EntryPointParser {
    
    /**
     * Indique si ce parseur est intéressé par l'analyse de ce fichier.
     * @param file Le fichier à tester.
     * @return true si le parseur doit être exécuté, false sinon.
     */
    boolean supports(File file);

    /**
     * Analyse un fichier et retourne une liste de points d'entrée trouvés.
     * @param file Le fichier à analyser.
     * @param projectRoot Le chemin racine du projet.
     * @return Une liste d'objets Endpoint trouvés dans ce fichier.
     */
    List<com.analyzer.model.technical.Endpoint> parse(File file, Path projectRoot);
}
```java
// parsers/common/DependencyParser.java
package com.analyzer.parsers.common;

import com.analyzer.model.technical.ExternalCall;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.List;

/**
 * Interface pour les parseurs qui analysent le contenu d'une méthode
 * pour trouver des dépendances (ex: appels JDBC, EJB).
 */
public interface DependencyParser {

    /**
     * Analyse une déclaration de méthode pour y trouver des dépendances.
     * @param method La méthode à analyser.
     * @param enclosingClass La classe contenant la méthode.
     * @return Une liste d'appels externes trouvés dans cette méthode.
     */
    List<ExternalCall> findDependencies(MethodDeclaration method, TypeDeclaration<?> enclosingClass);
}

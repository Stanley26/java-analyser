package com.analyzer.parsers.common;

import com.analyzer.model.technical.AnalysisReport;
import java.io.File;
import java.nio.file.Path;

/**
 * Interface commune pour tous les parseurs. Chaque parseur est spécialisé
 * dans la détection d'une technologie (Spring, Struts, JDBC, etc.).
 */
public interface FrameworkParser {

    /**
     * Analyse un fichier et enrichit le rapport avec les informations trouvées.
     * @param file Le fichier à analyser.
     * @param projectRoot Le chemin racine du projet, pour résoudre les chemins de fichiers.
     * @param report L'objet rapport à enrichir.
     */
    void parse(File file, Path projectRoot, AnalysisReport report);

    /**
     * Indique si ce parseur est intéressé par l'analyse de ce fichier.
     * @param file Le fichier à tester.
     * @return true si le parseur doit être exécuté, false sinon.
     */
    boolean supports(File file);
}

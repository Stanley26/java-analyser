package com.analyzer.ui;

import java.io.File;
import java.nio.file.Path;

/**
 * Gère l'affichage de la progression de l'analyse dans la console.
 */
public class ConsoleProgressReporter {

    public void startAnalysis(int totalProjects, File rootDir) {
        System.out.println("========================================================================");
        System.out.printf("Analyse démarrée pour %d projet(s) trouvé(s) dans : %s%n", totalProjects, rootDir.getAbsolutePath());
        System.out.println("========================================================================");
    }

    public void startProject(String projectName, int projectNumber) {
        System.out.printf("%n[%d] Démarrage de l'analyse pour le projet : '%s'%n", projectNumber, projectName);
    }

    // NOUVEAU: Pour rapporter une étape spécifique
    public void reportStep(String projectName, String message) {
        System.out.printf("    - %s%n", message);
    }

    // NOUVEAU: Pour rapporter une erreur sans arrêter le processus
    public void reportError(String projectName, String message, Exception e) {
        System.err.printf("    -> [ERREUR] dans '%s': %s (%s)%n", projectName, message, e.getMessage());
    }

    public void completeProject(String projectName, Path reportPath) {
        System.out.printf(" -> [SUCCÈS] Analyse de '%s' terminée. Rapport généré : %s%n", projectName, reportPath.toAbsolutePath());
    }

    public void endAnalysis(int totalProjects) {
        System.out.println("\n========================================================================");
        System.out.printf("Analyse terminée. %d rapport(s) technique(s) généré(s).%n", totalProjects);
        System.out.println("========================================================================");
    }

    public void reportNoProjectsFound(File rootDir) {
        System.err.println("AVERTISSEMENT : Aucun projet (contenant pom.xml ou build.gradle.kts) n'a été trouvé dans " + rootDir.getAbsolutePath());
    }

    public void reportCorrelationStart() {
        System.out.println("\n------------------------------------------------------------------------");
        System.out.println("Phase de Corrélation Métier : Démarrage...");
    }

    public void reportCorrelationEnd() {
        System.out.println(" -> [SUCCÈS] Corrélation terminée. Le rapport métier a été généré.");
        System.out.println("------------------------------------------------------------------------");
    }
}

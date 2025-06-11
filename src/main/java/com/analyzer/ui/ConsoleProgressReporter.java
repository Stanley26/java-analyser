package com.analyzer.ui;

import java.io.File;
import java.nio.file.Path;

/**
 * Gère l'affichage de la progression de l'analyse dans la console.
 * Son rôle est de fournir un retour visuel clair et structuré à l'utilisateur
 * tout au long du processus, qui peut être long.
 */
public class ConsoleProgressReporter {

    private static final String SEPARATOR = "========================================================================";
    private static final String SUB_SEPARATOR = "------------------------------------------------------------------------";

    /**
     * Affiche le message de bienvenue au tout début de l'analyse.
     * @param totalProjects Le nombre total de projets qui seront analysés.
     * @param rootDir Le répertoire racine de l'analyse.
     */
    public void startAnalysis(int totalProjects, File rootDir) {
        System.out.println(SEPARATOR);
        System.out.printf("Analyse démarrée pour %d projet(s) trouvé(s) dans : %s%n", totalProjects, rootDir.getAbsolutePath());
        System.out.println(SEPARATOR);
    }

    /**
     * Indique le début de l'analyse pour un projet spécifique.
     * @param projectName Le nom du projet en cours d'analyse.
     * @param projectNumber Le numéro du projet actuel (ex: 3 sur 5).
     */
    public void startProject(String projectName, int projectNumber) {
        System.out.printf("%n[%d] Démarrage de l'analyse pour le projet : '%s'%n", projectNumber, projectName);
        System.out.println(SUB_SEPARATOR);
    }

    /**
     * Rapporte une étape intermédiaire spécifique au sein de l'analyse d'un projet.
     * @param message Le message décrivant l'étape (ex: "Analyse de la configuration...").
     */
    public void reportStep(String projectName, String message) {
        System.out.printf("    - %s%n", message);
    }

    /**
     * Affiche un message d'erreur non bloquant.
     * @param projectName Le nom du projet où l'erreur est survenue.
     * @param message Le message décrivant l'erreur.
     * @param e L'exception capturée.
     */
    public void reportError(String projectName, String message, Exception e) {
        System.err.printf("    -> [ERREUR] dans '%s': %s (%s)%n", projectName, message, e.getMessage());
    }

    /**
     * Confirme la fin de l'analyse pour un projet et indique où le rapport a été généré.
     * @param projectName Le nom du projet terminé.
     * @param reportPath Le chemin complet du rapport généré.
     */
    public void completeProject(String projectName, Path reportPath) {
        System.out.println(SUB_SEPARATOR);
        System.out.printf(" -> [SUCCÈS] Analyse de '%s' terminée. Rapport généré : %s%n", projectName, reportPath.toAbsolutePath());
    }

    /**
     * Affiche le message de début pour la phase de corrélation métier.
     */
    public void reportCorrelationStart() {
        System.out.println("\n" + SEPARATOR);
        System.out.println("Phase de Corrélation Métier : Démarrage...");
    }

    /**
     * Confirme la fin de la phase de corrélation et indique où le rapport métier a été généré.
     * @param reportPath Le chemin complet du rapport métier consolidé.
     */
    public void reportCorrelationEnd(Path reportPath) {
        System.out.println(" -> [SUCCÈS] Corrélation terminée. Rapport métier généré : " + reportPath.toAbsolutePath());
        System.out.println(SEPARATOR);
    }

    /**
     * Affiche le message final lorsque tout le processus est terminé.
     * @param totalProjects Le nombre total de projets qui ont été analysés.
     */
    public void endAnalysis(int totalProjects) {
        System.out.println("\n" + SEPARATOR);
        System.out.printf("Analyse terminée. %d rapport(s) technique(s) généré(s).%n", totalProjects);
        System.out.println(SEPARATOR);
    }

    /**
     * Affiche un avertissement si aucun projet n'a été trouvé dans le répertoire spécifié.
     * @param rootDir Le répertoire qui a été scanné.
     */
    public void reportNoProjectsFound(File rootDir) {
        System.err.println("AVERTISSEMENT : Aucun projet (contenant pom.xml ou build.gradle.kts) n'a été trouvé dans " + rootDir.getAbsolutePath());
    }
}

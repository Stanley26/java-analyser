// =================================================================================
// Fichier: src/main/java/com/votre_entreprise/analyzer/Main.java
// =================================================================================
package com.votre_entreprise.analyzer;

import com.votre_entreprise.analyzer.discovery.ProjectDiscoverer;
import com.votre_entreprise.analyzer.model.AnalyzedEndpoint;
import com.votre_entreprise.analyzer.serialization.JsonSerializer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0 || args[0] == null || args[0].isBlank()) {
            System.err.println("ERREUR: Vous devez fournir le chemin vers le répertoire racine des projets.");
            System.out.println("Usage: java -jar analyzer.jar C:/chemin/vers/mes/projets");
            return;
        }

        String rootDirectoryPath = args[0];
        System.out.println("Lancement de l'analyse du portfolio dans : " + rootDirectoryPath);
        System.out.println("------------------------------------------------------------------");

        try {
            List<Path> projectsToAnalyze = ProjectDiscoverer.findMavenProjects(rootDirectoryPath);

            if (projectsToAnalyze.isEmpty()) {
                System.out.println("Aucun projet Maven valide trouvé dans le répertoire spécifié.");
                return;
            }

            System.out.println(projectsToAnalyze.size() + " projets à analyser : ");
            projectsToAnalyze.forEach(p -> System.out.println(" - " + p.getFileName()));
            System.out.println("------------------------------------------------------------------");

            int successCount = 0;
            for (Path projectPath : projectsToAnalyze) {
                String projectName = projectPath.getFileName().toString();
                System.out.println(">> Démarrage de l'analyse pour le projet : " + projectName);

                try {
                    SingleProjectAnalyzer analyzer = new SingleProjectAnalyzer(projectPath.toString());
                    List<AnalyzedEndpoint> results = analyzer.analyze();
                    saveProjectReport(projectName, results);
                    System.out.println("<< Succès : Rapport généré pour " + projectName);
                    successCount++;

                } catch (Exception e) {
                    System.err.println("!! ERREUR lors de l'analyse du projet " + projectName + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    System.out.println("------------------------------------------------------------------");
                }
            }

            System.out.println("Analyse du portfolio terminée.");
            System.out.println("Rapports générés avec succès : " + successCount + "/" + projectsToAnalyze.size());

        } catch (IOException e) {
            System.err.println("ERREUR: Impossible de lire le répertoire racine des projets : " + e.getMessage());
        }
    }

    private static void saveProjectReport(String projectName, List<AnalyzedEndpoint> results) {
        if (results == null || results.isEmpty()) {
            System.out.println("   -> Aucun endpoint trouvé ou analysé pour " + projectName + ". Aucun rapport généré.");
            return;
        }
        String outputFilename = "rapport_analyse_" + projectName + ".json";
        try {
            JsonSerializer.save(results, outputFilename);
            System.out.println("   -> Rapport sauvegardé : " + outputFilename);
        } catch (Exception e) {
            System.err.println("   -> !! ERREUR lors de la sauvegarde du rapport pour " + projectName + ": " + e.getMessage());
        }
    }
}
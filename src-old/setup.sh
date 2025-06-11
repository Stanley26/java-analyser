// Main.java - Point d'entrée de l'application
package com.analyzer;

import com.analyzer.engine.AnalysisEngine;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.File;
import java.util.concurrent.Callable;

@Command(name = "legacy-analyzer", mixinStandardHelpOptions = true, version = "1.0",
         description = "Analyse statique d'applications Java legacy pour guider une réécriture.")
public class Main implements Callable<Integer> {

    @Option(names = {"-p", "--projects-path"}, required = true, description = "Chemin vers le dossier racine contenant les projets à analyser.")
    private File projectsPath;

    @Option(names = {"-o", "--override-path"}, description = "Chemin vers le projet contenant les properties d'override (ex: production).")
    private File overridePath;

    @Option(names = {"-b", "--business-map"}, description = "Chemin vers le fichier CSV mappant les URLs aux fonctions d'affaires.")
    private File businessMapFile;

    @Option(names = {"-out", "--output-directory"}, description = "Dossier de sortie pour les rapports.", defaultValue = "reports")
    private File outputDirectory;

    @Override
    public Integer call() throws Exception {
        // Crée l'instance du moteur principal avec la configuration de la ligne de commande
        AnalysisEngine engine = new AnalysisEngine(projectsPath, overridePath, businessMapFile, outputDirectory);
        engine.run();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
```java
// engine/AnalysisEngine.java - Le chef d'orchestre
package com.analyzer.engine;

import com.analyzer.model.technical.AnalysisReport;
import com.analyzer.reporter.JsonReportGenerator;
import com.analyzer.ui.ConsoleProgressReporter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Orchestre l'analyse complète d'un ou plusieurs projets.
 */
public class AnalysisEngine {

    private final File projectsPath;
    private final File overridePath;
    private final File businessMapFile;
    private final File outputDirectory;

    private final ProjectScanner projectScanner;
    private final ConsoleProgressReporter progressReporter;
    private final JsonReportGenerator reportGenerator;

    public AnalysisEngine(File projectsPath, File overridePath, File businessMapFile, File outputDirectory) {
        this.projectsPath = projectsPath;
        this.overridePath = overridePath;
        this.businessMapFile = businessMapFile;
        this.outputDirectory = outputDirectory;
        this.projectScanner = new ProjectScanner();
        this.progressReporter = new ConsoleProgressReporter();
        this.reportGenerator = new JsonReportGenerator();
    }

    public void run() throws IOException {
        // 1. Découvrir les projets
        List<File> projectsToAnalyze = projectScanner.findProjects(projectsPath);
        if (projectsToAnalyze.isEmpty()) {
            progressReporter.reportNoProjectsFound(projectsPath);
            return;
        }

        // 2. Préparer le dossier de sortie
        if (!outputDirectory.exists()) {
            Files.createDirectories(outputDirectory.toPath());
        }

        // 3. Boucler sur chaque projet et l'analyser
        progressReporter.startAnalysis(projectsToAnalyze.size(), projectsPath);
        for (int i = 0; i < projectsToAnalyze.size(); i++) {
            File projectDir = projectsToAnalyze.get(i);
            String projectName = projectDir.getName();
            progressReporter.startProject(projectName, i + 1);

            // Création d'un rapport vide pour cette étape
            AnalysisReport report = new AnalysisReport(projectName);

            // TODO: Ici se trouvera la logique d'analyse de fichiers et d'appel aux parseurs.
            // Pour l'instant, nous simulons la fin de l'analyse.

            // Génération du rapport technique
            String reportFileName = "rapport-technique-" + projectName + ".json";
            Path reportPath = outputDirectory.toPath().resolve(reportFileName);
            reportGenerator.writeReport(report, reportPath.toFile());

            progressReporter.completeProject(projectName, reportPath);
        }

        // TODO: Ajouter la phase de corrélation métier ici

        progressReporter.endAnalysis(projectsToAnalyze.size());
    }
}
```java
// engine/ProjectScanner.java - Le découvreur de projets
package com.analyzer.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Identifie les projets Java valides à analyser dans un répertoire racine.
 */
public class ProjectScanner {

    /**
     * Trouve tous les sous-dossiers contenant un fichier `build.gradle.kts` ou `pom.xml`.
     * @param rootDirectory Le dossier racine où chercher les projets.
     * @return Une liste de répertoires de projets.
     */
    public List<File> findProjects(File rootDirectory) {
        List<File> projects = new ArrayList<>();
        File[] subDirectories = rootDirectory.listFiles(File::isDirectory);

        if (subDirectories == null) {
            return projects;
        }

        for (File dir : subDirectories) {
            File gradleBuildFile = new File(dir, "build.gradle.kts");
            File mavenBuildFile = new File(dir, "pom.xml");
            if (gradleBuildFile.exists() || mavenBuildFile.exists()) {
                projects.add(dir);
            }
        }
        return projects;
    }
}
```java
// ui/ConsoleProgressReporter.java - La voix de l'application
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
}
```java
// model/technical/AnalysisReport.java - Le conteneur de données
package com.analyzer.model.technical;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AnalysisReport {
    public String applicationName;
    public String analysisTimestamp;
    public String sourcePath; // Sera rempli plus tard
    public List<Object> endpoints = new ArrayList<>(); // Remplacer Object par Endpoint.java
    // etc.

    public AnalysisReport(String applicationName) {
        this.applicationName = applicationName;
        this.analysisTimestamp = Instant.now().toString();
    }

    // Constructeur par défaut nécessaire pour la désérialisation JSON
    public AnalysisReport() {}
}
```

Nous avons maintenant une base solide et fonctionnelle. La prochaine étape sera de commencer l'analyse réelle des fichiers en implémentant notre premier parseur. Que diriez-vous de commencer par le plus simple et le plus important : le `PropertiesParser` pour gérer la configuratio
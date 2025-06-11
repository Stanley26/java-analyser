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

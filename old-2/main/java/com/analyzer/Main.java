package com.analyzer;

import com.analyzer.engine.AnalysisEngine;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Classe principale et point d'entrée de l'application Legacy-Analyzer.
 * Utilise la librairie Picocli pour créer une interface en ligne de commande (CLI)
 * robuste et facile à utiliser, avec gestion automatique de l'aide et des options.
 */
@Command(name = "legacy-analyzer",
         mixinStandardHelpOptions = true,
         version = "Legacy Analyzer 1.0",
         description = "Analyse statiquement des applications Java legacy pour guider et sécuriser les projets de réécriture.")
public class Main implements Callable<Integer> {

    @Option(names = {"-p", "--projects-path"},
            required = true,
            description = "Chemin vers le dossier racine contenant les projets à analyser.")
    private File projectsPath;

    @Option(names = {"-o", "--override-path"},
            description = "Chemin (optionnel) vers le projet contenant les fichiers .properties d'override (ex: la configuration de production).")
    private File overridePath;

    @Option(names = {"-b", "--business-map"},
            description = "Chemin (optionnel) vers le fichier CSV qui mappe les URLs aux fonctions d'affaires.")
    private File businessMapFile;
    
    @Option(names = {"-sp", "--spring-profile"},
            description = "Spécifie (optionnellement) le profil Spring à activer (ex: prod) pour l'analyse des configurations.")
    private String springProfile;

    @Option(names = {"-out", "--output-directory"},
            description = "Dossier de sortie pour les rapports JSON. Par défaut, un dossier 'reports' est créé.",
            defaultValue = "reports")
    private File outputDirectory;

    /**
     * Cette méthode est appelée par Picocli après avoir parsé les arguments de la ligne de commande.
     * C'est ici que la logique principale de l'application est lancée.
     *
     * @return 0 en cas de succès, un autre code en cas d'erreur.
     * @throws Exception Si une erreur survient durant l'analyse.
     */
    @Override
    public Integer call() throws Exception {
        System.out.println("Initialisation de l'analyseur...");
        
        // Crée une instance du moteur d'analyse en lui passant toute la configuration
        // reçue de la ligne de commande.
        AnalysisEngine engine = new AnalysisEngine(projectsPath, overridePath, businessMapFile, springProfile, outputDirectory);
        
        // Lance le processus d'analyse.
        engine.run();
        
        return 0;
    }

    /**
     * La méthode main standard de Java. Elle configure Picocli et exécute l'application.
     *
     * @param args Les arguments fournis par l'utilisateur en ligne de commande.
     */
    public static void main(String[] args) {
        // Crée une nouvelle instance de la commande et l'exécute avec les arguments fournis.
        // Picocli s'occupe de créer une instance de Main, d'injecter les valeurs des options,
        // et d'appeler la méthode call().
        int exitCode = new CommandLine(new Main()).execute(args);
        
        // Termine l'application avec le code de sortie retourné par la logique métier.
        System.exit(exitCode);
    }
}

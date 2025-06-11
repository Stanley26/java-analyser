package com.legacy.analyzer.cli;

import com.legacy.analyzer.core.AnalysisOrchestrator;
import com.legacy.analyzer.core.config.AnalyzerConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Slf4j
@Component
@RequiredArgsConstructor
@Command(
    name = "legacy-analyzer",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Analyse automatique d'applications Java legacy",
    subcommands = {
        AnalyzerCommand.AnalyzeCommand.class,
        AnalyzerCommand.ReportCommand.class,
        AnalyzerCommand.ValidateCommand.class,
        AnalyzerCommand.ServeCommand.class
    }
)
public class AnalyzerCommand implements Callable<Integer> {

    private final AnalysisOrchestrator orchestrator;
    private final AnalyzerConfiguration configuration;

    @Override
    public Integer call() {
        System.out.println("Legacy Analyzer v1.0.0");
        System.out.println("Utilisez --help pour voir les commandes disponibles");
        return 0;
    }

    @Component
    @Command(name = "analyze", description = "Lance l'analyse des applications")
    @RequiredArgsConstructor
    public static class AnalyzeCommand implements Callable<Integer> {
        
        private final AnalysisOrchestrator orchestrator;
        private final AnalyzerConfiguration configuration;

        @Option(names = {"-s", "--source"}, description = "Répertoire source", required = true)
        private String sourceDir;

        @Option(names = {"-o", "--output"}, description = "Répertoire de sortie")
        private String outputDir;

        @Option(names = {"-c", "--config"}, description = "Fichier de configuration")
        private Path configFile;

        @Option(names = {"--app-name"}, description = "Nom de l'application spécifique à analyser")
        private String appName;

        @Option(names = {"--parallel"}, description = "Activer l'analyse parallèle", defaultValue = "true")
        private boolean parallel;

        @Option(names = {"--deep"}, description = "Analyse approfondie", defaultValue = "false")
        private boolean deepAnalysis;

        @Option(names = {"--business-functions-file"}, description = "Fichier CSV associant les fonctions d'affaire aux URLs. Format : 'Fonction;URL'")
        private Path businessFunctionsFile;

        @Override
        public Integer call() {
            try {
                log.info("Démarrage de l'analyse...");
                log.info("Répertoire source: {}", sourceDir);
                log.info("Répertoire de sortie: {}", outputDir != null ? outputDir : "Par défaut");
                
                // Configuration de l'analyse
                if (configFile != null) {
                    configuration.loadFromFile(configFile);
                }

                //  On passe le fichier à la configuration
                if (businessFunctionsFile != null) {
                    configuration.setBusinessFunctionsFile(businessFunctionsFile);
                }
                
                configuration.setSourceDirectory(Paths.get(sourceDir));
                if (outputDir != null) {
                    configuration.setOutputDirectory(Paths.get(outputDir));
                }
                
                configuration.setParallelAnalysis(parallel);
                configuration.setDeepAnalysis(deepAnalysis);
                
                if (appName != null) {
                    configuration.setTargetApplication(appName);
                }
                
                // Lancement de l'analyse
                orchestrator.performAnalysis();
                
                log.info("Analyse terminée avec succès!");
                return 0;
                
            } catch (Exception e) {
                log.error("Erreur lors de l'analyse", e);
                return 1;
            }
        }
    }

    @Component
    @Command(name = "serve", description = "Démarre le serveur web pour visualiser les résultats.")
    @RequiredArgsConstructor
    public static class ServeCommand implements Callable<Integer> {
        
        private final AnalyzerConfiguration configuration;

        @Override
        public Integer call() throws Exception {
            log.info("=======================================================================");
            log.info(" Démarrage du serveur web de visualisation...");
            log.info(" ");
            log.info("  -> Accédez au tableau de bord : http://localhost:{}", configuration.getServerPort());
            log.info("  -> Répertoire des résultats   : {}", configuration.getOutputDirectory().toAbsolutePath());
            log.info(" ");
            log.info(" Utilisez Ctrl+C pour arrêter le serveur.");
            log.info("=======================================================================");

            // On laisse le thread principal se terminer, le serveur Tomcat tournera en arrière-plan
            // et maintiendra l'application en vie.
            return 0;
        }
    }

    @Component
    @Command(name = "report", description = "Génère des rapports à partir d'une analyse existante")
    @RequiredArgsConstructor
    public static class ReportCommand implements Callable<Integer> {
        
        private final AnalysisOrchestrator orchestrator;

        @Option(names = {"-i", "--input"}, description = "Répertoire des résultats d'analyse", required = true)
        private String inputDir;

        @Option(names = {"-f", "--format"}, description = "Format de sortie (json, excel, all)", defaultValue = "all")
        private String format;

        @Override
        public Integer call() {
            try {
                log.info("Génération des rapports...");
                log.info("Répertoire d'entrée: {}", inputDir);
                log.info("Format: {}", format);
                
                orchestrator.generateReports(Paths.get(inputDir), format);
                
                log.info("Rapports générés avec succès!");
                return 0;
                
            } catch (Exception e) {
                log.error("Erreur lors de la génération des rapports", e);
                return 1;
            }
        }
    }

    @Component
    @Command(name = "validate", description = "Valide la configuration")
    @RequiredArgsConstructor
    public static class ValidateCommand implements Callable<Integer> {
        
        private final AnalyzerConfiguration configuration;

        @Option(names = {"-c", "--config"}, description = "Fichier de configuration à valider", required = true)
        private Path configFile;

        @Override
        public Integer call() {
            try {
                log.info("Validation de la configuration...");
                
                configuration.loadFromFile(configFile);
                configuration.validate();
                
                log.info("Configuration valide!");
                return 0;
                
            } catch (Exception e) {
                log.error("Configuration invalide", e);
                return 1;
            }
        }
    }
}
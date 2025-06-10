package com.legacy.analyzer.cli;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.ParseResult;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzerCommandLineRunner implements CommandLineRunner, ExitCodeGenerator {

    private final AnalyzerCommand analyzerCommand;
    private final IFactory factory;
    private int exitCode;

    @Override
    public void run(String... args) throws Exception {
        log.debug("Démarrage de l'application avec les arguments: {}", String.join(" ", args));
        
        // Créer l'instance CommandLine avec la factory Spring pour l'injection de dépendances
        CommandLine commandLine = new CommandLine(analyzerCommand, factory);
        
        // Configuration de CommandLine
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setUnmatchedArgumentsAllowed(false);
        commandLine.setUsageHelpAutoWidth(true);
        
        // Exécuter la commande et récupérer le code de sortie
        exitCode = commandLine.execute(args);

        ParseResult parseResult = commandLine.getParseResult();

        // On vérifie si une sous-commande a été appelée et si son nom est "serve".
        boolean isServeCommand = parseResult.subcommand() != null &&
                                 "serve".equals(parseResult.subcommand().commandSpec().name());
        
        if (isServeCommand) {
            // Si la commande était 'serve', on empêche le thread principal de se terminer.
            // C'est ce qui permet au serveur web (qui tourne sur d'autres threads)
            // de rester actif et de répondre aux requêtes HTTP.
            // L'application s'arrêtera alors uniquement avec un Ctrl+C.
            log.info("Le mode 'serve' est actif. Le thread principal est en attente.");
            Thread.currentThread().join();
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
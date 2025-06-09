package com.legacy.analyzer.cli;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

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
        
        // Créer l'instance CommandLine avec Spring factory pour l'injection de dépendances
        CommandLine commandLine = new CommandLine(analyzerCommand, factory);
        
        // Configuration de CommandLine
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setUnmatchedArgumentsAllowed(false);
        commandLine.setUsageHelpAutoWidth(true);
        
        // Exécuter la commande
        exitCode = commandLine.execute(args);
    }
    
    @Override
    public int getExitCode() {
        return exitCode;
    }
}
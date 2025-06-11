package com.analyzer.parsers.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Analyse les fichiers .properties, en gérant la fusion avec des fichiers d'override.
 */
public class PropertiesParser {

    /**
     * Orchestre la lecture des properties locales et leur fusion avec les overrides.
     * @param projectDir Le répertoire du projet à analyser.
     * @param overrideDir Le répertoire (optionnel) contenant les properties d'override.
     * @return Une Map contenant la configuration finale.
     */
    public Map<String, String> parseAndMergeProperties(File projectDir, File overrideDir) throws IOException {
        Properties mergedProperties = new Properties();

        // 1. Charger les propriétés de base de l'application
        loadPropertiesFromDirectory(projectDir, mergedProperties);

        // 2. Charger les propriétés d'override (elles écraseront les propriétés de base)
        if (overrideDir != null && overrideDir.exists()) {
            loadPropertiesFromDirectory(overrideDir, mergedProperties);
        }

        // 3. Convertir l'objet Properties en une Map<String, String> pour le rapport
        return mergedProperties.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> (String) e.getKey(),
                        e -> (String) e.getValue()
                ));
    }

    /**
     * Charge tous les fichiers .properties d'un répertoire et de ses sous-dossiers
     * dans un objet Properties existant.
     * @param directory Le dossier à scanner.
     * @param properties L'objet Properties à remplir.
     */
    private void loadPropertiesFromDirectory(File directory, Properties properties) throws IOException {
        Path startPath = directory.toPath();
        if (!Files.exists(startPath)) return;

        try (Stream<Path> stream = Files.walk(startPath)) {
            stream.filter(path -> path.toString().endsWith(".properties"))
                  .forEach(path -> {
                      try (InputStream input = new FileInputStream(path.toFile())) {
                          properties.load(input);
                      } catch (IOException e) {
                          System.err.println("Erreur lors de la lecture du fichier de propriétés : " + path);
                          e.printStackTrace();
                      }
                  });
        }
    }
}

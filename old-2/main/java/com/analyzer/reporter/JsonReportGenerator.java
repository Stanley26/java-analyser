package com.analyzer.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;

/**
 * Responsable de la sérialisation des objets de rapport (comme AnalysisReport)
 * en un fichier au format JSON.
 * Utilise la librairie Jackson pour une conversion robuste et configurable.
 */
public class JsonReportGenerator {

    private final ObjectMapper objectMapper;

    /**
     * Construit une nouvelle instance du générateur de rapport.
     * Initialise et configure l'ObjectMapper, qui est le moteur de conversion JSON.
     */
    public JsonReportGenerator() {
        this.objectMapper = new ObjectMapper();
        
        // Active la fonctionnalité "pretty print".
        // Le JSON généré sera indenté et formaté pour être facilement lisible par un humain.
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Prend un objet de données (le rapport), le convertit en une chaîne de caractères JSON,
     * et l'écrit dans le fichier de sortie spécifié.
     *
     * @param reportData L'objet contenant les données à sérialiser (ex: une instance de AnalysisReport).
     * @param outputFile Le fichier dans lequel le rapport JSON sera écrit.
     * @throws IOException Si une erreur d'entrée/sortie se produit lors de l'écriture du fichier.
     */
    public void writeReport(Object reportData, File outputFile) throws IOException {
        // La méthode writeValue gère toute la complexité de la conversion de l'objet Java en JSON.
        objectMapper.writeValue(outputFile, reportData);
    }
}

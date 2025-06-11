package com.analyzer.parsers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Lit un fichier CSV fourni par l'utilisateur qui mappe
 * les URL et méthodes HTTP à des fonctions d'affaires.
 */
public class BusinessMapParser {

    /**
     * Lit le fichier de mapping.
     * Format attendu du CSV : "Fonction d'Affaires";"Méthode HTTP";"URL Template"
     * @param mappingFile Le fichier CSV.
     * @return Une map où la clé est "METHODE:/url/template" et la valeur est le nom de la fonction.
     * @throws IOException Si le fichier ne peut pas être lu.
     */
    public Map<String, String> parse(File mappingFile) throws IOException {
        Map<String, String> businessMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(mappingFile))) {
            String line;
            // Ignorer l'en-tête
            br.readLine(); 
            while ((line = br.readLine()) != null) {
                String[] values = line.split(";");
                if (values.length == 3) {
                    String functionName = values[0].replace("\"", "").trim();
                    String httpMethod = values[1].replace("\"", "").trim().toUpperCase();
                    String urlTemplate = values[2].replace("\"", "").trim();
                    String key = httpMethod + ":" + urlTemplate;
                    businessMap.put(key, functionName);
                }
            }
        }
        return businessMap;
    }
}

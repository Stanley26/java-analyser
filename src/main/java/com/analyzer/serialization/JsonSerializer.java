// Fichier: src/main/java/com/votre_entreprise/analyzer/serialization/JsonSerializer.java
package com.votre_entreprise.analyzer.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;

public class JsonSerializer {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // Configure l'ObjectMapper pour une sortie JSON "jolie" (indentée)
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static <T> void save(T data, String filePath) throws IOException {
        objectMapper.writeValue(new File(filePath), data);
    }
}

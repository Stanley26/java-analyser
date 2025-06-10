package com.legacy.analyzer.mapping;

import com.legacy.analyzer.model.Endpoint;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class BusinessFunctionMapper {

    private static class BusinessFunctionMapping {
        final String functionName;
        final String urlPath;

        BusinessFunctionMapping(String functionName, String url) {
            this.functionName = functionName;
            this.urlPath = normalizeUrlPath(url);
        }

        private static String normalizeUrlPath(String fullUrl) {
            try {
                String path = fullUrl;
                if (path.contains("://")) {
                    path = path.substring(path.indexOf("://") + 3);
                    if (path.contains("/")) {
                        path = path.substring(path.indexOf("/"));
                    } else {
                        path = "/";
                    }
                }
                return path.split("\\?")[0];
            } catch (Exception e) {
                log.warn("Impossible de normaliser l'URL : {}", fullUrl);
                return fullUrl;
            }
        }
    }

    private final List<BusinessFunctionMapping> mappings = new ArrayList<>();

    public BusinessFunctionMapper(Path mappingFile) {
        if (mappingFile == null || !Files.exists(mappingFile)) {
            log.warn("Le fichier de fonctions d'affaire n'a pas été fourni ou n'existe pas.");
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(mappingFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(";", 2);
                if (parts.length == 2 && !parts[0].trim().isEmpty() && !parts[1].trim().isEmpty()) {
                    mappings.add(new BusinessFunctionMapping(parts[0].trim(), parts[1].trim()));
                }
            }
            log.info("{} fonctions d'affaire chargées depuis le fichier.", mappings.size());
        } catch (Exception e) {
            log.error("Impossible de lire ou parser le fichier de fonctions d'affaire : {}", mappingFile, e);
        }
    }

    public void enrichEndpoints(List<Endpoint> endpoints) {
        if (mappings.isEmpty() || endpoints == null) {
            return;
        }

        for (Endpoint endpoint : endpoints) {
            Pattern endpointPattern = convertEndpointUrlToRegex(endpoint.getUrl());
            if (endpointPattern == null) continue;

            for (BusinessFunctionMapping mapping : mappings) {
                if (endpointPattern.matcher(mapping.urlPath).matches()) {
                    endpoint.setBusinessFunction(mapping.functionName);
                    log.debug("Endpoint '{}' associé à la fonction '{}'", endpoint.getUrl(), mapping.functionName);
                    break;
                }
            }
        }
    }

    private Pattern convertEndpointUrlToRegex(String endpointUrl) {
        if (endpointUrl == null) {
            return null;
        }
        try {
            String regex = endpointUrl
                    .replaceAll("\\{[^/]+\\}", "([^/]+)")
                    .replaceAll("\\*", ".*");
            return Pattern.compile("^" + regex + "$");
        } catch (Exception e) {
            log.error("Impossible de convertir l'URL de l'endpoint en regex : {}", endpointUrl, e);
            return null;
        }
    }
}
package com.legacy.analyzer.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.legacy.analyzer.core.config.AnalyzerConfiguration;
import com.legacy.analyzer.model.AnalysisResult;
import com.legacy.analyzer.model.WebLogicApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final AnalyzerConfiguration configuration;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @GetMapping("/")
    public String dashboard(Model model) {
        Path outputDir = configuration.getOutputDirectory();
        model.addAttribute("outputDir", outputDir.toAbsolutePath());

        try {
            List<Map<String, Object>> applications = loadApplicationSummaries(outputDir);
            model.addAttribute("applications", applications);
        } catch (IOException e) {
            log.error("Impossible de lire les résultats d'analyse", e);
            model.addAttribute("error", "Impossible de lire les résultats d'analyse depuis : " + outputDir.toAbsolutePath());
            model.addAttribute("applications", new ArrayList<>());
        }
        return "dashboard"; // Nom du template Thymeleaf
    }

    @GetMapping("/application/{appName}")
    public String applicationDetails(@PathVariable String appName, Model model) {
        Path appDir = configuration.getOutputDirectory().resolve("applications").resolve(appName);
        model.addAttribute("appName", appName);

        try {
            Path infoFile = appDir.resolve("application-info.json");
            Map<String, Object> appInfo = objectMapper.readValue(infoFile.toFile(), new TypeReference<>() {});
            model.addAttribute("appInfo", appInfo);

            Path endpointsFile = appDir.resolve("endpoints.json");
            if(Files.exists(endpointsFile)) {
                 List<Map<String, Object>> endpoints = objectMapper.readValue(endpointsFile.toFile(), new TypeReference<>() {});
                 model.addAttribute("endpoints", endpoints);
            }

            Path depsFile = appDir.resolve("dependencies.json");
             if(Files.exists(depsFile)) {
                Map<String, Object> dependencies = objectMapper.readValue(depsFile.toFile(), new TypeReference<>() {});
                model.addAttribute("dependencies", dependencies);
             }

        } catch (IOException e) {
            log.error("Impossible de lire les détails de l'application {}", appName, e);
            model.addAttribute("error", "Impossible de charger les détails pour l'application : " + appName);
        }

        return "application"; // Nom du template Thymeleaf
    }

    private List<Map<String, Object>> loadApplicationSummaries(Path outputDir) throws IOException {
        Path applicationsRoot = outputDir.resolve("applications");
        if (!Files.exists(applicationsRoot)) {
            return new ArrayList<>();
        }
        try (Stream<Path> appDirs = Files.list(applicationsRoot)) {
            return appDirs.filter(Files::isDirectory)
                    .map(this::readSummary)
                    .collect(Collectors.toList());
        }
    }

    private Map<String, Object> readSummary(Path appDir) {
        try {
            Path infoFile = appDir.resolve("application-info.json");
            if (Files.exists(infoFile)) {
                return objectMapper.readValue(infoFile.toFile(), new TypeReference<>() {});
            }
        } catch (IOException e) {
            log.error("Erreur de lecture du résumé pour {}", appDir.getFileName(), e);
        }
        return Map.of("name", appDir.getFileName().toString(), "error", "Fichier info manquant");
    }
}
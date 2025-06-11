package com.analyzer.engine;

import com.analyzer.correlation.BusinessFunctionCorrelator;
import com.analyzer.model.business.BusinessFunctionReport;
import com.analyzer.model.technical.AnalysisReport;
import com.analyzer.model.technical.Endpoint;
import com.analyzer.parsers.BusinessMapParser;
import com.analyzer.parsers.common.DependencyParser;
import com.analyzer.parsers.common.EjbParser;
import com.analyzer.parsers.common.EntryPointParser;
import com.analyzer.parsers.common.JdbcParser;
import com.analyzer.parsers.config.PropertiesParser;
import com.analyzer.parsers.config.SpringConfigParser;
import com.analyzer.parsers.framework.ServletXmlParser;
import com.analyzer.parsers.framework.SpringAnnotationParser;
import com.analyzer.parsers.framework.StrutsXmlParser;
import com.analyzer.reporter.JsonReportGenerator;
import com.analyzer.ui.ConsoleProgressReporter;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Orchestre l'analyse complète d'un ou plusieurs projets.
 * C'est le chef d'orchestre principal du processus.
 */
public class AnalysisEngine {

    // --- Champs de Configuration ---
    private final File projectsPath;
    private final File overridePath;
    private final File businessMapFile;
    private final String springProfile;
    private final File outputDirectory;

    // --- Composants principaux ---
    private final ProjectScanner projectScanner;
    private final ConsoleProgressReporter progressReporter;
    private final JsonReportGenerator reportGenerator;
    private final BusinessMapParser businessMapParser;
    private final BusinessFunctionCorrelator correlator;
    private final SpringConfigParser springConfigParser;

    // --- Listes de Parseurs ---
    private final List<EntryPointParser> entryPointParsers;
    private final List<DependencyParser> dependencyParsers;
    private final PropertiesParser propertiesParser;

    /**
     * Constructeur du moteur d'analyse.
     * Initialise tous les composants et enregistre les parseurs.
     */
    public AnalysisEngine(File projectsPath, File overridePath, File businessMapFile, String springProfile, File outputDirectory) {
        this.projectsPath = projectsPath;
        this.overridePath = overridePath;
        this.businessMapFile = businessMapFile;
        this.springProfile = springProfile;
        this.outputDirectory = outputDirectory;

        // Initialisation des composants
        this.projectScanner = new ProjectScanner();
        this.progressReporter = new ConsoleProgressReporter();
        this.reportGenerator = new JsonReportGenerator();
        this.businessMapParser = new BusinessMapParser();
        this.correlator = new BusinessFunctionCorrelator();
        this.propertiesParser = new PropertiesParser();
        this.springConfigParser = new SpringConfigParser();

        // Enregistrement de tous nos parseurs de points d'entrée
        this.entryPointParsers = List.of(
                new SpringAnnotationParser(),
                new StrutsXmlParser(),
                new ServletXmlParser()
        );
        // Enregistrement de tous nos parseurs de dépendances
        this.dependencyParsers = List.of(
                new JdbcParser(),
                new EjbParser()
        );
    }

    /**
     * Méthode principale qui lance l'intégralité du processus d'analyse.
     */
    public void run() throws IOException {
        List<File> projectsToAnalyze = projectScanner.findProjects(projectsPath);
        if (projectsToAnalyze.isEmpty()) {
            progressReporter.reportNoProjectsFound(projectsPath);
            return;
        }

        prepareOutputDirectory();
        progressReporter.startAnalysis(projectsToAnalyze.size(), projectsPath);

        List<AnalysisReport> allTechnicalReports = new ArrayList<>();

        for (int i = 0; i < projectsToAnalyze.size(); i++) {
            File projectDir = projectsToAnalyze.get(i);
            progressReporter.startProject(projectDir.getName(), i + 1);

            AnalysisReport report = new AnalysisReport(projectDir.getName());
            report.sourcePath = projectDir.getAbsolutePath();

            // 1. Analyser la configuration Spring pour la résolution d'interfaces
            Map<String, String> beanMap = springConfigParser.buildBeanMap(projectDir, this.springProfile);

            // 2. Créer l'index du projet pour une résolution rapide des méthodes
            progressReporter.reportStep(projectDir.getName(), "Création de l'index du code source...");
            JavaProjectIndexer indexer = new JavaProjectIndexer();
            indexer.indexProject(projectDir.toPath(), beanMap);

            // 3. Analyser la configuration .properties
            parseConfiguration(projectDir, report);

            // 4. Phase 1: Découvrir tous les points d'entrée
            progressReporter.reportStep(projectDir.getName(), "Phase 1: Découverte des points d'entrée...");
            discoverEntryPoints(projectDir, report);

            // 5. Phase 2: Analyser en profondeur chaque point d'entrée
            progressReporter.reportStep(projectDir.getName(), "Phase 2: Analyse du graphe d'appels...");
            CallGraphResolver resolver = new CallGraphResolver(dependencyParsers, indexer, projectDir);
            analyzeDependencies(report, indexer, resolver);

            // 6. Sauvegarder le rapport technique et l'ajouter à la liste pour la corrélation
            allTechnicalReports.add(report);
            generateReport(report.applicationName, report, "technique");
        }

        // Étape Finale: Corrélation avec les fonctions d'affaires si un fichier de mapping est fourni
        if (businessMapFile != null && businessMapFile.exists()) {
            correlateBusinessFunctions(allTechnicalReports);
        }

        progressReporter.endAnalysis(projectsToAnalyze.size());
    }

    private void prepareOutputDirectory() throws IOException {
        if (!outputDirectory.exists()) {
            Files.createDirectories(outputDirectory.toPath());
        }
    }

    private void parseConfiguration(File projectDir, AnalysisReport report) {
        progressReporter.reportStep(projectDir.getName(), "Analyse de la configuration .properties...");
        try {
            Map<String, String> configuration = propertiesParser.parseAndMergeProperties(projectDir, overridePath);
            report.configuration.putAll(configuration);
        } catch (IOException e) {
            progressReporter.reportError(projectDir.getName(), "Erreur lors de l'analyse de la configuration.", e);
        }
    }

    private void discoverEntryPoints(File projectDir, AnalysisReport report) {
        try (Stream<Path> stream = Files.walk(projectDir.toPath())) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                for (EntryPointParser parser : entryPointParsers) {
                    if (parser.supports(path.toFile())) {
                        report.endpoints.addAll(parser.parse(path.toFile(), projectDir.toPath()));
                    }
                }
            });
        } catch (IOException e) {
            progressReporter.reportError(projectDir.getName(), "Erreur lors de la découverte des points d'entrée.", e);
        }
    }

    private void analyzeDependencies(AnalysisReport report, JavaProjectIndexer indexer, CallGraphResolver resolver) {
        for (Endpoint endpoint : report.endpoints) {
            if (endpoint.details.controllerClass == null || endpoint.details.handlerMethod == null) continue;

            MethodDeclaration startMethod = indexer.getMethod(endpoint.details.controllerClass, endpoint.details.handlerMethod);
            if (startMethod != null) {
                resolver.resolveAndAnalyze(startMethod, endpoint.details);
            }
        }
    }

    private void correlateBusinessFunctions(List<AnalysisReport> technicalReports) throws IOException {
        progressReporter.reportCorrelationStart();
        Map<String, String> businessMap = businessMapParser.parse(businessMapFile);
        BusinessFunctionReport businessReport = correlator.correlate(technicalReports, businessMap);
        generateReport("metier", businessReport, "metier-consolidé");
        progressReporter.reportCorrelationEnd(outputDirectory.toPath().resolve("rapport-metier-consolidé.json"));
    }

    private void generateReport(String name, Object reportData, String type) throws IOException {
        String reportFileName = String.format("rapport-%s-%s.json", type, name.replaceAll("[^a-zA-Z0-9.-]", "_"));
        Path reportPath = outputDirectory.toPath().resolve(reportFileName);
        reportGenerator.writeReport(reportData, reportPath.toFile());

        if (type.equals("technique")) {
             progressReporter.completeProject(name, reportPath);
        }
    }
}

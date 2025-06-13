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
import com.analyzer.parsers.security.SecurityParser;
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

    // --- Configuration fields ---
    private final File projectsPath;
    private final File overridePath;
    private final File businessMapFile;
    private final String springProfile;
    private final File outputDirectory;

    // --- Core components ---
    private final ProjectScanner projectScanner;
    private final ConsoleProgressReporter progressReporter;
    private final JsonReportGenerator reportGenerator;
    private final BusinessMapParser businessMapParser;
    private final BusinessFunctionCorrelator correlator;
    private final SpringConfigParser springConfigParser;
    private final PropertiesParser propertiesParser;

    // --- Parser lists ---
    private final List<EntryPointParser> entryPointParsers;
    private final List<DependencyParser> dependencyParsers;
    private final SecurityParser securityParser;

    public AnalysisEngine(File projectsPath, File overridePath, File businessMapFile, String springProfile, File outputDirectory) {
        this.projectsPath = projectsPath;
        this.overridePath = overridePath;
        this.businessMapFile = businessMapFile;
        this.springProfile = springProfile;
        this.outputDirectory = outputDirectory;

        // Initialize components
        this.projectScanner = new ProjectScanner();
        this.progressReporter = new ConsoleProgressReporter();
        this.reportGenerator = new JsonReportGenerator();
        this.businessMapParser = new BusinessMapParser();
        this.correlator = new BusinessFunctionCorrelator();
        this.propertiesParser = new PropertiesParser();
        this.springConfigParser = new SpringConfigParser();
        this.securityParser = new SecurityParser();

        // Register all our parsers
        this.entryPointParsers = List.of(
                new SpringAnnotationParser(),
                new StrutsXmlParser(),
                new ServletXmlParser()
        );
        this.dependencyParsers = List.of(
                new JdbcParser(),
                new EjbParser()
        );
    }

    /**
     * Main method to run the entire analysis process.
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

            // 1. Parse Spring config to build the bean map for dependency injection resolution.
            progressReporter.reportStep(projectDir.getName(), "Analyse de la configuration Spring...");
            Map<String, String> beanMap = springConfigParser.buildBeanMap(projectDir, this.springProfile);

            // 2. Index the entire project for fast lookups.
            progressReporter.reportStep(projectDir.getName(), "Création de l'index du code source...");
            JavaProjectIndexer indexer = new JavaProjectIndexer();
            indexer.indexProject(projectDir.toPath(), beanMap);

            // 3. Parse properties files.
            parseConfiguration(projectDir, report);

            // 4. Phase 1: Discover all entry points (e.g., REST controllers, Struts actions).
            progressReporter.reportStep(projectDir.getName(), "Phase 1: Découverte des points d'entrée...");
            discoverEntryPoints(projectDir, report);

            // 5. Phase 2: Perform deep analysis using the call graph for each entry point.
            progressReporter.reportStep(projectDir.getName(), "Phase 2: Analyse du graphe d'appels et des dépendances...");
            CallGraphResolver resolver = new CallGraphResolver(dependencyParsers, securityParser, indexer, projectDir);
            analyzeDependencies(report, indexer, resolver);

            // 6. Finalize report metadata and save it.
            report.endpointCount = report.endpoints.size();
            allTechnicalReports.add(report);
            generateReport(report.applicationName, report, "technique");
        }

        // Final Step: Correlate with business functions if a map is provided.
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
        progressReporter.reportStep(projectDir.getName(), "Analyse des fichiers de propriétés...");
        try {
            Map<String, String> configuration = propertiesParser.parseAndMergeProperties(projectDir, overridePath);
            report.configuration.putAll(configuration);
        } catch (IOException e) {
            progressReporter.reportError(projectDir.getName(), "Erreur lors de l'analyse des fichiers de propriétés.", e);
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
        String reportFileName = String.format("rapport-%s-%s.json", type, name);
        Path reportPath = outputDirectory.toPath().resolve(reportFileName);
        reportGenerator.writeReport(reportData, reportPath.toFile());

        if (type.equals("technique")) {
             progressReporter.completeProject(name, reportPath);
        }
    }
}

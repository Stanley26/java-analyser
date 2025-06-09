package com.legacy.analyzer.scanner;

import com.legacy.analyzer.core.config.AnalyzerConfiguration;
import com.legacy.analyzer.model.WebLogicApplication;
import com.legacy.analyzer.model.WebLogicApplication.ApplicationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebLogicProjectScanner {
    
    private final AnalyzerConfiguration configuration;
    private final FrameworkDetector frameworkDetector;
    
    public List<WebLogicApplication> scanDirectory(Path rootPath) throws IOException {
        log.info("Scan du répertoire: {}", rootPath);
        
        List<WebLogicApplication> applications = new ArrayList<>();
        
        // Recherche des fichiers d'application
        List<Path> applicationFiles = findApplicationFiles(rootPath);
        log.info("Nombre d'applications trouvées: {}", applicationFiles.size());
        
        // Analyse de chaque application
        for (Path appPath : applicationFiles) {
            try {
                WebLogicApplication app = analyzeApplication(appPath);
                if (app != null) {
                    applications.add(app);
                }
            } catch (Exception e) {
                log.error("Erreur lors de l'analyse de l'application: {}", appPath, e);
            }
        }
        
        return applications;
    }
    
    private List<Path> findApplicationFiles(Path rootPath) throws IOException {
        List<Path> applicationFiles = new ArrayList<>();
        
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                
                // Vérifier si le fichier correspond aux patterns d'inclusion
                if (matchesIncludePatterns(fileName) && !matchesExcludePatterns(file)) {
                    applicationFiles.add(file);
                }
                
                return FileVisitResult.CONTINUE;
            }
        });
        
        // Recherche aussi des applications déployées (exploded)
        findExplodedApplications(rootPath, applicationFiles);
        
        return applicationFiles;
    }
    
    private boolean matchesIncludePatterns(String fileName) {
        return configuration.getSource().getIncludePatterns().stream()
                .anyMatch(pattern -> FilenameUtils.wildcardMatch(fileName, pattern));
    }
    
    private boolean matchesExcludePatterns(Path file) {
        String relativePath = file.toString();
        return configuration.getSource().getExcludePatterns().stream()
                .anyMatch(pattern -> FilenameUtils.wildcardMatch(relativePath, pattern));
    }
    
    private void findExplodedApplications(Path rootPath, List<Path> applicationFiles) throws IOException {
        Files.walk(rootPath, 2)
                .filter(Files::isDirectory)
                .filter(this::isExplodedApplication)
                .forEach(applicationFiles::add);
    }
    
    private boolean isExplodedApplication(Path dir) {
        // Vérifier la présence de descripteurs WebLogic
        return Files.exists(dir.resolve("WEB-INF/web.xml")) ||
               Files.exists(dir.resolve("META-INF/application.xml")) ||
               Files.exists(dir.resolve("META-INF/weblogic-application.xml"));
    }
    
    private WebLogicApplication analyzeApplication(Path appPath) throws IOException {
        log.debug("Analyse de l'application: {}", appPath);
        
        WebLogicApplication.WebLogicApplicationBuilder builder = WebLogicApplication.builder()
                .id(generateApplicationId(appPath))
                .name(extractApplicationName(appPath))
                .sourcePath(appPath)
                .type(determineApplicationType(appPath));
        
        // Extraction si nécessaire
        if (Files.isRegularFile(appPath)) {
            Path extractedPath = extractApplication(appPath);
            builder.extractedPath(extractedPath);
            analyzeExtractedApplication(extractedPath, builder);
        } else {
            analyzeExtractedApplication(appPath, builder);
        }
        
        return builder.build();
    }
    
    private ApplicationType determineApplicationType(Path appPath) {
        String fileName = appPath.getFileName().toString().toLowerCase();
        
        if (Files.isDirectory(appPath)) {
            if (Files.exists(appPath.resolve("META-INF/application.xml"))) {
                return ApplicationType.EXPLODED_EAR;
            } else if (Files.exists(appPath.resolve("WEB-INF/web.xml"))) {
                return ApplicationType.EXPLODED_WAR;
            }
        } else {
            if (fileName.endsWith(".ear")) {
                return ApplicationType.EAR;
            } else if (fileName.endsWith(".war")) {
                return ApplicationType.WAR;
            } else if (fileName.endsWith(".jar")) {
                return ApplicationType.JAR;
            }
        }
        
        return ApplicationType.WAR; // Default
    }
    
    private String generateApplicationId(Path appPath) {
        return appPath.getFileName().toString().replaceAll("[^a-zA-Z0-9-_]", "_");
    }
    
    private String extractApplicationName(Path appPath) {
        String fileName = appPath.getFileName().toString();
        return FilenameUtils.removeExtension(fileName);
    }
    
    private Path extractApplication(Path archivePath) throws IOException {
        Path extractDir = configuration.getOutputDirectory()
                .resolve("extracted")
                .resolve(extractApplicationName(archivePath));
        
        if (Files.exists(extractDir)) {
            log.debug("Application déjà extraite: {}", extractDir);
            return extractDir;
        }
        
        log.info("Extraction de l'application: {} vers {}", archivePath, extractDir);
        Files.createDirectories(extractDir);
        
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = extractDir.resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zipFile.getInputStream(entry), entryPath, 
                              StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        
        return extractDir;
    }
    
    private void analyzeExtractedApplication(Path appPath, 
                                           WebLogicApplication.WebLogicApplicationBuilder builder) 
            throws IOException {
        
        // Détection des frameworks
        Set<String> frameworks = frameworkDetector.detectFrameworks(appPath);
        builder.frameworks(frameworks);
        
        // Analyse des descripteurs WebLogic
        WebLogicApplication.DeploymentInfo deploymentInfo = analyzeDeploymentDescriptors(appPath);
        builder.deploymentInfo(deploymentInfo);
        
        // Découverte des modules
        List<WebLogicApplication.Module> modules = discoverModules(appPath);
        builder.modules(modules);
        
        // Extraction des datasources
        if (configuration.getWeblogic().isExtractDatasources()) {
            List<WebLogicApplication.DataSource> dataSources = extractDataSources(appPath);
            builder.dataSources(dataSources);
        }
        
        // Analyse des bibliothèques
        List<WebLogicApplication.Library> libraries = analyzeLibraries(appPath);
        builder.libraries(libraries);
    }
    
    private WebLogicApplication.DeploymentInfo analyzeDeploymentDescriptors(Path appPath) 
            throws IOException {
        WebLogicApplication.DeploymentInfo.DeploymentInfoBuilder infoBuilder = 
                WebLogicApplication.DeploymentInfo.builder();
        
        Map<String, String> descriptors = new HashMap<>();
        
        // web.xml
        Path webXml = appPath.resolve("WEB-INF/web.xml");
        if (Files.exists(webXml)) {
            descriptors.put("web.xml", Files.readString(webXml));
            // Parser le web.xml pour extraire les infos de base
            parseWebXml(webXml, infoBuilder);
        }
        
        // weblogic.xml
        Path weblogicXml = appPath.resolve("WEB-INF/weblogic.xml");
        if (Files.exists(weblogicXml)) {
            descriptors.put("weblogic.xml", Files.readString(weblogicXml));
            parseWebLogicXml(weblogicXml, infoBuilder);
        }
        
        // application.xml pour les EAR
        Path applicationXml = appPath.resolve("META-INF/application.xml");
        if (Files.exists(applicationXml)) {
            descriptors.put("application.xml", Files.readString(applicationXml));
            parseApplicationXml(applicationXml, infoBuilder);
        }
        
        // weblogic-application.xml
        Path weblogicAppXml = appPath.resolve("META-INF/weblogic-application.xml");
        if (Files.exists(weblogicAppXml)) {
            descriptors.put("weblogic-application.xml", Files.readString(weblogicAppXml));
            parseWebLogicApplicationXml(weblogicAppXml, infoBuilder);
        }
        
        infoBuilder.weblogicDescriptors(descriptors);
        return infoBuilder.build();
    }
    
    private void parseWebXml(Path webXml, WebLogicApplication.DeploymentInfo.DeploymentInfoBuilder builder) {
        // Implémentation basique - sera enrichie avec JDOM2
        try {
            String content = Files.readString(webXml);
            if (content.contains("<display-name>")) {
                String displayName = extractXmlValue(content, "display-name");
                builder.displayName(displayName);
            }
            if (content.contains("<description>")) {
                String description = extractXmlValue(content, "description");
                builder.description(description);
            }
        } catch (IOException e) {
            log.error("Erreur lors du parsing de web.xml", e);
        }
    }
    
    private void parseWebLogicXml(Path weblogicXml, 
                                  WebLogicApplication.DeploymentInfo.DeploymentInfoBuilder builder) {
        try {
            String content = Files.readString(weblogicXml);
            if (content.contains("<context-root>")) {
                String contextRoot = extractXmlValue(content, "context-root");
                builder.contextRoot(contextRoot);
            }
        } catch (IOException e) {
            log.error("Erreur lors du parsing de weblogic.xml", e);
        }
    }
    
    private void parseApplicationXml(Path applicationXml, 
                                    WebLogicApplication.DeploymentInfo.DeploymentInfoBuilder builder) {
        // Parsing basique - à enrichir
    }
    
    private void parseWebLogicApplicationXml(Path weblogicAppXml, 
                                           WebLogicApplication.DeploymentInfo.DeploymentInfoBuilder builder) {
        // Parsing basique - à enrichir
    }
    
    private String extractXmlValue(String xml, String tagName) {
        int start = xml.indexOf("<" + tagName + ">");
        int end = xml.indexOf("</" + tagName + ">");
        if (start != -1 && end != -1) {
            return xml.substring(start + tagName.length() + 2, end).trim();
        }
        return null;
    }
    
    private List<WebLogicApplication.Module> discoverModules(Path appPath) throws IOException {
        List<WebLogicApplication.Module> modules = new ArrayList<>();
        
        // Pour un EAR, chercher les modules WAR et EJB
        if (Files.exists(appPath.resolve("META-INF/application.xml"))) {
            Files.walk(appPath, 2)
                    .filter(p -> p.toString().endsWith(".war") || p.toString().endsWith(".jar"))
                    .forEach(modulePath -> {
                        try {
                            WebLogicApplication.Module module = analyzeModule(modulePath);
                            modules.add(module);
                        } catch (IOException e) {
                            log.error("Erreur lors de l'analyse du module: {}", modulePath, e);
                        }
                    });
        } else {
            // Application WAR simple
            WebLogicApplication.Module module = analyzeModule(appPath);
            modules.add(module);
        }
        
        return modules;
    }
    
    private WebLogicApplication.Module analyzeModule(Path modulePath) throws IOException {
        return WebLogicApplication.Module.builder()
                .name(modulePath.getFileName().toString())
                .type(determineModuleType(modulePath))
                .path(modulePath)
                .frameworks(frameworkDetector.detectFrameworks(modulePath))
                .build();
    }
    
    private WebLogicApplication.ModuleType determineModuleType(Path modulePath) {
        if (Files.exists(modulePath.resolve("WEB-INF/web.xml"))) {
            return WebLogicApplication.ModuleType.WEB;
        } else if (Files.exists(modulePath.resolve("META-INF/ejb-jar.xml"))) {
            return WebLogicApplication.ModuleType.EJB;
        }
        return WebLogicApplication.ModuleType.UTILITY;
    }
    
    private List<WebLogicApplication.DataSource> extractDataSources(Path appPath) {
        // Extraction des datasources depuis les descripteurs WebLogic
        List<WebLogicApplication.DataSource> dataSources = new ArrayList<>();
        // Implémentation à compléter avec parsing XML
        return dataSources;
    }
    
    private List<WebLogicApplication.Library> analyzeLibraries(Path appPath) throws IOException {
        List<WebLogicApplication.Library> libraries = new ArrayList<>();
        
        Path libPath = appPath.resolve("WEB-INF/lib");
        if (Files.exists(libPath)) {
            Files.list(libPath)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jarPath -> {
                        WebLogicApplication.Library library = WebLogicApplication.Library.builder()
                                .jarFile(jarPath.getFileName().toString())
                                .build();
                        libraries.add(library);
                    });
        }
        
        return libraries;
    }
}
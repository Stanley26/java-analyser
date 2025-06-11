package com.legacy.analyzer.generators;

import com.legacy.analyzer.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class UMLDiagramGenerator {
    
    private final String PLANTUML_HEADER = "@startuml\n" +
            "skinparam backgroundColor #FEFEFE\n" +
            "skinparam classBackgroundColor #F0F0F0\n" +
            "skinparam classBorderColor #888888\n" +
            "skinparam stereotypeCBackgroundColor #ADD8E6\n" +
            "skinparam stereotypeIBackgroundColor #B4A7E5\n";
    
    public void generateUMLDiagrams(List<AnalysisResult> results, Path outputDir) throws IOException {
        Path umlDir = outputDir.resolve("uml-diagrams");
        Files.createDirectories(umlDir);
        
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            
            // Générer différents types de diagrammes
            generateClassDiagram(app, umlDir);
            generateSequenceDiagrams(app, umlDir);
            generateComponentDiagram(app, umlDir);
            generateDeploymentDiagram(app, umlDir);
        }
        
        // Générer un diagramme global du système
        generateSystemOverviewDiagram(results, umlDir);
    }
    
    private void generateClassDiagram(WebLogicApplication app, Path outputDir) throws IOException {
        StringBuilder plantUml = new StringBuilder(PLANTUML_HEADER);
        plantUml.append("title Class Diagram - ").append(app.getName()).append("\n\n");
        
        // Grouper les endpoints par classe
        Map<String, List<Endpoint>> endpointsByClass = groupEndpointsByClass(app);
        
        // Créer les packages
        Map<String, Set<String>> packageStructure = buildPackageStructure(endpointsByClass.keySet());
        
        for (Map.Entry<String, Set<String>> packageEntry : packageStructure.entrySet()) {
            String packageName = packageEntry.getKey();
            Set<String> classes = packageEntry.getValue();
            
            plantUml.append("package \"").append(packageName).append("\" {\n");
            
            for (String className : classes) {
                String simpleClassName = getSimpleClassName(className);
                List<Endpoint> classEndpoints = endpointsByClass.get(className);
                
                if (classEndpoints != null && !classEndpoints.isEmpty()) {
                    // Déterminer le stéréotype
                    String stereotype = determineStereotype(classEndpoints.get(0));
                    plantUml.append("  class ").append(simpleClassName).append(" ")
                           .append(stereotype).append(" {\n");
                    
                    // Ajouter les méthodes (endpoints)
                    for (Endpoint endpoint : classEndpoints) {
                        plantUml.append("    +").append(endpoint.getMethodName())
                               .append("(");
                        
                        if (endpoint.getParameters() != null) {
                            String params = endpoint.getParameters().stream()
                                    .map(p -> p.getType() + " " + p.getName())
                                    .collect(Collectors.joining(", "));
                            plantUml.append(params);
                        }
                        
                        plantUml.append(")\n");
                    }
                    
                    plantUml.append("  }\n");
                }
            }
            
            plantUml.append("}\n\n");
        }
        
        // Ajouter les relations de dépendances
        addDependencyRelations(app, plantUml);
        
        plantUml.append("@enduml");
        
        Path classFile = outputDir.resolve(sanitizeFileName(app.getName()) + "-class-diagram.puml");
        Files.writeString(classFile, plantUml.toString());
    }
    
    private void generateSequenceDiagrams(WebLogicApplication app, Path outputDir) throws IOException {
        // Générer un diagramme de séquence pour les endpoints principaux
        List<Endpoint> mainEndpoints = selectMainEndpoints(app);
        
        for (Endpoint endpoint : mainEndpoints) {
            StringBuilder plantUml = new StringBuilder(PLANTUML_HEADER);
            plantUml.append("title Sequence Diagram - ").append(endpoint.getUrl()).append("\n\n");
            
            // Acteurs
            plantUml.append("actor Client\n");
            plantUml.append("participant \"").append(endpoint.getClassName()).append("\" as Controller\n");
            
            // Ajouter les participants selon les dépendances
            if (endpoint.getDependencies() != null) {
                if (endpoint.getDependencies().getDatabases() != null) {
                    plantUml.append("database \"Database\" as DB\n");
                }
                if (endpoint.getDependencies().getEjbs() != null) {
                    for (Dependencies.EJBDependency ejb : endpoint.getDependencies().getEjbs()) {
                        plantUml.append("participant \"").append(ejb.getEjbName()).append("\" as ")
                               .append(sanitizeId(ejb.getEjbName())).append("\n");
                    }
                }
                if (endpoint.getDependencies().getWebServices() != null) {
                    plantUml.append("participant \"External Service\" as ExtService\n");
                }
            }
            
            plantUml.append("\n");
            
            // Séquence d'appels
            plantUml.append("Client -> Controller : ").append(endpoint.getHttpMethods().iterator().next())
                   .append(" ").append(endpoint.getUrl()).append("\n");
            plantUml.append("activate Controller\n");
            
            // Ajouter les appels basés sur le pseudo-code
            if (endpoint.getPseudoCode() != null && endpoint.getPseudoCode().getBlocks() != null) {
                generateSequenceFromPseudoCode(endpoint.getPseudoCode().getBlocks(), plantUml, "Controller");
            }
            
            plantUml.append("Controller --> Client : Response\n");
            plantUml.append("deactivate Controller\n");
            
            plantUml.append("\n@enduml");
            
            String fileName = sanitizeFileName(endpoint.getClassName() + "-" + endpoint.getMethodName()) 
                            + "-sequence.puml";
            Path sequenceFile = outputDir.resolve(fileName);
            Files.writeString(sequenceFile, plantUml.toString());
        }
    }
    
    private void generateComponentDiagram(WebLogicApplication app, Path outputDir) throws IOException {
        StringBuilder plantUml = new StringBuilder(PLANTUML_HEADER);
        plantUml.append("title Component Diagram - ").append(app.getName()).append("\n\n");
        
        // Définir les composants
        plantUml.append("package \"").append(app.getName()).append("\" {\n");
        
        // Couche présentation
        plantUml.append("  package \"Presentation Layer\" {\n");
        Set<String> frameworks = app.getFrameworks();
        if (frameworks.contains("struts")) {
            plantUml.append("    [Struts Actions] as struts\n");
        }
        if (frameworks.contains("spring-mvc")) {
            plantUml.append("    [Spring Controllers] as spring\n");
        }
        if (frameworks.contains("servlet")) {
            plantUml.append("    [Servlets] as servlets\n");
        }
        if (frameworks.contains("jax-rs")) {
            plantUml.append("    [REST Services] as rest\n");
        }
        plantUml.append("  }\n\n");
        
        // Couche métier
        plantUml.append("  package \"Business Layer\" {\n");
        if (app.getGlobalDependencies() != null && app.getGlobalDependencies().getEjbs() != null) {
            plantUml.append("    [EJB Components] as ejb\n");
        }
        plantUml.append("    [Business Logic] as business\n");
        plantUml.append("  }\n\n");
        
        // Couche d'intégration
        plantUml.append("  package \"Integration Layer\" {\n");
        if (app.getGlobalDependencies() != null) {
            if (app.getGlobalDependencies().getDatabases() != null) {
                plantUml.append("    [DAO Layer] as dao\n");
            }
            if (app.getGlobalDependencies().getWebServices() != null) {
                plantUml.append("    [Web Service Clients] as wsclient\n");
            }
            if (app.getGlobalDependencies().getJmsQueues() != null) {
                plantUml.append("    [JMS Components] as jms\n");
            }
        }
        plantUml.append("  }\n");
        plantUml.append("}\n\n");
        
        // Systèmes externes
        if (app.getGlobalDependencies() != null) {
            if (app.getGlobalDependencies().getDatabases() != null) {
                plantUml.append("database \"Database\" as db\n");
            }
            if (app.getGlobalDependencies().getCobolPrograms() != null) {
                plantUml.append("component \"Mainframe\\nCOBOL\" as cobol\n");
            }
            if (app.getGlobalDependencies().getWebServices() != null) {
                plantUml.append("cloud \"External Services\" as external\n");
            }
        }
        
        // Ajouter les relations
        addComponentRelations(app, plantUml);
        
        plantUml.append("\n@enduml");
        
        Path componentFile = outputDir.resolve(sanitizeFileName(app.getName()) + "-component-diagram.puml");
        Files.writeString(componentFile, plantUml.toString());
    }
    
    private void generateDeploymentDiagram(WebLogicApplication app, Path outputDir) throws IOException {
        StringBuilder plantUml = new StringBuilder(PLANTUML_HEADER);
        plantUml.append("title Deployment Diagram - ").append(app.getName()).append("\n\n");
        
        // Nœud WebLogic
        plantUml.append("node \"WebLogic Server\" {\n");
        plantUml.append("  artifact \"").append(app.getName()).append(".").append(app.getType()).append("\" {\n");
        
        if (app.getModules() != null) {
            for (WebLogicApplication.Module module : app.getModules()) {
                plantUml.append("    component \"").append(module.getName()).append("\"\n");
            }
        }
        
        plantUml.append("  }\n");
        
        // DataSources
        if (app.getDataSources() != null) {
            plantUml.append("  component \"Data Sources\" {\n");
            for (WebLogicApplication.DataSource ds : app.getDataSources()) {
                plantUml.append("    component \"").append(ds.getName()).append("\"\n");
            }
            plantUml.append("  }\n");
        }
        
        plantUml.append("}\n\n");
        
        // Bases de données
        if (app.getGlobalDependencies() != null && app.getGlobalDependencies().getDatabases() != null) {
            Set<String> dbTypes = app.getGlobalDependencies().getDatabases().stream()
                    .map(Dependencies.DatabaseDependency::getDatabaseType)
                    .collect(Collectors.toSet());
            
            for (String dbType : dbTypes) {
                plantUml.append("database \"").append(dbType).append(" Database\" as ")
                       .append(dbType.toLowerCase()).append("\n");
            }
        }
        
        // Systèmes externes
        if (app.getGlobalDependencies() != null) {
            if (app.getGlobalDependencies().getCobolPrograms() != null) {
                plantUml.append("node \"Mainframe\" {\n");
                plantUml.append("  component \"COBOL Programs\"\n");
                plantUml.append("}\n");
            }
            
            if (app.getGlobalDependencies().getWebServices() != null) {
                plantUml.append("cloud \"External Services\" {\n");
                Set<String> services = app.getGlobalDependencies().getWebServices().stream()
                        .map(Dependencies.WebServiceDependency::getServiceName)
                        .collect(Collectors.toSet());
                
                for (String service : services) {
                    plantUml.append("  component \"").append(service).append("\"\n");
                }
                plantUml.append("}\n");
            }
        }
        
        plantUml.append("\n@enduml");
        
        Path deploymentFile = outputDir.resolve(sanitizeFileName(app.getName()) + "-deployment-diagram.puml");
        Files.writeString(deploymentFile, plantUml.toString());
    }
    
    private void generateSystemOverviewDiagram(List<AnalysisResult> results, Path outputDir) 
            throws IOException {
        StringBuilder plantUml = new StringBuilder(PLANTUML_HEADER);
        plantUml.append("title System Overview - Application Ecosystem\n\n");
        plantUml.append("!define ICONURL https://raw.githubusercontent.com/tupadr3/plantuml-icon-font-sprites/v2.4.0\n");
        plantUml.append("!includeurl ICONURL/common.puml\n");
        plantUml.append("!includeurl ICONURL/devicons/java.puml\n\n");
        
        // Applications
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            String appId = sanitizeId(app.getName());
            
            plantUml.append("component \"").append(app.getName()).append("\" as ").append(appId)
                   .append(" <<application>>\n");
        }
        
        plantUml.append("\n");
        
        // Ajouter les relations inter-applications
        addInterApplicationRelations(results, plantUml);
        
        plantUml.append("\n@enduml");
        
        Path overviewFile = outputDir.resolve("system-overview-diagram.puml");
        Files.writeString(overviewFile, plantUml.toString());
    }
    
    // Méthodes utilitaires
    
    private Map<String, List<Endpoint>> groupEndpointsByClass(WebLogicApplication app) {
        List<Endpoint> allEndpoints = new ArrayList<>();
        
        if (app.getEndpoints() != null) {
            allEndpoints.addAll(app.getEndpoints());
        }
        
        if (app.getModules() != null) {
            for (WebLogicApplication.Module module : app.getModules()) {
                if (module.getEndpoints() != null) {
                    allEndpoints.addAll(module.getEndpoints());
                }
            }
        }
        
        return allEndpoints.stream()
                .filter(e -> e.getClassName() != null)
                .collect(Collectors.groupingBy(Endpoint::getClassName));
    }
    
    private Map<String, Set<String>> buildPackageStructure(Set<String> classNames) {
        Map<String, Set<String>> packages = new TreeMap<>();
        
        for (String className : classNames) {
            String packageName = getPackageName(className);
            packages.computeIfAbsent(packageName, k -> new TreeSet<>()).add(className);
        }
        
        return packages;
    }
    
    private String getPackageName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(0, lastDot) : "default";
    }
    
    private String getSimpleClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
    
    private String determineStereotype(Endpoint endpoint) {
        if (endpoint.getMetadata() != null) {
            String framework = (String) endpoint.getMetadata().get("framework");
            if (framework != null) {
                switch (framework.toLowerCase()) {
                    case "spring": return "<<Controller>>";
                    case "struts": return "<<Action>>";
                    case "jax-rs": return "<<Resource>>";
                    case "servlet": return "<<Servlet>>";
                    default: return "<<Component>>";
                }
            }
        }
        return "<<Component>>";
    }
    
    private void addDependencyRelations(WebLogicApplication app, StringBuilder plantUml) {
        // Analyser les dépendances pour créer des relations
        if (app.getGlobalDependencies() == null) return;
        
        // Relations avec les EJB
        if (app.getGlobalDependencies().getEjbs() != null) {
            for (Dependencies.EJBDependency ejb : app.getGlobalDependencies().getEjbs()) {
                // Simplification : on pourrait être plus précis
                plantUml.append("note right : Uses EJB ").append(ejb.getEjbName()).append("\n");
            }
        }
    }
    
    private List<Endpoint> selectMainEndpoints(WebLogicApplication app) {
        List<Endpoint> allEndpoints = new ArrayList<>();
        
        if (app.getEndpoints() != null) {
            allEndpoints.addAll(app.getEndpoints());
        }
        
        if (app.getModules() != null) {
            for (WebLogicApplication.Module module : app.getModules()) {
                if (module.getEndpoints() != null) {
                    allEndpoints.addAll(module.getEndpoints());
                }
            }
        }
        
        // Sélectionner les endpoints les plus importants (max 5)
        return allEndpoints.stream()
                .filter(e -> e.getPseudoCode() != null)
                .limit(5)
                .collect(Collectors.toList());
    }
    
    private void generateSequenceFromPseudoCode(List<Endpoint.PseudoCodeBlock> blocks, 
                                              StringBuilder plantUml, String currentActor) {
        for (Endpoint.PseudoCodeBlock block : blocks) {
            if (block.getType().equals("CALL") && block.getContent().contains("APPELER")) {
                String target = extractCallTarget(block.getContent());
                if (target.contains("DAO") || target.contains("Repository")) {
                    plantUml.append(currentActor).append(" -> DB : ").append(target).append("\n");
                } else if (target.contains("Service") || target.contains("EJB")) {
                    String serviceId = sanitizeId(target);
                    plantUml.append(currentActor).append(" -> ").append(serviceId)
                           .append(" : ").append(target).append("\n");
                }
            }
        }
    }
    
    private String extractCallTarget(String content) {
        // Extraire le nom de la méthode appelée depuis le pseudo-code
        if (content.contains("APPELER ")) {
            return content.substring(content.indexOf("APPELER ") + 8).trim();
        }
        return content;
    }
    
    private void addComponentRelations(WebLogicApplication app, StringBuilder plantUml) {
        // Relations entre composants
        Set<String> frameworks = app.getFrameworks();
        
        if (frameworks.contains("struts")) {
            plantUml.append("struts --> business\n");
        }
        if (frameworks.contains("spring-mvc")) {
            plantUml.append("spring --> business\n");
        }
        if (frameworks.contains("servlet")) {
            plantUml.append("servlets --> business\n");
        }
        
        if (app.getGlobalDependencies() != null) {
            if (app.getGlobalDependencies().getEjbs() != null) {
                plantUml.append("business --> ejb\n");
            }
            if (app.getGlobalDependencies().getDatabases() != null) {
                plantUml.append("business --> dao\n");
                plantUml.append("dao --> db\n");
            }
            if (app.getGlobalDependencies().getWebServices() != null) {
                plantUml.append("business --> wsclient\n");
                plantUml.append("wsclient --> external\n");
            }
            if (app.getGlobalDependencies().getCobolPrograms() != null) {
                plantUml.append("business --> cobol\n");
            }
        }
    }
    
    private void addInterApplicationRelations(List<AnalysisResult> results, StringBuilder plantUml) {
        // Analyser les dépendances inter-applications
        Map<String, Set<String>> appDependencies = new HashMap<>();
        
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            String appId = sanitizeId(app.getName());
            Set<String> dependencies = new HashSet<>();
            
            if (app.getGlobalDependencies() != null) {
                // Analyser les appels EJB pour détecter les dépendances inter-apps
                if (app.getGlobalDependencies().getEjbs() != null) {
                    for (Dependencies.EJBDependency ejb : app.getGlobalDependencies().getEjbs()) {
                        // Essayer de déterminer l'application cible
                        String targetApp = guessTargetApplication(ejb.getEjbName(), results);
                        if (targetApp != null && !targetApp.equals(app.getName())) {
                            dependencies.add(targetApp);
                        }
                    }
                }
                
                // Analyser les Web Services
                if (app.getGlobalDependencies().getWebServices() != null) {
                    for (Dependencies.WebServiceDependency ws : app.getGlobalDependencies().getWebServices()) {
                        String targetApp = guessTargetApplication(ws.getServiceName(), results);
                        if (targetApp != null && !targetApp.equals(app.getName())) {
                            dependencies.add(targetApp);
                        }
                    }
                }
            }
            
            appDependencies.put(appId, dependencies);
        }
        
        // Générer les relations
        for (Map.Entry<String, Set<String>> entry : appDependencies.entrySet()) {
            String sourceApp = entry.getKey();
            for (String targetApp : entry.getValue()) {
                String targetId = sanitizeId(targetApp);
                plantUml.append(sourceApp).append(" --> ").append(targetId).append("\n");
            }
        }
    }
    
    private String guessTargetApplication(String componentName, List<AnalysisResult> results) {
        // Heuristique pour deviner l'application cible basée sur le nom du composant
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            String appNameLower = app.getName().toLowerCase();
            String componentLower = componentName.toLowerCase();
            
            if (componentLower.contains(appNameLower) || 
                appNameLower.contains(componentLower.split("\\.")[0])) {
                return app.getName();
            }
        }
        
        return null;
    }
    
    private String sanitizeId(String id) {
        return id.replaceAll("[^a-zA-Z0-9]", "_");
    }
    
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
package com.legacy.analyzer.generators;

import com.legacy.analyzer.core.config.AnalyzerConfiguration;
import com.legacy.analyzer.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerator {
    
    private final AnalyzerConfiguration configuration;
    
    public void generateExcelReports(List<AnalysisResult> results) throws IOException {
        Path reportsDir = configuration.getOutputDirectory().resolve("reports");
        Files.createDirectories(reportsDir);
        
        // Générer le rapport global
        generateGlobalReport(results, reportsDir);
        
        // Générer les rapports par application
        Path perAppDir = reportsDir.resolve("per-application");
        Files.createDirectories(perAppDir);
        
        for (AnalysisResult result : results) {
            if (result.isSuccess()) {
                generateApplicationReport(result, perAppDir);
            }
        }
    }
    
    private void generateGlobalReport(List<AnalysisResult> results, Path reportsDir) 
            throws IOException {
        
        log.info("Génération du rapport global...");
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        
        // Feuille 1: Vue d'ensemble
        createOverviewSheet(workbook, results, headerStyle, titleStyle, dataStyle, numberStyle);
        
        // Feuille 2: Liste des applications
        createApplicationsListSheet(workbook, results, headerStyle, dataStyle);
        
        // Feuille 3: Analyse des endpoints
        createEndpointsAnalysisSheet(workbook, results, headerStyle, dataStyle);
        
        // Feuille 4: Matrice des dépendances
        createDependencyMatrixSheet(workbook, results, headerStyle, dataStyle);
        
        // Feuille 5: Technologies utilisées
        createTechnologySheet(workbook, results, headerStyle, dataStyle);
        
        // Feuille 6: Statistiques détaillées
        createDetailedStatisticsSheet(workbook, results, headerStyle, dataStyle, numberStyle);
        
        // Sauvegarder le fichier
        Path reportFile = reportsDir.resolve("global-analysis-report.xlsx");
        try (FileOutputStream fileOut = new FileOutputStream(reportFile.toFile())) {
            workbook.write(fileOut);
        }
        workbook.close();
        
        log.info("Rapport global généré: {}", reportFile);
    }
    
    private void createOverviewSheet(XSSFWorkbook workbook, List<AnalysisResult> results,
                                   CellStyle headerStyle, CellStyle titleStyle,
                                   CellStyle dataStyle, CellStyle numberStyle) {
        
        XSSFSheet sheet = workbook.createSheet("Vue d'ensemble");
        int rowNum = 0;
        
        // Titre
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Analyse de l'Écosystème Legacy - Vue d'Ensemble");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        
        // Date
        Row dateRow = sheet.createRow(rowNum++);
        dateRow.createCell(0).setCellValue("Date de l'analyse:");
        dateRow.createCell(1).setCellValue(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        
        rowNum++; // Ligne vide
        
        // Statistiques principales
        Row statsHeaderRow = sheet.createRow(rowNum++);
        statsHeaderRow.createCell(0).setCellValue("Statistiques Globales");
        statsHeaderRow.getCell(0).setCellStyle(headerStyle);
        
        // Calcul des statistiques
        long successCount = results.stream().filter(AnalysisResult::isSuccess).count();
        long failureCount = results.size() - successCount;
        int totalEndpoints = results.stream()
                .filter(AnalysisResult::isSuccess)
                .mapToInt(AnalysisResult::getEndpointsCount)
                .sum();
        
        // Affichage des stats
        createStatRow(sheet, rowNum++, "Nombre total d'applications", results.size(), dataStyle, numberStyle);
        createStatRow(sheet, rowNum++, "Applications analysées avec succès", successCount, dataStyle, numberStyle);
        createStatRow(sheet, rowNum++, "Applications en erreur", failureCount, dataStyle, numberStyle);
        createStatRow(sheet, rowNum++, "Nombre total d'endpoints", totalEndpoints, dataStyle, numberStyle);
        
        rowNum++; // Ligne vide
        
        // Répartition par framework
        Row frameworkHeaderRow = sheet.createRow(rowNum++);
        frameworkHeaderRow.createCell(0).setCellValue("Répartition par Framework");
        frameworkHeaderRow.getCell(0).setCellStyle(headerStyle);
        
        Map<String, Long> frameworkCount = results.stream()
                .filter(AnalysisResult::isSuccess)
                .map(AnalysisResult::getApplication)
                .flatMap(app -> app.getFrameworks().stream())
                .collect(Collectors.groupingBy(f -> f, Collectors.counting()));
        
        for (Map.Entry<String, Long> entry : frameworkCount.entrySet()) {
            createStatRow(sheet, rowNum++, entry.getKey(), entry.getValue(), dataStyle, numberStyle);
        }
        
        // Ajuster les largeurs de colonnes
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void createApplicationsListSheet(XSSFWorkbook workbook, List<AnalysisResult> results,
                                           CellStyle headerStyle, CellStyle dataStyle) {
        
        XSSFSheet sheet = workbook.createSheet("Liste des Applications");
        int rowNum = 0;
        
        // En-têtes
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Application", "Type", "Frameworks", "Endpoints", 
                          "Bases de données", "EJB", "Cobol", "Statut"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Données
        for (AnalysisResult result : results) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;
            
            WebLogicApplication app = result.getApplication();
            
            row.createCell(colNum++).setCellValue(app.getName());
            row.createCell(colNum++).setCellValue(app.getType() != null ? 
                    app.getType().toString() : "");
            row.createCell(colNum++).setCellValue(app.getFrameworks() != null ? 
                    String.join(", ", app.getFrameworks()) : "");
            row.createCell(colNum++).setCellValue(result.getEndpointsCount());
            
            // Compter les dépendances
            if (app.getGlobalDependencies() != null) {
                row.createCell(colNum++).setCellValue(
                        app.getGlobalDependencies().getDatabases() != null ? 
                        app.getGlobalDependencies().getDatabases().size() : 0);
                row.createCell(colNum++).setCellValue(
                        app.getGlobalDependencies().getEjbs() != null ? 
                        app.getGlobalDependencies().getEjbs().size() : 0);
                row.createCell(colNum++).setCellValue(
                        app.getGlobalDependencies().getCobolPrograms() != null ? 
                        app.getGlobalDependencies().getCobolPrograms().size() : 0);
            } else {
                row.createCell(colNum++).setCellValue(0);
                row.createCell(colNum++).setCellValue(0);
                row.createCell(colNum++).setCellValue(0);
            }
            
            row.createCell(colNum++).setCellValue(result.isSuccess() ? "Succès" : "Erreur");
        }
        
        // Ajuster les largeurs
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void createEndpointsAnalysisSheet(XSSFWorkbook workbook, List<AnalysisResult> results,
                                            CellStyle headerStyle, CellStyle dataStyle) {
        
        XSSFSheet sheet = workbook.createSheet("Analyse des Endpoints");
        int rowNum = 0;
        
        // En-têtes
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Application", "Module", "URL", "Méthodes HTTP", 
                          "Classe", "Méthode", "Paramètres", "Sécurité"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Limiter à 1000 endpoints pour éviter un fichier trop gros
        int endpointCount = 0;
        
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
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
            
            for (Endpoint endpoint : allEndpoints) {
                if (endpointCount++ > 1000) break;
                
                Row row = sheet.createRow(rowNum++);
                int colNum = 0;
                
                row.createCell(colNum++).setCellValue(app.getName());
                row.createCell(colNum++).setCellValue(endpoint.getModuleName() != null ? 
                        endpoint.getModuleName() : "");
                row.createCell(colNum++).setCellValue(endpoint.getUrl());
                row.createCell(colNum++).setCellValue(endpoint.getHttpMethods() != null ?
                        endpoint.getHttpMethods().stream()
                                .map(Enum::toString)
                                .collect(Collectors.joining(", ")) : "");
                row.createCell(colNum++).setCellValue(endpoint.getClassName());
                row.createCell(colNum++).setCellValue(endpoint.getMethodName());
                row.createCell(colNum++).setCellValue(endpoint.getParameters() != null ?
                        endpoint.getParameters().size() : 0);
                row.createCell(colNum++).setCellValue(endpoint.getSecurity() != null &&
                        endpoint.getSecurity().isRequiresAuthentication() ? "Oui" : "Non");
            }
        }
        
        // Note si tronqué
        if (endpointCount > 1000) {
            Row noteRow = sheet.createRow(rowNum++);
            noteRow.createCell(0).setCellValue("Note: Liste limitée aux 1000 premiers endpoints");
        }
        
        // Ajuster les largeurs
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void createDependencyMatrixSheet(XSSFWorkbook workbook, List<AnalysisResult> results,
                                           CellStyle headerStyle, CellStyle dataStyle) {
        
        XSSFSheet sheet = workbook.createSheet("Matrice des Dépendances");
        
        // Collecter toutes les applications et dépendances
        List<String> appNames = results.stream()
                .filter(AnalysisResult::isSuccess)
                .map(r -> r.getApplication().getName())
                .sorted()
                .collect(Collectors.toList());
        
        Set<String> allDependencies = new HashSet<>();
        
        // Collecter toutes les dépendances uniques
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            if (app.getGlobalDependencies() != null) {
                // Bases de données
                if (app.getGlobalDependencies().getDatabases() != null) {
                    app.getGlobalDependencies().getDatabases()
                            .forEach(db -> allDependencies.add("DB:" + db.getDataSourceName()));
                }
                
                // EJB
                if (app.getGlobalDependencies().getEjbs() != null) {
                    app.getGlobalDependencies().getEjbs()
                            .forEach(ejb -> allDependencies.add("EJB:" + ejb.getEjbName()));
                }
                
                // Cobol
                if (app.getGlobalDependencies().getCobolPrograms() != null) {
                    app.getGlobalDependencies().getCobolPrograms()
                            .forEach(cobol -> allDependencies.add("COBOL:" + cobol.getProgramName()));
                }
            }
        }
        
        List<String> depList = new ArrayList<>(allDependencies);
        depList.sort(String::compareTo);
        
        // Créer la matrice
        int rowNum = 0;
        
        // En-tête avec les dépendances
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("Application \\ Dépendance");
        headerRow.getCell(0).setCellStyle(headerStyle);
        
        for (int i = 0; i < depList.size(); i++) {
            Cell cell = headerRow.createCell(i + 1);
            cell.setCellValue(depList.get(i));
            cell.setCellStyle(headerStyle);
        }
        
        // Remplir la matrice
        for (String appName : appNames) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(appName);
            row.getCell(0).setCellStyle(headerStyle);
            
            // Trouver l'application
            Optional<AnalysisResult> resultOpt = results.stream()
                    .filter(r -> r.isSuccess() && r.getApplication().getName().equals(appName))
                    .findFirst();
            
            if (resultOpt.isPresent()) {
                WebLogicApplication app = resultOpt.get().getApplication();
                Set<String> appDeps = collectAppDependencies(app);
                
                for (int i = 0; i < depList.size(); i++) {
                    Cell cell = row.createCell(i + 1);
                    cell.setCellValue(appDeps.contains(depList.get(i)) ? "X" : "");
                    cell.setCellStyle(dataStyle);
                }
            }
        }
        
        // Ajuster les largeurs
        sheet.setColumnWidth(0, 5000); // Première colonne plus large
        for (int i = 1; i <= depList.size(); i++) {
            sheet.setColumnWidth(i, 3000);
        }
    }
    
    private Set<String> collectAppDependencies(WebLogicApplication app) {
        Set<String> deps = new HashSet<>();
        
        if (app.getGlobalDependencies() != null) {
            if (app.getGlobalDependencies().getDatabases() != null) {
                app.getGlobalDependencies().getDatabases()
                        .forEach(db -> deps.add("DB:" + db.getDataSourceName()));
            }
            
            if (app.getGlobalDependencies().getEjbs() != null) {
                app.getGlobalDependencies().getEjbs()
                        .forEach(ejb -> deps.add("EJB:" + ejb.getEjbName()));
            }
            
            if (app.getGlobalDependencies().getCobolPrograms() != null) {
                app.getGlobalDependencies().getCobolPrograms()
                        .forEach(cobol -> deps.add("COBOL:" + cobol.getProgramName()));
            }
        }
        
        return deps;
    }
    
    private void createTechnologySheet(XSSFWorkbook workbook, List<AnalysisResult> results,
                                     CellStyle headerStyle, CellStyle dataStyle) {
        
        XSSFSheet sheet = workbook.createSheet("Technologies Utilisées");
        int rowNum = 0;
        
        // Titre
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.createCell(0).setCellValue("Inventaire des Technologies");
        titleRow.getCell(0).setCellStyle(headerStyle);
        
        rowNum++; // Ligne vide
        
        // Frameworks Web
        Row webHeaderRow = sheet.createRow(rowNum++);
        webHeaderRow.createCell(0).setCellValue("Frameworks Web");
        webHeaderRow.getCell(0).setCellStyle(headerStyle);
        
        Map<String, Long> frameworkCount = results.stream()
                .filter(AnalysisResult::isSuccess)
                .map(AnalysisResult::getApplication)
                .flatMap(app -> app.getFrameworks().stream())
                .collect(Collectors.groupingBy(f -> f, Collectors.counting()));
        
        for (Map.Entry<String, Long> entry : frameworkCount.entrySet()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        
        rowNum++; // Ligne vide
        
        // Types de bases de données
        Row dbHeaderRow = sheet.createRow(rowNum++);
        dbHeaderRow.createCell(0).setCellValue("Bases de Données");
        dbHeaderRow.getCell(0).setCellStyle(headerStyle);
        
        Map<String, Long> dbTypes = results.stream()
                .filter(AnalysisResult::isSuccess)
                .map(AnalysisResult::getApplication)
                .filter(app -> app.getGlobalDependencies() != null && 
                              app.getGlobalDependencies().getDatabases() != null)
                .flatMap(app -> app.getGlobalDependencies().getDatabases().stream())
                .map(Dependencies.DatabaseDependency::getDatabaseType)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(type -> type, Collectors.counting()));
        
        for (Map.Entry<String, Long> entry : dbTypes.entrySet()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        
        // Ajuster les largeurs
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }
    
    private void createDetailedStatisticsSheet(XSSFWorkbook workbook, List<AnalysisResult> results,
                                             CellStyle headerStyle, CellStyle dataStyle,
                                             CellStyle numberStyle) {
        
        XSSFSheet sheet = workbook.createSheet("Statistiques Détaillées");
        int rowNum = 0;
        
        // Titre
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.createCell(0).setCellValue("Statistiques Détaillées de l'Analyse");
        titleRow.getCell(0).setCellStyle(headerStyle);
        
        rowNum++; // Ligne vide
        
        // Métriques globales
        createStatRow(sheet, rowNum++, "Nombre total d'applications", 
                     results.size(), dataStyle, numberStyle);
        
        long successCount = results.stream().filter(AnalysisResult::isSuccess).count();
        createStatRow(sheet, rowNum++, "Taux de succès", 
                     (successCount * 100.0 / results.size()) + "%", dataStyle, dataStyle);
        
        // Temps d'analyse total
        long totalDuration = results.stream()
                .filter(r -> r.getDuration() != null)
                .mapToLong(r -> r.getDuration().getSeconds())
                .sum();
        
        createStatRow(sheet, rowNum++, "Temps d'analyse total (minutes)", 
                     totalDuration / 60, dataStyle, numberStyle);
        
        rowNum++; // Ligne vide
        
        // Top 10 des applications par nombre d'endpoints
        Row topHeaderRow = sheet.createRow(rowNum++);
        topHeaderRow.createCell(0).setCellValue("Top 10 - Applications par nombre d'endpoints");
        topHeaderRow.getCell(0).setCellStyle(headerStyle);
        
        List<AnalysisResult> topApps = results.stream()
                .filter(AnalysisResult::isSuccess)
                .sorted((a, b) -> Integer.compare(b.getEndpointsCount(), a.getEndpointsCount()))
                .limit(10)
                .collect(Collectors.toList());
        
        for (AnalysisResult result : topApps) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(result.getApplication().getName());
            row.createCell(1).setCellValue(result.getEndpointsCount());
            row.getCell(1).setCellStyle(numberStyle);
        }
        
        // Ajuster les largeurs
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }
    
    private void generateApplicationReport(AnalysisResult result, Path perAppDir) 
            throws IOException {
        
        WebLogicApplication app = result.getApplication();
        log.info("Génération du rapport pour l'application: {}", app.getName());
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        CellStyle codeStyle = createCodeStyle(workbook);
        
        // Feuille 1: Résumé
        createAppSummarySheet(workbook, app, headerStyle, titleStyle, dataStyle);
        
        // Feuille 2: Endpoints
        createAppEndpointsSheet(workbook, app, headerStyle, dataStyle);
        
        // Feuille 3: Dépendances
        createAppDependenciesSheet(workbook, app, headerStyle, dataStyle);
        
        // Feuille 4: Pseudo-code (échantillon)
        createAppPseudoCodeSheet(workbook, app, headerStyle, codeStyle);
        
        // Sauvegarder
        String fileName = sanitizeFileName(app.getName()) + "-detailed-report.xlsx";
        Path reportFile = perAppDir.resolve(fileName);
        
        try (FileOutputStream fileOut = new FileOutputStream(reportFile.toFile())) {
            workbook.write(fileOut);
        }
        workbook.close();
    }
    
    private void createAppSummarySheet(XSSFWorkbook workbook, WebLogicApplication app,
                                     CellStyle headerStyle, CellStyle titleStyle,
                                     CellStyle dataStyle) {
        
        XSSFSheet sheet = workbook.createSheet("Résumé");
        int rowNum = 0;
        
        // Titre
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Rapport d'Analyse - " + app.getName());
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        
        rowNum++; // Ligne vide
        
        // Informations générales
        createInfoRow(sheet, rowNum++, "Nom de l'application", app.getName(), dataStyle);
        createInfoRow(sheet, rowNum++, "Type", app.getType() != null ? 
                app.getType().toString() : "", dataStyle);
        createInfoRow(sheet, rowNum++, "Frameworks", app.getFrameworks() != null ? 
                String.join(", ", app.getFrameworks()) : "", dataStyle);
        
        rowNum++; // Ligne vide
        
        // Statistiques
        Row statsHeaderRow = sheet.createRow(rowNum++);
        statsHeaderRow.createCell(0).setCellValue("Statistiques");
        statsHeaderRow.getCell(0).setCellStyle(headerStyle);
        
        if (app.getStatistics() != null) {
            createInfoRow(sheet, rowNum++, "Nombre d'endpoints", 
                         String.valueOf(app.getStatistics().getTotalEndpoints()), dataStyle);
            createInfoRow(sheet, rowNum++, "Nombre de classes", 
                         String.valueOf(app.getStatistics().getTotalClasses()), dataStyle);
            createInfoRow(sheet, rowNum++, "Nombre de méthodes", 
                         String.valueOf(app.getStatistics().getTotalMethods()), dataStyle);
        }
        
        // Ajuster les largeurs
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }
    
    private void createAppEndpointsSheet(XSSFWorkbook workbook, WebLogicApplication app,
                                       CellStyle headerStyle, CellStyle dataStyle) {
        
        XSSFSheet sheet = workbook.createSheet("Endpoints");
        int rowNum = 0;
        
        // En-têtes
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"URL", "Méthodes HTTP", "Classe", "Méthode", 
                          "Paramètres", "Règles métier"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Collecter tous les endpoints
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
        
        // Afficher les endpoints
        for (Endpoint endpoint : allEndpoints) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;
            
            row.createCell(colNum++).setCellValue(endpoint.getUrl());
            row.createCell(colNum++).setCellValue(endpoint.getHttpMethods() != null ?
                    endpoint.getHttpMethods().stream()
                            .map(Enum::toString)
                            .collect(Collectors.joining(", ")) : "");
            row.createCell(colNum++).setCellValue(endpoint.getClassName());
            row.createCell(colNum++).setCellValue(endpoint.getMethodName());
            
            // Paramètres
            String params = "";
            if (endpoint.getParameters() != null) {
                params = endpoint.getParameters().stream()
                        .map(p -> p.getName() + ":" + p.getType())
                        .collect(Collectors.joining(", "));
            }
            row.createCell(colNum++).setCellValue(params);
            
            // Règles métier
            String rules = "";
            if (endpoint.getBusinessRules() != null) {
                rules = endpoint.getBusinessRules().stream()
                        .map(Endpoint.BusinessRule::getDescription)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("; "));
            }
            row.createCell(colNum++).setCellValue(rules);
        }
        
        // Ajuster les largeurs
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void createAppDependenciesSheet(XSSFWorkbook workbook, WebLogicApplication app,
                                          CellStyle headerStyle, CellStyle dataStyle) {
        
        XSSFSheet sheet = workbook.createSheet("Dépendances");
        int rowNum = 0;
        
        if (app.getGlobalDependencies() == null) {
            Row row = sheet.createRow(rowNum);
            row.createCell(0).setCellValue("Aucune dépendance détectée");
            return;
        }
        
        Dependencies deps = app.getGlobalDependencies();
        
        // Bases de données
        if (deps.getDatabases() != null && !deps.getDatabases().isEmpty()) {
            Row dbHeaderRow = sheet.createRow(rowNum++);
            dbHeaderRow.createCell(0).setCellValue("Bases de Données");
            dbHeaderRow.getCell(0).setCellStyle(headerStyle);
            
            for (Dependencies.DatabaseDependency db : deps.getDatabases()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(db.getDataSourceName());
                row.createCell(1).setCellValue(db.getDatabaseType());
                row.createCell(2).setCellValue(db.getTables() != null ? 
                        String.join(", ", db.getTables()) : "");
            }
            
            rowNum++; // Ligne vide
        }
        
        // EJB
        if (deps.getEjbs() != null && !deps.getEjbs().isEmpty()) {
            Row ejbHeaderRow = sheet.createRow(rowNum++);
            ejbHeaderRow.createCell(0).setCellValue("EJB");
            ejbHeaderRow.getCell(0).setCellStyle(headerStyle);
            
            for (Dependencies.EJBDependency ejb : deps.getEjbs()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(ejb.getEjbName());
                row.createCell(1).setCellValue(ejb.getJndiName());
                row.createCell(2).setCellValue(ejb.isLocal() ? "Local" : "Remote");
            }
            
            rowNum++; // Ligne vide
        }
        
        // Cobol
        if (deps.getCobolPrograms() != null && !deps.getCobolPrograms().isEmpty()) {
            Row cobolHeaderRow = sheet.createRow(rowNum++);
            cobolHeaderRow.createCell(0).setCellValue("Programmes Cobol");
            cobolHeaderRow.getCell(0).setCellStyle(headerStyle);
            
            for (Dependencies.CobolDependency cobol : deps.getCobolPrograms()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(cobol.getProgramName());
                row.createCell(1).setCellValue(cobol.getConnectionType());
                row.createCell(2).setCellValue(cobol.getHost() != null ? 
                        cobol.getHost() + ":" + cobol.getPort() : "");
            }
        }
        
        // Ajuster les largeurs
        for (int i = 0; i < 3; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void createAppPseudoCodeSheet(XSSFWorkbook workbook, WebLogicApplication app,
                                        CellStyle headerStyle, CellStyle codeStyle) {
        
        XSSFSheet sheet = workbook.createSheet("Pseudo-code (Échantillon)");
        int rowNum = 0;
        
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("Exemples de Pseudo-code");
        headerRow.getCell(0).setCellStyle(headerStyle);
        
        rowNum++; // Ligne vide
        
        // Afficher quelques exemples de pseudo-code
        List<Endpoint> endpointsWithCode = new ArrayList<>();
        
        if (app.getEndpoints() != null) {
            endpointsWithCode.addAll(app.getEndpoints().stream()
                    .filter(e -> e.getPseudoCode() != null)
                    .limit(5)
                    .collect(Collectors.toList()));
        }
        
        for (Endpoint endpoint : endpointsWithCode) {
            // En-tête de l'endpoint
            Row endpointRow = sheet.createRow(rowNum++);
            endpointRow.createCell(0).setCellValue(
                    endpoint.getUrl() + " - " + endpoint.getMethodName());
            endpointRow.getCell(0).setCellStyle(headerStyle);
            
            rowNum++; // Ligne vide
            
            // Pseudo-code
            if (endpoint.getPseudoCode().getSimplified() != null) {
                String[] lines = endpoint.getPseudoCode().getSimplified().split("\n");
                for (String line : lines) {
                    Row codeRow = sheet.createRow(rowNum++);
                    Cell codeCell = codeRow.createCell(0);
                    codeCell.setCellValue(line);
                    codeCell.setCellStyle(codeStyle);
                }
            }
            
            rowNum += 2; // Deux lignes vides
        }
        
        // Ajuster la largeur
        sheet.setColumnWidth(0, 15000); // Largeur fixe pour le code
    }
    
    // Méthodes utilitaires pour les styles
    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
    
    private CellStyle createDataStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private CellStyle createNumberStyle(XSSFWorkbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
    
    private CellStyle createCodeStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Courier New");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setWrapText(true);
        return style;
    }
    
    // Méthodes utilitaires
    private void createStatRow(Sheet sheet, int rowNum, String label, Object value,
                             CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);
        
        Cell valueCell = row.createCell(1);
        if (value instanceof Number) {
            valueCell.setCellValue(((Number) value).doubleValue());
        } else {
            valueCell.setCellValue(value.toString());
        }
        valueCell.setCellStyle(valueStyle);
    }
    
    private void createInfoRow(Sheet sheet, int rowNum, String label, String value,
                             CellStyle dataStyle) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label + ":");
        row.createCell(1).setCellValue(value);
    }
    
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
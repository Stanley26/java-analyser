package com.legacy.analyzer.extractors.dependencies;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.legacy.analyzer.model.Dependencies;
import com.legacy.analyzer.model.WebLogicApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Component
public class DatabaseDependencyExtractor {
    
    private final JavaParser javaParser = new JavaParser();
    
    // Patterns pour détecter les requêtes SQL
    private static final Pattern SQL_PATTERN = Pattern.compile(
            "\\b(SELECT|INSERT|UPDATE|DELETE|MERGE|CREATE|DROP|ALTER|TRUNCATE|CALL|EXEC|EXECUTE)\\b",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "\\b(?:FROM|INTO|UPDATE|TABLE|JOIN)\\s+([a-zA-Z_][a-zA-Z0-9_]*)(?:\\s|,|$)",
            Pattern.CASE_INSENSITIVE
    );
    
    public List<Dependencies.DatabaseDependency> extractDependencies(Path path, 
                                                                   WebLogicApplication application) 
            throws IOException {
        
        Map<String, Dependencies.DatabaseDependency> dependencyMap = new HashMap<>();
        
        // 1. Extraire les DataSources depuis les descripteurs
        extractDataSourcesFromDescriptors(path, application, dependencyMap);
        
        // 2. Scanner les fichiers Java pour les requêtes SQL
        scanJavaFiles(path, dependencyMap);
        
        // 3. Scanner les fichiers de mapping (Hibernate, MyBatis)
        scanMappingFiles(path, dependencyMap);
        
        // 4. Scanner les fichiers SQL
        scanSqlFiles(path, dependencyMap);
        
        return new ArrayList<>(dependencyMap.values());
    }
    
    private void extractDataSourcesFromDescriptors(Path path, WebLogicApplication application,
                                                 Map<String, Dependencies.DatabaseDependency> dependencyMap) {
        
        // Extraire depuis les DataSources WebLogic configurées
        if (application.getDataSources() != null) {
            for (WebLogicApplication.DataSource ds : application.getDataSources()) {
                Dependencies.DatabaseDependency dep = Dependencies.DatabaseDependency.builder()
                        .dataSourceName(ds.getName())
                        .connectionName(ds.getJndiName())
                        .databaseType(detectDatabaseType(ds.getDriverClass(), ds.getUrl()))
                        .build();
                
                dependencyMap.put(ds.getName(), dep);
            }
        }
        
        // TODO: Parser persistence.xml, hibernate.cfg.xml, etc.
    }
    
    private String detectDatabaseType(String driverClass, String url) {
        if (driverClass != null) {
            if (driverClass.contains("oracle")) return "ORACLE";
            if (driverClass.contains("db2")) return "DB2";
            if (driverClass.contains("sqlserver") || driverClass.contains("microsoft")) return "SQL_SERVER";
            if (driverClass.contains("mysql")) return "MYSQL";
            if (driverClass.contains("postgresql")) return "POSTGRESQL";
        }
        
        if (url != null) {
            if (url.contains("oracle")) return "ORACLE";
            if (url.contains("db2")) return "DB2";
            if (url.contains("sqlserver")) return "SQL_SERVER";
            if (url.contains("mysql")) return "MYSQL";
            if (url.contains("postgresql")) return "POSTGRESQL";
        }
        
        return "UNKNOWN";
    }
    
    private void scanJavaFiles(Path path, Map<String, Dependencies.DatabaseDependency> dependencyMap) 
            throws IOException {
        
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                         extractFromJavaFile(javaFile, dependencyMap);
                     } catch (Exception e) {
                         log.error("Erreur lors de l'analyse du fichier: {}", javaFile, e);
                     }
                 });
        }
    }
    
    private void extractFromJavaFile(Path javaFile, 
                                   Map<String, Dependencies.DatabaseDependency> dependencyMap) 
            throws IOException {
        
        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
        if (!parseResult.isSuccessful()) {
            return;
        }
        
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return;
        
        // Créer une dépendance par défaut si nécessaire
        String defaultDsName = "default";
        Dependencies.DatabaseDependency defaultDep = dependencyMap.computeIfAbsent(
                defaultDsName, 
                k -> Dependencies.DatabaseDependency.builder()
                        .dataSourceName(defaultDsName)
                        .queries(new ArrayList<>())
                        .tables(new HashSet<>())
                        .storedProcedures(new HashSet<>())
                        .build()
        );
        
        // Visitor pour extraire les requêtes SQL
        cu.accept(new SqlExtractorVisitor(defaultDep, javaFile), null);
    }
    
    private class SqlExtractorVisitor extends VoidVisitorAdapter<Void> {
        private final Dependencies.DatabaseDependency dependency;
        private final Path sourceFile;
        private int queryCounter = 0;
        
        public SqlExtractorVisitor(Dependencies.DatabaseDependency dependency, Path sourceFile) {
            this.dependency = dependency;
            this.sourceFile = sourceFile;
            
            if (dependency.getQueries() == null) {
                dependency.setQueries(new ArrayList<>());
            }
            if (dependency.getTables() == null) {
                dependency.setTables(new ArrayList<>());
            }
            if (dependency.getStoredProcedures() == null) {
                dependency.setStoredProcedures(new ArrayList<>());
            }
        }
        
        @Override
        public void visit(StringLiteralExpr expr, Void arg) {
            String value = expr.getValue();
            
            // Vérifier si c'est une requête SQL
            if (SQL_PATTERN.matcher(value).find()) {
                queryCounter++;
                
                Dependencies.SQLQuery query = Dependencies.SQLQuery.builder()
                        .id("query_" + sourceFile.getFileName() + "_" + queryCounter)
                        .rawQuery(value)
                        .normalizedQuery(normalizeQuery(value))
                        .type(detectQueryType(value))
                        .tables(extractTables(value))
                        .lineNumber(expr.getBegin().map(pos -> pos.line).orElse(0))
                        .isDynamic(false)
                        .build();
                
                dependency.getQueries().add(query);
                
                // Ajouter les tables à la liste globale
                dependency.getTables().addAll(query.getTables());
                
                // Détecter les procédures stockées
                if (query.getType().equals("CALL")) {
                    String procName = extractStoredProcedureName(value);
                    if (procName != null) {
                        dependency.getStoredProcedures().add(procName);
                    }
                }
            }
            
            super.visit(expr, arg);
        }
        
        @Override
        public void visit(BinaryExpr expr, Void arg) {
            // Détecter les concaténations de strings qui pourraient former une requête SQL
            if (expr.getOperator() == BinaryExpr.Operator.PLUS) {
                String combined = extractConcatenatedString(expr);
                if (combined != null && SQL_PATTERN.matcher(combined).find()) {
                    queryCounter++;
                    
                    Dependencies.SQLQuery query = Dependencies.SQLQuery.builder()
                            .id("query_dynamic_" + sourceFile.getFileName() + "_" + queryCounter)
                            .rawQuery(combined)
                            .normalizedQuery(normalizeQuery(combined))
                            .type(detectQueryType(combined))
                            .tables(extractTables(combined))
                            .lineNumber(expr.getBegin().map(pos -> pos.line).orElse(0))
                            .isDynamic(true)
                            .build();
                    
                    dependency.getQueries().add(query);
                    dependency.getTables().addAll(query.getTables());
                }
            }
            
            super.visit(expr, arg);
        }
        
        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            // Détecter les appels PreparedStatement, CallableStatement
            if (methodName.equals("prepareStatement") || methodName.equals("prepareCall")) {
                if (!expr.getArguments().isEmpty()) {
                    Expression sqlExpr = expr.getArguments().get(0);
                    if (sqlExpr instanceof StringLiteralExpr) {
                        String sql = ((StringLiteralExpr) sqlExpr).getValue();
                        visit((StringLiteralExpr) sqlExpr, null);
                    }
                }
            }
            
            // Détecter les appels Hibernate/JPA
            if (methodName.equals("createQuery") || methodName.equals("createNativeQuery") ||
                methodName.equals("createSQLQuery") || methodName.equals("createNamedQuery")) {
                if (!expr.getArguments().isEmpty()) {
                    Expression queryExpr = expr.getArguments().get(0);
                    if (queryExpr instanceof StringLiteralExpr) {
                        String query = ((StringLiteralExpr) queryExpr).getValue();
                        
                        // Pour les requêtes HQL/JPQL, extraire les entités
                        if (methodName.equals("createQuery")) {
                            extractEntitiesFromHQL(query);
                        } else {
                            visit((StringLiteralExpr) queryExpr, null);
                        }
                    }
                }
            }
            
            super.visit(expr, arg);
        }
        
        private void extractEntitiesFromHQL(String hql) {
            // Extraction simplifiée des entités depuis HQL
            Pattern entityPattern = Pattern.compile("FROM\\s+([a-zA-Z][a-zA-Z0-9]*)", 
                                                   Pattern.CASE_INSENSITIVE);
            Matcher matcher = entityPattern.matcher(hql);
            
            while (matcher.find()) {
                String entity = matcher.group(1);
                dependency.getTables().add(entity.toLowerCase());
            }
        }
    }
    
    private String normalizeQuery(String query) {
        // Normaliser la requête pour faciliter l'analyse
        return query.replaceAll("\\s+", " ")
                   .replaceAll("\\?", "?")
                   .trim();
    }
    
    private String detectQueryType(String query) {
        String upperQuery = query.toUpperCase().trim();
        
        if (upperQuery.startsWith("SELECT")) return "SELECT";
        if (upperQuery.startsWith("INSERT")) return "INSERT";
        if (upperQuery.startsWith("UPDATE")) return "UPDATE";
        if (upperQuery.startsWith("DELETE")) return "DELETE";
        if (upperQuery.startsWith("MERGE")) return "MERGE";
        if (upperQuery.startsWith("CALL") || upperQuery.startsWith("EXEC")) return "CALL";
        if (upperQuery.startsWith("CREATE")) return "DDL";
        if (upperQuery.startsWith("ALTER")) return "DDL";
        if (upperQuery.startsWith("DROP")) return "DDL";
        
        return "UNKNOWN";
    }
    
    private List<String> extractTables(String query) {
        Set<String> tables = new HashSet<>();
        
        Matcher matcher = TABLE_PATTERN.matcher(query);
        while (matcher.find()) {
            String table = matcher.group(1);
            if (table != null && !isKeyword(table)) {
                tables.add(table.toLowerCase());
            }
        }
        
        return new ArrayList<>(tables);
    }
    
    private boolean isKeyword(String word) {
        Set<String> keywords = Set.of("SELECT", "FROM", "WHERE", "AND", "OR", 
                                      "ORDER", "GROUP", "BY", "HAVING", "JOIN",
                                      "LEFT", "RIGHT", "INNER", "OUTER", "ON");
        return keywords.contains(word.toUpperCase());
    }
    
    private String extractStoredProcedureName(String query) {
        Pattern pattern = Pattern.compile("(?:CALL|EXEC|EXECUTE)\\s+([a-zA-Z_][a-zA-Z0-9_\\.]*)",
                                        Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    private String extractConcatenatedString(BinaryExpr expr) {
        try {
            StringBuilder result = new StringBuilder();
            extractStringFromExpression(expr, result);
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    private void extractStringFromExpression(Expression expr, StringBuilder result) {
        if (expr instanceof StringLiteralExpr) {
            result.append(((StringLiteralExpr) expr).getValue());
        } else if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            if (binary.getOperator() == BinaryExpr.Operator.PLUS) {
                extractStringFromExpression(binary.getLeft(), result);
                extractStringFromExpression(binary.getRight(), result);
            }
        }
    }
    
    private void scanMappingFiles(Path path, Map<String, Dependencies.DatabaseDependency> dependencyMap) 
            throws IOException {
        
        // Scanner les fichiers Hibernate
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith(".hbm.xml") || 
                             p.toString().endsWith("hibernate.cfg.xml"))
                 .forEach(mappingFile -> {
                     try {
                         extractFromHibernateMapping(mappingFile, dependencyMap);
                     } catch (Exception e) {
                         log.error("Erreur lors de l'analyse du fichier Hibernate: {}", mappingFile, e);
                     }
                 });
        }
        
        // Scanner les fichiers MyBatis
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith("-mapper.xml") || 
                             p.toString().endsWith("Mapper.xml"))
                 .forEach(mappingFile -> {
                     try {
                         extractFromMyBatisMapping(mappingFile, dependencyMap);
                     } catch (Exception e) {
                         log.error("Erreur lors de l'analyse du fichier MyBatis: {}", mappingFile, e);
                     }
                 });
        }
    }
    
    private void extractFromHibernateMapping(Path mappingFile, 
                                           Map<String, Dependencies.DatabaseDependency> dependencyMap) {
        // TODO: Implémenter le parsing des fichiers Hibernate
        log.debug("Analyse du fichier Hibernate: {}", mappingFile);
    }
    
    private void extractFromMyBatisMapping(Path mappingFile, 
                                         Map<String, Dependencies.DatabaseDependency> dependencyMap) {
        // TODO: Implémenter le parsing des fichiers MyBatis
        log.debug("Analyse du fichier MyBatis: {}", mappingFile);
    }
    
    private void scanSqlFiles(Path path, Map<String, Dependencies.DatabaseDependency> dependencyMap) 
            throws IOException {
        
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith(".sql"))
                 .forEach(sqlFile -> {
                     try {
                         extractFromSqlFile(sqlFile, dependencyMap);
                     } catch (Exception e) {
                         log.error("Erreur lors de l'analyse du fichier SQL: {}", sqlFile, e);
                     }
                 });
        }
    }
    
    private void extractFromSqlFile(Path sqlFile, 
                                  Map<String, Dependencies.DatabaseDependency> dependencyMap) 
            throws IOException {
        
        String content = Files.readString(sqlFile);
        
        // Créer une dépendance pour les scripts SQL
        Dependencies.DatabaseDependency sqlDep = dependencyMap.computeIfAbsent(
                "sql_scripts",
                k -> Dependencies.DatabaseDependency.builder()
                        .dataSourceName("sql_scripts")
                        .queries(new ArrayList<>())
                        .tables(new HashSet<>())
                        .storedProcedures(new HashSet<>())
                        .build()
        );
        
        // Parser le contenu SQL
        String[] statements = content.split(";");
        for (int i = 0; i < statements.length; i++) {
            String statement = statements[i].trim();
            if (!statement.isEmpty() && SQL_PATTERN.matcher(statement).find()) {
                Dependencies.SQLQuery query = Dependencies.SQLQuery.builder()
                        .id("sql_" + sqlFile.getFileName() + "_" + i)
                        .rawQuery(statement)
                        .normalizedQuery(normalizeQuery(statement))
                        .type(detectQueryType(statement))
                        .tables(extractTables(statement))
                        .isDynamic(false)
                        .build();
                
                sqlDep.getQueries().add(query);
                sqlDep.getTables().addAll(query.getTables());
            }
        }
    }
}
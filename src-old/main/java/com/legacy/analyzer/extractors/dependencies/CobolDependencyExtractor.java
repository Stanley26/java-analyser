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
public class CobolDependencyExtractor {
    
    private final JavaParser javaParser = new JavaParser();
    
    // Patterns pour détecter les connexions Cobol
    private static final Pattern SOCKET_PATTERN = Pattern.compile(
            "new\\s+Socket\\s*\\([^)]*\\)",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern JNI_PATTERN = Pattern.compile(
            "System\\.loadLibrary\\s*\\([^)]*\\)|native\\s+",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern MQ_PATTERN = Pattern.compile(
            "MQQueueManager|MQQueue|MQMessage",
            Pattern.CASE_INSENSITIVE
    );
    
    public List<Dependencies.CobolDependency> extractDependencies(Path path,
                                                                WebLogicApplication application)
            throws IOException {
        
        Map<String, Dependencies.CobolDependency> dependencyMap = new HashMap<>();
        
        // 1. Scanner les fichiers Java pour les connexions Cobol
        scanJavaFiles(path, dependencyMap);
        
        // 2. Scanner les fichiers de configuration
        scanConfigurationFiles(path, dependencyMap);
        
        // 3. Scanner les fichiers de propriétés
        scanPropertyFiles(path, dependencyMap);
        
        return new ArrayList<>(dependencyMap.values());
    }
    
    private void scanJavaFiles(Path path, Map<String, Dependencies.CobolDependency> dependencyMap)
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
                                   Map<String, Dependencies.CobolDependency> dependencyMap)
            throws IOException {
        
        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
        if (!parseResult.isSuccessful()) {
            return;
        }
        
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return;
        
        // Visitor pour extraire les connexions Cobol
        cu.accept(new CobolConnectionVisitor(dependencyMap, javaFile), null);
    }
    
    private class CobolConnectionVisitor extends VoidVisitorAdapter<Void> {
        private final Map<String, Dependencies.CobolDependency> dependencyMap;
        private final Path sourceFile;
        
        public CobolConnectionVisitor(Map<String, Dependencies.CobolDependency> dependencyMap,
                                    Path sourceFile) {
            this.dependencyMap = dependencyMap;
            this.sourceFile = sourceFile;
        }
        
        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            String className = expr.getTypeAsString();
            
            // Détecter les connexions Socket
            if (className.equals("Socket") || className.endsWith(".Socket")) {
                extractSocketConnection(expr);
            }
            
            // Détecter les connexions MQ
            if (className.contains("MQQueueManager") || className.contains("MQQueue")) {
                extractMQConnection(expr);
            }
            
            // Détecter les adaptateurs Cobol personnalisés
            if (className.contains("Cobol") || className.contains("Mainframe") ||
                className.contains("CICS") || className.contains("IMS")) {
                extractCustomCobolAdapter(expr);
            }
            
            super.visit(expr, arg);
        }
        
        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            // Détecter les appels JNI
            if (methodName.equals("loadLibrary") && 
                expr.getScope().isPresent() &&
                expr.getScope().get().toString().equals("System")) {
                extractJNICall(expr);
            }
            
            // Détecter les appels de transfert de fichiers
            if (methodName.contains("transfer") || methodName.contains("send") ||
                methodName.contains("receive") || methodName.contains("ftp")) {
                extractFileTransfer(expr);
            }
            
            // Détecter les appels CICS/IMS
            if (methodName.startsWith("EXEC") || methodName.contains("CICS") ||
                methodName.contains("IMS")) {
                extractCICSIMSCall(expr);
            }
            
            super.visit(expr, arg);
        }
        
        @Override
        public void visit(FieldDeclaration field, Void arg) {
            // Détecter les méthodes natives (JNI)
            if (field.toString().contains("native")) {
                extractNativeMethod(field);
            }
            
            super.visit(field, arg);
        }
        
        private void extractSocketConnection(ObjectCreationExpr expr) {
            Dependencies.CobolDependency.CobolDependencyBuilder builder = 
                    Dependencies.CobolDependency.builder();
            
            builder.connectionType("SOCKET");
            
            // Extraire host et port
            if (expr.getArguments().size() >= 2) {
                Expression hostExpr = expr.getArgument(0);
                Expression portExpr = expr.getArgument(1);
                
                if (hostExpr instanceof StringLiteralExpr) {
                    builder.host(((StringLiteralExpr) hostExpr).getValue());
                }
                
                if (portExpr instanceof IntegerLiteralExpr) {
                    builder.port(((IntegerLiteralExpr) portExpr).asInt());
                }
            }
            
            // Générer un nom pour la connexion
            String connectionName = "COBOL_SOCKET_" + 
                    (builder.build().getHost() != null ? builder.build().getHost() : "unknown");
            builder.programName(connectionName);
            
            Map<String, String> details = new HashMap<>();
            details.put("sourceFile", sourceFile.toString());
            details.put("lineNumber", String.valueOf(expr.getBegin().map(p -> p.line).orElse(0)));
            builder.connectionDetails(details);
            
            dependencyMap.put(connectionName, builder.build());
        }
        
        private void extractMQConnection(ObjectCreationExpr expr) {
            Dependencies.CobolDependency.CobolDependencyBuilder builder = 
                    Dependencies.CobolDependency.builder();
            
            builder.connectionType("MQ");
            builder.protocol("IBM MQ");
            
            // Extraire les paramètres MQ
            String queueManager = extractMQParameter(expr, "queueManager");
            String channel = extractMQParameter(expr, "channel");
            String queueName = extractMQParameter(expr, "queue");
            
            Map<String, String> details = new HashMap<>();
            if (queueManager != null) details.put("queueManager", queueManager);
            if (channel != null) details.put("channel", channel);
            if (queueName != null) details.put("queue", queueName);
            details.put("sourceFile", sourceFile.toString());
            
            builder.connectionDetails(details);
            
            String programName = "COBOL_MQ_" + (queueManager != null ? queueManager : "unknown");
            builder.programName(programName);
            
            dependencyMap.put(programName, builder.build());
        }
        
        private void extractJNICall(MethodCallExpr expr) {
            if (!expr.getArguments().isEmpty() && 
                expr.getArgument(0) instanceof StringLiteralExpr) {
                
                String libraryName = ((StringLiteralExpr) expr.getArgument(0)).getValue();
                
                Dependencies.CobolDependency dependency = Dependencies.CobolDependency.builder()
                        .programName(libraryName)
                        .connectionType("JNI")
                        .protocol("Native Library")
                        .connectionDetails(Map.of(
                                "libraryName", libraryName,
                                "sourceFile", sourceFile.toString(),
                                "lineNumber", String.valueOf(expr.getBegin().map(p -> p.line).orElse(0))
                        ))
                        .build();
                
                dependencyMap.put(libraryName, dependency);
            }
        }
        
        private void extractFileTransfer(MethodCallExpr expr) {
            // Analyser le contexte pour déterminer si c'est un transfert Cobol
            String methodName = expr.getNameAsString();
            
            if (isCobolFileTransfer(expr)) {
                String fileName = extractFileName(expr);
                
                Dependencies.CobolDependency dependency = Dependencies.CobolDependency.builder()
                        .programName("COBOL_FILE_" + (fileName != null ? fileName : "unknown"))
                        .connectionType("FILE")
                        .protocol("File Transfer")
                        .connectionDetails(Map.of(
                                "method", methodName,
                                "fileName", fileName != null ? fileName : "unknown",
                                "sourceFile", sourceFile.toString()
                        ))
                        .build();
                
                dependencyMap.put(dependency.getProgramName(), dependency);
            }
        }
        
        private void extractCICSIMSCall(MethodCallExpr expr) {
            String methodName = expr.getNameAsString();
            String programType = methodName.contains("CICS") ? "CICS" : "IMS";
            
            Dependencies.CobolDependency dependency = Dependencies.CobolDependency.builder()
                    .programName(programType + "_CALL_" + methodName)
                    .connectionType(programType)
                    .protocol(programType + " Transaction")
                    .connectionDetails(Map.of(
                            "method", methodName,
                            "sourceFile", sourceFile.toString(),
                            "lineNumber", String.valueOf(expr.getBegin().map(p -> p.line).orElse(0))
                    ))
                    .build();
            
            // Extraire les paramètres si possible
            List<String> parameters = new ArrayList<>();
            expr.getArguments().forEach(arg -> {
                if (arg instanceof StringLiteralExpr) {
                    parameters.add(((StringLiteralExpr) arg).getValue());
                }
            });
            
            if (!parameters.isEmpty()) {
                dependency.setInputParameters(parameters);
            }
            
            dependencyMap.put(dependency.getProgramName(), dependency);
        }
        
        private void extractCustomCobolAdapter(ObjectCreationExpr expr) {
            String className = expr.getTypeAsString();
            
            Dependencies.CobolDependency.CobolDependencyBuilder builder = 
                    Dependencies.CobolDependency.builder();
            
            builder.programName("COBOL_ADAPTER_" + className);
            builder.connectionType("CUSTOM");
            builder.protocol(className);
            
            Map<String, String> details = new HashMap<>();
            details.put("adapterClass", className);
            details.put("sourceFile", sourceFile.toString());
            
            // Extraire les paramètres du constructeur
            List<String> constructorParams = new ArrayList<>();
            expr.getArguments().forEach(arg -> {
                if (arg instanceof StringLiteralExpr) {
                    constructorParams.add(((StringLiteralExpr) arg).getValue());
                }
            });
            
            if (!constructorParams.isEmpty()) {
                details.put("constructorParams", String.join(",", constructorParams));
            }
            
            builder.connectionDetails(details);
            
            dependencyMap.put(builder.build().getProgramName(), builder.build());
        }
        
        private void extractNativeMethod(FieldDeclaration field) {
            String fieldName = field.getVariable(0).getNameAsString();
            
            Dependencies.CobolDependency dependency = Dependencies.CobolDependency.builder()
                    .programName("NATIVE_" + fieldName)
                    .connectionType("JNI")
                    .protocol("Native Method")
                    .connectionDetails(Map.of(
                            "methodName", fieldName,
                            "sourceFile", sourceFile.toString()
                    ))
                    .build();
            
            dependencyMap.put(dependency.getProgramName(), dependency);
        }
        
        private String extractMQParameter(ObjectCreationExpr expr, String paramName) {
            // Simplification - en réalité, il faudrait analyser plus en profondeur
            return null;
        }
        
        private boolean isCobolFileTransfer(MethodCallExpr expr) {
            // Heuristique pour déterminer si c'est un transfert vers/depuis Cobol
            String scope = expr.getScope().map(Object::toString).orElse("");
            return scope.contains("Cobol") || scope.contains("Mainframe") ||
                   scope.contains("FTP") || scope.contains("Transfer");
        }
        
        private String extractFileName(MethodCallExpr expr) {
            // Extraire le nom de fichier des arguments si possible
            for (Expression arg : expr.getArguments()) {
                if (arg instanceof StringLiteralExpr) {
                    String value = ((StringLiteralExpr) arg).getValue();
                    if (value.contains(".") || value.contains("/") || value.contains("\\")) {
                        return value;
                    }
                }
            }
            return null;
        }
    }
    
    private void scanConfigurationFiles(Path path, 
                                      Map<String, Dependencies.CobolDependency> dependencyMap) 
            throws IOException {
        
        // Scanner les fichiers XML de configuration
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith(".xml") && 
                             (p.toString().contains("cobol") || 
                              p.toString().contains("mainframe") ||
                              p.toString().contains("mq")))
                 .forEach(configFile -> {
                     try {
                         extractFromConfigFile(configFile, dependencyMap);
                     } catch (Exception e) {
                         log.error("Erreur lors de l'analyse du fichier de config: {}", configFile, e);
                     }
                 });
        }
    }
    
    private void extractFromConfigFile(Path configFile, 
                                     Map<String, Dependencies.CobolDependency> dependencyMap) 
            throws IOException {
        
        String content = Files.readString(configFile);
        
        // Rechercher les configurations de connexion
        Pattern hostPattern = Pattern.compile("<host>([^<]+)</host>");
        Pattern portPattern = Pattern.compile("<port>([^<]+)</port>");
        Pattern programPattern = Pattern.compile("<program>([^<]+)</program>");
        
        Matcher hostMatcher = hostPattern.matcher(content);
        Matcher portMatcher = portPattern.matcher(content);
        Matcher programMatcher = programPattern.matcher(content);
        
        if (hostMatcher.find() || programMatcher.find()) {
            Dependencies.CobolDependency.CobolDependencyBuilder builder = 
                    Dependencies.CobolDependency.builder();
            
            if (hostMatcher.find()) {
                builder.host(hostMatcher.group(1));
            }
            
            if (portMatcher.find()) {
                try {
                    builder.port(Integer.parseInt(portMatcher.group(1)));
                } catch (NumberFormatException e) {
                    // Ignorer
                }
            }
            
            if (programMatcher.find()) {
                builder.programName(programMatcher.group(1));
            } else {
                builder.programName("COBOL_CONFIG_" + configFile.getFileName());
            }
            
            builder.connectionType("CONFIG");
            builder.connectionDetails(Map.of("configFile", configFile.toString()));
            
            Dependencies.CobolDependency dependency = builder.build();
            dependencyMap.put(dependency.getProgramName(), dependency);
        }
    }
    
    private void scanPropertyFiles(Path path, 
                                 Map<String, Dependencies.CobolDependency> dependencyMap) 
            throws IOException {
        
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith(".properties"))
                 .forEach(propFile -> {
                     try {
                         extractFromPropertyFile(propFile, dependencyMap);
                     } catch (Exception e) {
                         log.error("Erreur lors de l'analyse du fichier properties: {}", propFile, e);
                     }
                 });
        }
    }
    
    private void extractFromPropertyFile(Path propFile, 
                                       Map<String, Dependencies.CobolDependency> dependencyMap) 
            throws IOException {
        
        Properties props = new Properties();
        props.load(Files.newInputStream(propFile));
        
        // Rechercher les propriétés liées à Cobol
        props.forEach((key, value) -> {
            String keyStr = key.toString().toLowerCase();
            String valueStr = value.toString();
            
            if (keyStr.contains("cobol") || keyStr.contains("mainframe") ||
                keyStr.contains("cics") || keyStr.contains("ims") ||
                keyStr.contains("mq")) {
                
                Dependencies.CobolDependency.CobolDependencyBuilder builder = 
                        Dependencies.CobolDependency.builder();
                
                builder.programName("COBOL_PROP_" + key);
                builder.connectionType("PROPERTY");
                
                Map<String, String> details = new HashMap<>();
                details.put("property", keyStr);
                details.put("value", valueStr);
                details.put("file", propFile.toString());
                
                // Essayer d'extraire host/port si présent
                if (keyStr.contains("host")) {
                    builder.host(valueStr);
                } else if (keyStr.contains("port")) {
                    try {
                        builder.port(Integer.parseInt(valueStr));
                    } catch (NumberFormatException e) {
                        // Ignorer
                    }
                }
                
                builder.connectionDetails(details);
                
                Dependencies.CobolDependency dependency = builder.build();
                dependencyMap.put(dependency.getProgramName(), dependency);
            }
        });
    }
}
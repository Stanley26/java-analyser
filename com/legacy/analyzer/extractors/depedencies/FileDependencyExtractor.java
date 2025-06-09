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
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Component
public class FileDependencyExtractor {
    
    private final JavaParser javaParser = new JavaParser();
    
    // Patterns pour détecter les chemins de fichiers
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "([a-zA-Z]:)?[\\\\/]([\\w.-]+[\\\\/])*[\\w.-]+\\.[a-zA-Z]{2,4}",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern UNC_PATH_PATTERN = Pattern.compile(
            "\\\\\\\\[\\w.-]+\\\\[\\w.-]+.*",
            Pattern.CASE_INSENSITIVE
    );
    
    public List<Dependencies.FileDependency> extractDependencies(Path path,
                                                               WebLogicApplication application)
            throws IOException {
        
        Map<String, Dependencies.FileDependency> dependencyMap = new HashMap<>();
        
        // 1. Scanner les fichiers Java
        scanJavaFiles(path, dependencyMap);
        
        // 2. Scanner les fichiers de propriétés
        scanPropertyFiles(path, dependencyMap);
        
        // 3. Scanner les fichiers de configuration
        scanConfigFiles(path, dependencyMap);
        
        return new ArrayList<>(dependencyMap.values());
    }
    
    private void scanJavaFiles(Path path, Map<String, Dependencies.FileDependency> dependencyMap)
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
                                   Map<String, Dependencies.FileDependency> dependencyMap)
            throws IOException {
        
        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
        if (!parseResult.isSuccessful()) {
            return;
        }
        
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return;
        
        // Visitor pour extraire les opérations sur fichiers
        cu.accept(new FileOperationVisitor(dependencyMap, javaFile), null);
    }
    
    private class FileOperationVisitor extends VoidVisitorAdapter<Void> {
        private final Map<String, Dependencies.FileDependency> dependencyMap;
        private final Path sourceFile;
        
        public FileOperationVisitor(Map<String, Dependencies.FileDependency> dependencyMap,
                                   Path sourceFile) {
            this.dependencyMap = dependencyMap;
            this.sourceFile = sourceFile;
        }
        
        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            String className = expr.getTypeAsString();
            
            // Classes d'I/O fichier
            if (isFileIOClass(className)) {
                extractFileOperation(expr, className);
            }
            
            super.visit(expr, arg);
        }
        
        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            // Méthodes d'I/O fichier
            if (isFileIOMethod(methodName)) {
                extractFileMethodCall(expr);
            }
            
            // Files.* methods (Java NIO)
            if (expr.getScope().isPresent() && 
                expr.getScope().get().toString().equals("Files")) {
                extractNIOFileOperation(expr);
            }
            
            // FileUtils.* (Apache Commons IO)
            if (expr.getScope().isPresent() && 
                expr.getScope().get().toString().contains("FileUtils")) {
                extractCommonsIOOperation(expr);
            }
            
            super.visit(expr, arg);
        }
        
        @Override
        public void visit(StringLiteralExpr expr, Void arg) {
            String value = expr.getValue();
            
            // Détecter les chemins de fichiers
            if (FILE_PATH_PATTERN.matcher(value).matches() || 
                UNC_PATH_PATTERN.matcher(value).matches() ||
                looksLikeFilePath(value)) {
                
                Dependencies.FileDependency file = Dependencies.FileDependency.builder()
                        .filePath(value)
                        .fileType(detectFileType(value))
                        .format(detectFileFormat(value))
                        .accessType("UNKNOWN")
                        .isShared(isSharedPath(value))
                        .build();
                
                dependencyMap.put(value, file);
            }
            
            super.visit(expr, arg);
        }
        
        private boolean isFileIOClass(String className) {
            return className.equals("File") || 
                   className.equals("FileInputStream") ||
                   className.equals("FileOutputStream") ||
                   className.equals("FileReader") ||
                   className.equals("FileWriter") ||
                   className.equals("RandomAccessFile") ||
                   className.equals("BufferedReader") ||
                   className.equals("BufferedWriter") ||
                   className.equals("PrintWriter") ||
                   className.contains("Stream") && className.contains("File");
        }
        
        private boolean isFileIOMethod(String methodName) {
            return methodName.equals("createNewFile") ||
                   methodName.equals("delete") ||
                   methodName.equals("renameTo") ||
                   methodName.equals("mkdir") ||
                   methodName.equals("mkdirs") ||
                   methodName.equals("exists") ||
                   methodName.equals("canRead") ||
                   methodName.equals("canWrite") ||
                   methodName.equals("length") ||
                   methodName.equals("lastModified");
        }
        
        private void extractFileOperation(ObjectCreationExpr expr, String className) {
            Dependencies.FileDependency.FileDependencyBuilder builder = 
                    Dependencies.FileDependency.builder();
            
            // Déterminer le type d'accès
            String accessType = "READ_WRITE";
            if (className.contains("InputStream") || className.contains("Reader")) {
                accessType = "READ";
            } else if (className.contains("OutputStream") || className.contains("Writer")) {
                accessType = "WRITE";
            }
            
            builder.accessType(accessType);
            
            // Extraire le chemin du fichier
            if (!expr.getArguments().isEmpty()) {
                Expression fileArg = expr.getArgument(0);
                String filePath = extractFilePath(fileArg);
                
                if (filePath != null) {
                    builder.filePath(filePath);
                    builder.fileType(detectFileType(filePath));
                    builder.format(detectFileFormat(filePath));
                    builder.isShared(isSharedPath(filePath));
                    
                    // Détecter l'encoding si spécifié
                    if (expr.getArguments().size() > 1 && 
                        expr.getArgument(1) instanceof StringLiteralExpr) {
                        builder.encoding(((StringLiteralExpr) expr.getArgument(1)).getValue());
                    }
                    
                    Dependencies.FileDependency file = builder.build();
                    dependencyMap.put(filePath, file);
                }
            }
        }
        
        private void extractFileMethodCall(MethodCallExpr expr) {
            if (expr.getScope().isPresent()) {
                String scope = expr.getScope().get().toString();
                
                // Si c'est un appel sur un objet File
                if (scope.contains("file") || scope.contains("File")) {
                    String methodName = expr.getNameAsString();
                    String accessType = determineAccessTypeFromMethod(methodName);
                    
                    Dependencies.FileDependency file = Dependencies.FileDependency.builder()
                            .filePath("FILE_" + scope)
                            .accessType(accessType)
                            .build();
                    
                    dependencyMap.put(file.getFilePath(), file);
                }
            }
        }
        
        private void extractNIOFileOperation(MethodCallExpr expr) {
            String methodName = expr.getNameAsString();
            String accessType = determineNIOAccessType(methodName);
            
            if (!expr.getArguments().isEmpty()) {
                String filePath = extractFilePath(expr.getArgument(0));
                
                if (filePath != null) {
                    Dependencies.FileDependency file = Dependencies.FileDependency.builder()
                            .filePath(filePath)
                            .fileType(detectFileType(filePath))
                            .format(detectFileFormat(filePath))
                            .accessType(accessType)
                            .isShared(isSharedPath(filePath))
                            .build();
                    
                    dependencyMap.put(filePath, file);
                }
            }
        }
        
        private void extractCommonsIOOperation(MethodCallExpr expr) {
            String methodName = expr.getNameAsString();
            String accessType = "READ_WRITE";
            
            if (methodName.startsWith("read")) {
                accessType = "READ";
            } else if (methodName.startsWith("write")) {
                accessType = "WRITE";
            }
            
            // Extraire les fichiers des arguments
            expr.getArguments().forEach(arg -> {
                String filePath = extractFilePath(arg);
                if (filePath != null) {
                    Dependencies.FileDependency file = Dependencies.FileDependency.builder()
                            .filePath(filePath)
                            .fileType(detectFileType(filePath))
                            .format(detectFileFormat(filePath))
                            .accessType(accessType)
                            .isShared(isSharedPath(filePath))
                            .build();
                    
                    dependencyMap.put(filePath, file);
                }
            });
        }
        
        private String extractFilePath(Expression expr) {
            if (expr instanceof StringLiteralExpr) {
                return ((StringLiteralExpr) expr).getValue();
            } else if (expr instanceof ObjectCreationExpr) {
                ObjectCreationExpr objExpr = (ObjectCreationExpr) expr;
                if (objExpr.getTypeAsString().equals("File") && !objExpr.getArguments().isEmpty()) {
                    return extractFilePath(objExpr.getArgument(0));
                }
            } else if (expr instanceof MethodCallExpr) {
                MethodCallExpr methodExpr = (MethodCallExpr) expr;
                if (methodExpr.getNameAsString().equals("get") || 
                    methodExpr.getNameAsString().equals("toFile")) {
                    return "DYNAMIC_PATH";
                }
            }
            
            return null;
        }
        
        private boolean looksLikeFilePath(String value) {
            return value.contains("/") || value.contains("\\") ||
                   value.endsWith(".txt") || value.endsWith(".csv") ||
                   value.endsWith(".xml") || value.endsWith(".json") ||
                   value.endsWith(".dat") || value.endsWith(".log") ||
                   value.endsWith(".properties") || value.endsWith(".config");
        }
        
        private String detectFileType(String filePath) {
            if (filePath == null) return "UNKNOWN";
            
            String lowerPath = filePath.toLowerCase();
            
            if (lowerPath.endsWith(".txt") || lowerPath.endsWith(".log")) {
                return "TEXT";
            } else if (lowerPath.endsWith(".csv")) {
                return "CSV";
            } else if (lowerPath.endsWith(".xml")) {
                return "XML";
            } else if (lowerPath.endsWith(".json")) {
                return "JSON";
            } else if (lowerPath.endsWith(".properties") || lowerPath.endsWith(".config")) {
                return "CONFIG";
            } else if (lowerPath.endsWith(".dat") || lowerPath.endsWith(".bin")) {
                return "BINARY";
            } else if (lowerPath.endsWith(".xls") || lowerPath.endsWith(".xlsx")) {
                return "EXCEL";
            } else if (lowerPath.endsWith(".pdf")) {
                return "PDF";
            }
            
            return "OTHER";
        }
        
        private String detectFileFormat(String filePath) {
            String fileType = detectFileType(filePath);
            
            switch (fileType) {
                case "TEXT":
                case "LOG":
                    return "PLAIN_TEXT";
                case "CSV":
                    return "CSV";
                case "XML":
                    return "XML";
                case "JSON":
                    return "JSON";
                case "CONFIG":
                    return "PROPERTIES";
                case "BINARY":
                    return "BINARY";
                case "EXCEL":
                    return "EXCEL";
                default:
                    return "UNKNOWN";
            }
        }
        
        private boolean isSharedPath(String filePath) {
            if (filePath == null) return false;
            
            // Chemins réseau UNC
            if (filePath.startsWith("\\\\")) {
                return true;
            }
            
            // Chemins partagés typiques
            if (filePath.contains("/shared/") || filePath.contains("\\shared\\") ||
                filePath.contains("/common/") || filePath.contains("\\common\\") ||
                filePath.contains("/public/") || filePath.contains("\\public\\")) {
                return true;
            }
            
            return false;
        }
        
        private String determineAccessTypeFromMethod(String methodName) {
            if (methodName.equals("exists") || methodName.equals("canRead") ||
                methodName.equals("length") || methodName.equals("lastModified")) {
                return "READ";
            } else if (methodName.equals("createNewFile") || methodName.equals("mkdir") ||
                      methodName.equals("mkdirs")) {
                return "WRITE";
            } else if (methodName.equals("delete") || methodName.equals("renameTo")) {
                return "READ_WRITE";
            }
            
            return "UNKNOWN";
        }
        
        private String determineNIOAccessType(String methodName) {
            if (methodName.startsWith("read") || methodName.equals("exists") ||
                methodName.equals("isDirectory") || methodName.equals("isRegularFile")) {
                return "READ";
            } else if (methodName.startsWith("write") || methodName.equals("create") ||
                      methodName.equals("createDirectory")) {
                return "WRITE";
            } else if (methodName.equals("delete") || methodName.equals("move") ||
                      methodName.equals("copy")) {
                return "READ_WRITE";
            }
            
            return "READ_WRITE";
        }
    }
    
    private void scanPropertyFiles(Path path, Map<String, Dependencies.FileDependency> dependencyMap)
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
                                       Map<String, Dependencies.FileDependency> dependencyMap)
            throws IOException {
        
        Properties props = new Properties();
        props.load(Files.newInputStream(propFile));
        
        props.forEach((key, value) -> {
            String keyStr = key.toString().toLowerCase();
            String valueStr = value.toString();
            
            // Rechercher les propriétés qui contiennent des chemins
            if (keyStr.contains("path") || keyStr.contains("file") || 
                keyStr.contains("directory") || keyStr.contains("folder") ||
                FILE_PATH_PATTERN.matcher(valueStr).find()) {
                
                Dependencies.FileDependency file = Dependencies.FileDependency.builder()
                        .filePath(valueStr)
                        .fileType(detectFileType(valueStr))
                        .format(detectFileFormat(valueStr))
                        .accessType("CONFIG")
                        .isShared(isSharedPath(valueStr))
                        .build();
                
                dependencyMap.put(valueStr, file);
            }
        });
    }
    
    private void scanConfigFiles(Path path, Map<String, Dependencies.FileDependency> dependencyMap)
            throws IOException {
        
        // Scanner les fichiers XML de configuration
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith(".xml") && 
                             (p.toString().contains("config") || p.toString().contains("context")))
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
                                     Map<String, Dependencies.FileDependency> dependencyMap)
            throws IOException {
        
        String content = Files.readString(configFile);
        
        // Rechercher les chemins dans le contenu
        java.util.regex.Matcher matcher = FILE_PATH_PATTERN.matcher(content);
        while (matcher.find()) {
            String filePath = matcher.group();
            
            Dependencies.FileDependency file = Dependencies.FileDependency.builder()
                    .filePath(filePath)
                    .fileType(detectFileType(filePath))
                    .format(detectFileFormat(filePath))
                    .accessType("CONFIG")
                    .isShared(isSharedPath(filePath))
                    .build();
            
            dependencyMap.put(filePath, file);
        }
    }
    
    private String detectFileType(String filePath) {
        if (filePath == null) return "UNKNOWN";
        
        String lowerPath = filePath.toLowerCase();
        
        if (lowerPath.endsWith(".txt") || lowerPath.endsWith(".log")) {
            return "TEXT";
        } else if (lowerPath.endsWith(".csv")) {
            return "CSV";
        } else if (lowerPath.endsWith(".xml")) {
            return "XML";
        } else if (lowerPath.endsWith(".json")) {
            return "JSON";
        } else if (lowerPath.endsWith(".properties") || lowerPath.endsWith(".config")) {
            return "CONFIG";
        } else if (lowerPath.endsWith(".dat") || lowerPath.endsWith(".bin")) {
            return "BINARY";
        }
        
        return "OTHER";
    }
    
    private String detectFileFormat(String filePath) {
        String fileType = detectFileType(filePath);
        
        switch (fileType) {
            case "TEXT":
                return "PLAIN_TEXT";
            case "CSV":
                return "CSV";
            case "XML":
                return "XML";
            case "JSON":
                return "JSON";
            case "CONFIG":
                return "PROPERTIES";
            case "BINARY":
                return "BINARY";
            default:
                return "UNKNOWN";
        }
    }
    
    private boolean isSharedPath(String filePath) {
        if (filePath == null) return false;
        
        return filePath.startsWith("\\\\") || 
               filePath.contains("/shared/") || 
               filePath.contains("\\shared\\");
    }
}
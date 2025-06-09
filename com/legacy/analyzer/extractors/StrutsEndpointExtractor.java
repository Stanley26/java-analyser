package com.legacy.analyzer.extractors.endpoints;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.legacy.analyzer.model.Endpoint;
import com.legacy.analyzer.parser.StrutsConfigParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class StrutsEndpointExtractor {
    
    private final JavaParser javaParser = new JavaParser();
    private final StrutsConfigParser strutsConfigParser;
    
    public List<Endpoint> extractEndpoints(Path modulePath, String applicationName,
                                         String moduleName) throws IOException {
        log.debug("Extraction des endpoints Struts depuis: {}", modulePath);
        
        List<Endpoint> endpoints = new ArrayList<>();
        
        // 1. Parser struts-config.xml
        Path strutsConfigPath = modulePath.resolve("WEB-INF/struts-config.xml");
        if (!Files.exists(strutsConfigPath)) {
            log.debug("Pas de struts-config.xml trouvé");
            return endpoints;
        }
        
        Map<String, StrutsActionMapping> actionMappings = 
                strutsConfigParser.parseStrutsConfig(strutsConfigPath);
        
        // 2. Analyser les classes Action
        Map<String, ClassInfo> actionClasses = findActionClasses(modulePath);
        
        // 3. Créer les endpoints
        for (Map.Entry<String, StrutsActionMapping> entry : actionMappings.entrySet()) {
            String path = entry.getKey();
            StrutsActionMapping mapping = entry.getValue();
            
            ClassInfo actionClass = actionClasses.get(mapping.getType());
            if (actionClass != null) {
                List<Endpoint> actionEndpoints = createStrutsEndpoints(
                        applicationName, moduleName, path, mapping, actionClass
                );
                endpoints.addAll(actionEndpoints);
            } else {
                // Créer un endpoint basique même si la classe n'est pas trouvée
                Endpoint endpoint = createBasicStrutsEndpoint(
                        applicationName, moduleName, path, mapping
                );
                endpoints.add(endpoint);
            }
        }
        
        return endpoints;
    }
    
    private Map<String, ClassInfo> findActionClasses(Path modulePath) throws IOException {
        Map<String, ClassInfo> classes = new HashMap<>();
        
        Path classesPath = modulePath.resolve("WEB-INF/classes");
        if (!Files.exists(classesPath)) {
            classesPath = modulePath.resolve("classes");
        }
        
        if (Files.exists(classesPath)) {
            scanForActionClasses(classesPath, classes);
        }
        
        // Scanner aussi les sources si disponibles
        Path srcPath = modulePath.resolve("src");
        if (Files.exists(srcPath)) {
            scanForActionClasses(srcPath, classes);
        }
        
        return classes;
    }
    
    private void scanForActionClasses(Path path, Map<String, ClassInfo> classes) 
            throws IOException {
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                         analyzeJavaFile(javaFile, classes);
                     } catch (Exception e) {
                         log.error("Erreur lors de l'analyse du fichier: {}", javaFile, e);
                     }
                 });
        }
    }
    
    private void analyzeJavaFile(Path javaFile, Map<String, ClassInfo> classes) 
            throws IOException {
        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
        if (!parseResult.isSuccessful()) {
            return;
        }
        
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return;
        
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            if (isStrutsAction(classDecl)) {
                String fullClassName = getFullClassName(classDecl);
                ClassInfo classInfo = new ClassInfo();
                classInfo.className = classDecl.getNameAsString();
                classInfo.fullClassName = fullClassName;
                classInfo.sourceFile = javaFile;
                classInfo.classDeclaration = classDecl;
                classInfo.methods = extractActionMethods(classDecl);
                
                classes.put(fullClassName, classInfo);
            }
        });
    }
    
    private boolean isStrutsAction(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getExtendedTypes().stream()
                .anyMatch(type -> {
                    String typeName = type.getNameAsString();
                    return typeName.contains("Action") || 
                           typeName.equals("DispatchAction") ||
                           typeName.equals("MappingDispatchAction") ||
                           typeName.equals("LookupDispatchAction");
                });
    }
    
    private List<MethodInfo> extractActionMethods(ClassOrInterfaceDeclaration classDecl) {
        List<MethodInfo> methods = new ArrayList<>();
        
        classDecl.getMethods().forEach(method -> {
            String methodName = method.getNameAsString();
            
            // Méthodes d'action Struts
            if (methodName.equals("execute") || 
                methodName.equals("perform") ||
                isDispatchMethod(method)) {
                
                MethodInfo methodInfo = new MethodInfo();
                methodInfo.methodName = methodName;
                methodInfo.methodDeclaration = method;
                methodInfo.parameters = extractMethodParameters(method);
                methods.add(methodInfo);
            }
        });
        
        return methods;
    }
    
    private boolean isDispatchMethod(MethodDeclaration method) {
        // Pour DispatchAction, toute méthode avec la signature appropriée
        return method.getParameters().size() == 4 &&
               method.getParameter(0).getTypeAsString().contains("ActionMapping") &&
               method.getParameter(1).getTypeAsString().contains("ActionForm") &&
               method.getParameter(2).getTypeAsString().contains("HttpServletRequest") &&
               method.getParameter(3).getTypeAsString().contains("HttpServletResponse");
    }
    
    private List<String> extractMethodParameters(MethodDeclaration method) {
        List<String> parameters = new ArrayList<>();
        
        // Analyser le corps de la méthode pour trouver les request.getParameter()
        method.accept(new com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void>() {
            @Override
            public void visit(com.github.javaparser.ast.expr.MethodCallExpr expr, Void arg) {
                if (expr.getNameAsString().equals("getParameter") &&
                    expr.getScope().isPresent() &&
                    expr.getScope().get().toString().contains("request")) {
                    
                    if (expr.getArguments().size() == 1 &&
                        expr.getArgument(0) instanceof com.github.javaparser.ast.expr.StringLiteralExpr) {
                        String paramName = expr.getArgument(0).asStringLiteralExpr().getValue();
                        parameters.add(paramName);
                    }
                }
                super.visit(expr, arg);
            }
        }, null);
        
        return parameters;
    }
    
    private List<Endpoint> createStrutsEndpoints(String applicationName, String moduleName,
                                                String path, StrutsActionMapping mapping,
                                                ClassInfo actionClass) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Pour chaque méthode d'action
        for (MethodInfo methodInfo : actionClass.methods) {
            Endpoint endpoint = Endpoint.builder()
                    .id(generateEndpointId(applicationName, actionClass.className, methodInfo.methodName))
                    .applicationName(applicationName)
                    .moduleName(moduleName)
                    .className(actionClass.className)
                    .methodName(methodInfo.methodName)
                    .url(path)
                    .httpMethods(determineHttpMethods(mapping))
                    .parameters(createParameters(methodInfo.parameters))
                    .sourceLocation(createSourceLocation(actionClass.sourceFile, methodInfo.methodDeclaration))
                    .metadata(createStrutsMetadata(mapping))
                    .build();
            
            endpoints.add(endpoint);
        }
        
        // Si pas de méthode trouvée, créer un endpoint par défaut
        if (endpoints.isEmpty()) {
            endpoints.add(createBasicStrutsEndpoint(applicationName, moduleName, path, mapping));
        }
        
        return endpoints;
    }
    
    private Endpoint createBasicStrutsEndpoint(String applicationName, String moduleName,
                                             String path, StrutsActionMapping mapping) {
        return Endpoint.builder()
                .id(generateEndpointId(applicationName, mapping.getType(), "execute"))
                .applicationName(applicationName)
                .moduleName(moduleName)
                .className(extractClassName(mapping.getType()))
                .methodName("execute")
                .url(path)
                .httpMethods(determineHttpMethods(mapping))
                .metadata(createStrutsMetadata(mapping))
                .build();
    }
    
    private Set<Endpoint.HttpMethod> determineHttpMethods(StrutsActionMapping mapping) {
        Set<Endpoint.HttpMethod> methods = new HashSet<>();
        
        // Struts supporte généralement GET et POST
        methods.add(Endpoint.HttpMethod.GET);
        methods.add(Endpoint.HttpMethod.POST);
        
        // Vérifier si des méthodes spécifiques sont configurées
        if (mapping.getMethods() != null && !mapping.getMethods().isEmpty()) {
            methods.clear();
            for (String method : mapping.getMethods()) {
                try {
                    methods.add(Endpoint.HttpMethod.valueOf(method.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("Méthode HTTP inconnue: {}", method);
                }
            }
        }
        
        return methods;
    }
    
    private List<Endpoint.Parameter> createParameters(List<String> paramNames) {
        List<Endpoint.Parameter> parameters = new ArrayList<>();
        
        for (String paramName : paramNames) {
            parameters.add(Endpoint.Parameter.builder()
                    .name(paramName)
                    .type("String")
                    .source("QUERY")
                    .required(false)
                    .build());
        }
        
        return parameters;
    }
    
    private Map<String, Object> createStrutsMetadata(StrutsActionMapping mapping) {
        Map<String, Object> metadata = new HashMap<>();
        
        metadata.put("framework", "struts");
        metadata.put("actionType", mapping.getType());
        
        if (mapping.getName() != null) {
            metadata.put("formBean", mapping.getName());
        }
        
        if (mapping.getScope() != null) {
            metadata.put("scope", mapping.getScope());
        }
        
        if (mapping.getValidate() != null) {
            metadata.put("validate", mapping.getValidate());
        }
        
        if (mapping.getForwards() != null && !mapping.getForwards().isEmpty()) {
            metadata.put("forwards", mapping.getForwards());
        }
        
        return metadata;
    }
    
    private String extractClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
    
    private String getFullClassName(ClassOrInterfaceDeclaration classDecl) {
        String packageName = "";
        if (classDecl.findCompilationUnit().isPresent()) {
            CompilationUnit cu = classDecl.findCompilationUnit().get();
            if (cu.getPackageDeclaration().isPresent()) {
                packageName = cu.getPackageDeclaration().get().getNameAsString();
            }
        }
        
        return packageName.isEmpty() ? classDecl.getNameAsString() :
               packageName + "." + classDecl.getNameAsString();
    }
    
    private String generateEndpointId(String appName, String className, String methodName) {
        return String.format("%s_%s_%s_%s",
                appName, className, methodName, UUID.randomUUID().toString().substring(0, 8));
    }
    
    private Endpoint.SourceLocation createSourceLocation(Path file, MethodDeclaration method) {
        return Endpoint.SourceLocation.builder()
                .filePath(file.toString())
                .startLine(method.getBegin().map(pos -> pos.line).orElse(0))
                .endLine(method.getEnd().map(pos -> pos.line).orElse(0))
                .build();
    }
    
    // Classes internes pour stocker les informations
    private static class ClassInfo {
        String className;
        String fullClassName;
        Path sourceFile;
        ClassOrInterfaceDeclaration classDeclaration;
        List<MethodInfo> methods;
    }
    
    private static class MethodInfo {
        String methodName;
        MethodDeclaration methodDeclaration;
        List<String> parameters;
    }
    
    // Classe pour représenter un mapping Struts
    public static class StrutsActionMapping {
        private String path;
        private String type;
        private String name;
        private String scope;
        private String validate;
        private List<String> methods;
        private Map<String, String> forwards;
        
        // Getters et setters
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        
        public String getValidate() { return validate; }
        public void setValidate(String validate) { this.validate = validate; }
        
        public List<String> getMethods() { return methods; }
        public void setMethods(List<String> methods) { this.methods = methods; }
        
        public Map<String, String> getForwards() { return forwards; }
        public void setForwards(Map<String, String> forwards) { this.forwards = forwards; }
    }
}
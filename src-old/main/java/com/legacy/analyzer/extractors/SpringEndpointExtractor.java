package com.legacy.analyzer.extractors.endpoints;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.legacy.analyzer.model.Endpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class SpringEndpointExtractor {
    
    private final JavaParser javaParser = new JavaParser();
    
    public List<Endpoint> extractEndpoints(Path modulePath, String applicationName,
                                         String moduleName) throws IOException {
        log.debug("Extraction des endpoints Spring depuis: {}", modulePath);
        
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Scanner les classes Java
        Path classesPath = modulePath.resolve("WEB-INF/classes");
        if (!Files.exists(classesPath)) {
            classesPath = modulePath.resolve("classes");
        }
        
        if (Files.exists(classesPath)) {
            extractFromPath(classesPath, applicationName, moduleName, endpoints);
        }
        
        // Scanner les sources si disponibles
        Path srcPath = modulePath.resolve("src");
        if (Files.exists(srcPath)) {
            extractFromPath(srcPath, applicationName, moduleName, endpoints);
        }
        
        return endpoints;
    }
    
    private void extractFromPath(Path path, String applicationName, String moduleName,
                               List<Endpoint> endpoints) throws IOException {
        
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                         extractFromJavaFile(javaFile, applicationName, moduleName, endpoints);
                     } catch (Exception e) {
                         log.error("Erreur lors de l'analyse du fichier: {}", javaFile, e);
                     }
                 });
        }
    }
    
    private void extractFromJavaFile(Path javaFile, String applicationName,
                                   String moduleName, List<Endpoint> endpoints) 
            throws IOException {
        
        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
        if (!parseResult.isSuccessful()) {
            return;
        }
        
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return;
        
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            if (isSpringController(classDecl)) {
                extractControllerEndpoints(classDecl, applicationName, moduleName, 
                                         endpoints, javaFile);
            }
        });
    }
    
    private boolean isSpringController(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getAnnotations().stream()
                .anyMatch(ann -> {
                    String name = ann.getNameAsString();
                    return name.equals("Controller") || 
                           name.equals("RestController") ||
                           name.equals("RequestMapping");
                });
    }
    
    private void extractControllerEndpoints(ClassOrInterfaceDeclaration classDecl,
                                          String applicationName, String moduleName,
                                          List<Endpoint> endpoints, Path sourceFile) {
        
        String className = classDecl.getNameAsString();
        
        // Extraire le @RequestMapping au niveau de la classe
        String classLevelPath = extractClassLevelPath(classDecl);
        
        // Parcourir toutes les méthodes
        classDecl.getMethods().forEach(method -> {
            List<Endpoint> methodEndpoints = extractMethodEndpoints(
                    method, classLevelPath, applicationName, moduleName, 
                    className, sourceFile
            );
            endpoints.addAll(methodEndpoints);
        });
    }
    
    private String extractClassLevelPath(ClassOrInterfaceDeclaration classDecl) {
        Optional<AnnotationExpr> requestMapping = classDecl.getAnnotations().stream()
                .filter(ann -> ann.getNameAsString().equals("RequestMapping"))
                .findFirst();
        
        if (requestMapping.isPresent()) {
            return extractPathFromAnnotation(requestMapping.get());
        }
        
        return "";
    }
    
    private List<Endpoint> extractMethodEndpoints(MethodDeclaration method,
                                                String classLevelPath,
                                                String applicationName,
                                                String moduleName,
                                                String className,
                                                Path sourceFile) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Rechercher les annotations de mapping
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String annotationName = annotation.getNameAsString();
            
            if (isMappingAnnotation(annotationName)) {
                Endpoint endpoint = createSpringEndpoint(
                        method, annotation, classLevelPath, applicationName,
                        moduleName, className, sourceFile
                );
                
                if (endpoint != null) {
                    endpoints.add(endpoint);
                }
            }
        }
        
        return endpoints;
    }
    
    private boolean isMappingAnnotation(String annotationName) {
        return annotationName.equals("RequestMapping") ||
               annotationName.equals("GetMapping") ||
               annotationName.equals("PostMapping") ||
               annotationName.equals("PutMapping") ||
               annotationName.equals("DeleteMapping") ||
               annotationName.equals("PatchMapping");
    }
    
    private Endpoint createSpringEndpoint(MethodDeclaration method,
                                        AnnotationExpr annotation,
                                        String classLevelPath,
                                        String applicationName,
                                        String moduleName,
                                        String className,
                                        Path sourceFile) {
        
        // Extraire le path de l'annotation
        String methodPath = extractPathFromAnnotation(annotation);
        String fullPath = combinePaths(classLevelPath, methodPath);
        
        // Extraire les méthodes HTTP
        Set<Endpoint.HttpMethod> httpMethods = extractHttpMethods(annotation);
        
        // Extraire les paramètres
        List<Endpoint.Parameter> parameters = extractMethodParameters(method);
        
        // Créer l'endpoint
        return Endpoint.builder()
                .id(generateEndpointId(applicationName, className, method.getNameAsString()))
                .applicationName(applicationName)
                .moduleName(moduleName)
                .className(className)
                .methodName(method.getNameAsString())
                .url(fullPath)
                .httpMethods(httpMethods)
                .parameters(parameters)
                .sourceLocation(createSourceLocation(sourceFile, method))
                .metadata(createSpringMetadata(annotation))
                .build();
    }
    
    private String extractPathFromAnnotation(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                String memberName = pair.getNameAsString();
                
                if (memberName.equals("value") || memberName.equals("path")) {
                    return extractStringValue(pair.getValue());
                }
            }
        } else if (annotation instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr singleAnnotation = (SingleMemberAnnotationExpr) annotation;
            return extractStringValue(singleAnnotation.getMemberValue());
        }
        
        return "";
    }
    
    private String extractStringValue(Expression expr) {
        if (expr instanceof StringLiteralExpr) {
            return ((StringLiteralExpr) expr).getValue();
        } else if (expr instanceof ArrayInitializerExpr) {
            // Prendre la première valeur du tableau
            ArrayInitializerExpr array = (ArrayInitializerExpr) expr;
            if (!array.getValues().isEmpty() && array.getValues().get(0) instanceof StringLiteralExpr) {
                return ((StringLiteralExpr) array.getValues().get(0)).getValue();
            }
        }
        
        return "";
    }
    
    private Set<Endpoint.HttpMethod> extractHttpMethods(AnnotationExpr annotation) {
        Set<Endpoint.HttpMethod> methods = new HashSet<>();
        String annotationName = annotation.getNameAsString();
        
        // Annotations spécifiques aux méthodes HTTP
        switch (annotationName) {
            case "GetMapping":
                methods.add(Endpoint.HttpMethod.GET);
                break;
            case "PostMapping":
                methods.add(Endpoint.HttpMethod.POST);
                break;
            case "PutMapping":
                methods.add(Endpoint.HttpMethod.PUT);
                break;
            case "DeleteMapping":
                methods.add(Endpoint.HttpMethod.DELETE);
                break;
            case "PatchMapping":
                methods.add(Endpoint.HttpMethod.PATCH);
                break;
            case "RequestMapping":
                // Extraire les méthodes depuis l'annotation
                methods.addAll(extractMethodsFromRequestMapping(annotation));
                break;
        }
        
        // Si aucune méthode spécifiée, par défaut GET
        if (methods.isEmpty()) {
            methods.add(Endpoint.HttpMethod.GET);
        }
        
        return methods;
    }
    
    private Set<Endpoint.HttpMethod> extractMethodsFromRequestMapping(AnnotationExpr annotation) {
        Set<Endpoint.HttpMethod> methods = new HashSet<>();
        
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if (pair.getNameAsString().equals("method")) {
                    Expression value = pair.getValue();
                    
                    if (value instanceof FieldAccessExpr) {
                        // RequestMethod.GET
                        String methodName = ((FieldAccessExpr) value).getNameAsString();
                        try {
                            methods.add(Endpoint.HttpMethod.valueOf(methodName));
                        } catch (IllegalArgumentException e) {
                            log.warn("Méthode HTTP inconnue: {}", methodName);
                        }
                    } else if (value instanceof ArrayInitializerExpr) {
                        // {RequestMethod.GET, RequestMethod.POST}
                        ArrayInitializerExpr array = (ArrayInitializerExpr) value;
                        for (Expression expr : array.getValues()) {
                            if (expr instanceof FieldAccessExpr) {
                                String methodName = ((FieldAccessExpr) expr).getNameAsString();
                                try {
                                    methods.add(Endpoint.HttpMethod.valueOf(methodName));
                                } catch (IllegalArgumentException e) {
                                    log.warn("Méthode HTTP inconnue: {}", methodName);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return methods;
    }
    
    private List<Endpoint.Parameter> extractMethodParameters(MethodDeclaration method) {
        List<Endpoint.Parameter> parameters = new ArrayList<>();
        
        method.getParameters().forEach(param -> {
            // Rechercher les annotations de paramètres
            param.getAnnotations().forEach(ann -> {
                String annName = ann.getNameAsString();
                
                Endpoint.Parameter endpointParam = null;
                
                switch (annName) {
                    case "RequestParam":
                        endpointParam = createRequestParam(param, ann);
                        break;
                    case "PathVariable":
                        endpointParam = createPathVariable(param, ann);
                        break;
                    case "RequestBody":
                        endpointParam = createRequestBody(param);
                        break;
                    case "RequestHeader":
                        endpointParam = createRequestHeader(param, ann);
                        break;
                }
                
                if (endpointParam != null) {
                    parameters.add(endpointParam);
                }
            });
        });
        
        return parameters;
    }
    
    private Endpoint.Parameter createRequestParam(com.github.javaparser.ast.body.Parameter param,
                                                AnnotationExpr annotation) {
        String paramName = extractParamName(param, annotation);
        boolean required = extractRequired(annotation);
        String defaultValue = extractDefaultValue(annotation);
        
        return Endpoint.Parameter.builder()
                .name(paramName)
                .type(param.getTypeAsString())
                .source("QUERY")
                .required(required)
                .defaultValue(defaultValue)
                .build();
    }
    
    private Endpoint.Parameter createPathVariable(com.github.javaparser.ast.body.Parameter param,
                                                AnnotationExpr annotation) {
        String paramName = extractParamName(param, annotation);
        
        return Endpoint.Parameter.builder()
                .name(paramName)
                .type(param.getTypeAsString())
                .source("PATH")
                .required(true)
                .build();
    }
    
    private Endpoint.Parameter createRequestBody(com.github.javaparser.ast.body.Parameter param) {
        return Endpoint.Parameter.builder()
                .name(param.getNameAsString())
                .type(param.getTypeAsString())
                .source("BODY")
                .required(true)
                .build();
    }
    
    private Endpoint.Parameter createRequestHeader(com.github.javaparser.ast.body.Parameter param,
                                                 AnnotationExpr annotation) {
        String paramName = extractParamName(param, annotation);
        boolean required = extractRequired(annotation);
        
        return Endpoint.Parameter.builder()
                .name(paramName)
                .type(param.getTypeAsString())
                .source("HEADER")
                .required(required)
                .build();
    }
    
    private String extractParamName(com.github.javaparser.ast.body.Parameter param,
                                  AnnotationExpr annotation) {
        String annotationValue = extractAnnotationValue(annotation);
        return annotationValue.isEmpty() ? param.getNameAsString() : annotationValue;
    }
    
    private String extractAnnotationValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            Expression value = ((SingleMemberAnnotationExpr) annotation).getMemberValue();
            if (value instanceof StringLiteralExpr) {
                return ((StringLiteralExpr) value).getValue();
            }
        } else if (annotation instanceof NormalAnnotationExpr) {
            NodeList<MemberValuePair> pairs = ((NormalAnnotationExpr) annotation).getPairs();
            for (MemberValuePair pair : pairs) {
                if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("name")) {
                    if (pair.getValue() instanceof StringLiteralExpr) {
                        return ((StringLiteralExpr) pair.getValue()).getValue();
                    }
                }
            }
        }
        return "";
    }
    
    private boolean extractRequired(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            NodeList<MemberValuePair> pairs = ((NormalAnnotationExpr) annotation).getPairs();
            for (MemberValuePair pair : pairs) {
                if (pair.getNameAsString().equals("required") && 
                    pair.getValue() instanceof BooleanLiteralExpr) {
                    return ((BooleanLiteralExpr) pair.getValue()).getValue();
                }
            }
        }
        return true; // Par défaut, les paramètres sont requis
    }
    
    private String extractDefaultValue(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            NodeList<MemberValuePair> pairs = ((NormalAnnotationExpr) annotation).getPairs();
            for (MemberValuePair pair : pairs) {
                if (pair.getNameAsString().equals("defaultValue") && 
                    pair.getValue() instanceof StringLiteralExpr) {
                    return ((StringLiteralExpr) pair.getValue()).getValue();
                }
            }
        }
        return null;
    }
    
    private String combinePaths(String classPath, String methodPath) {
        if (classPath.isEmpty()) {
            return methodPath;
        }
        if (methodPath.isEmpty()) {
            return classPath;
        }
        
        // S'assurer que les paths sont correctement combinés
        if (!classPath.endsWith("/") && !methodPath.startsWith("/")) {
            return classPath + "/" + methodPath;
        } else if (classPath.endsWith("/") && methodPath.startsWith("/")) {
            return classPath + methodPath.substring(1);
        }
        
        return classPath + methodPath;
    }
    
    private Map<String, Object> createSpringMetadata(AnnotationExpr annotation) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("framework", "spring");
        metadata.put("annotationType", annotation.getNameAsString());
        
        // Extraire d'autres métadonnées si nécessaire
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                String memberName = pair.getNameAsString();
                
                if (memberName.equals("produces") || memberName.equals("consumes")) {
                    metadata.put(memberName, extractStringArray(pair.getValue()));
                }
            }
        }
        
        return metadata;
    }
    
    private List<String> extractStringArray(Expression expr) {
        List<String> values = new ArrayList<>();
        
        if (expr instanceof StringLiteralExpr) {
            values.add(((StringLiteralExpr) expr).getValue());
        } else if (expr instanceof ArrayInitializerExpr) {
            ArrayInitializerExpr array = (ArrayInitializerExpr) expr;
            for (Expression value : array.getValues()) {
                if (value instanceof StringLiteralExpr) {
                    values.add(((StringLiteralExpr) value).getValue());
                }
            }
        }
        
        return values;
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
}
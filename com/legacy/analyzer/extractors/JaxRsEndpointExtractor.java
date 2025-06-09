package com.legacy.analyzer.extractors.endpoints;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
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
import java.util.stream.Stream;

@Slf4j
@Component
public class JaxRsEndpointExtractor {
    
    private final JavaParser javaParser = new JavaParser();
    
    public List<Endpoint> extractEndpoints(Path modulePath, String applicationName,
                                         String moduleName) throws IOException {
        log.debug("Extraction des endpoints JAX-RS depuis: {}", modulePath);
        
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
            if (isJaxRsResource(classDecl)) {
                extractResourceEndpoints(classDecl, applicationName, moduleName, 
                                       endpoints, javaFile);
            }
        });
    }
    
    private boolean isJaxRsResource(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getAnnotations().stream()
                .anyMatch(ann -> ann.getNameAsString().equals("Path"));
    }
    
    private void extractResourceEndpoints(ClassOrInterfaceDeclaration classDecl,
                                        String applicationName, String moduleName,
                                        List<Endpoint> endpoints, Path sourceFile) {
        
        String className = classDecl.getNameAsString();
        
        // Extraire le @Path au niveau de la classe
        String classLevelPath = extractClassLevelPath(classDecl);
        
        // Parcourir toutes les méthodes
        classDecl.getMethods().forEach(method -> {
            if (hasHttpMethodAnnotation(method)) {
                Endpoint endpoint = createJaxRsEndpoint(
                        method, classLevelPath, applicationName, moduleName,
                        className, sourceFile
                );
                
                if (endpoint != null) {
                    endpoints.add(endpoint);
                }
            }
        });
    }
    
    private String extractClassLevelPath(ClassOrInterfaceDeclaration classDecl) {
        Optional<AnnotationExpr> pathAnnotation = classDecl.getAnnotations().stream()
                .filter(ann -> ann.getNameAsString().equals("Path"))
                .findFirst();
        
        if (pathAnnotation.isPresent()) {
            return extractPathValue(pathAnnotation.get());
        }
        
        return "";
    }
    
    private boolean hasHttpMethodAnnotation(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .anyMatch(ann -> isHttpMethodAnnotation(ann.getNameAsString()));
    }
    
    private boolean isHttpMethodAnnotation(String annotationName) {
        return annotationName.equals("GET") ||
               annotationName.equals("POST") ||
               annotationName.equals("PUT") ||
               annotationName.equals("DELETE") ||
               annotationName.equals("HEAD") ||
               annotationName.equals("OPTIONS") ||
               annotationName.equals("PATCH");
    }
    
    private Endpoint createJaxRsEndpoint(MethodDeclaration method,
                                       String classLevelPath,
                                       String applicationName,
                                       String moduleName,
                                       String className,
                                       Path sourceFile) {
        
        // Extraire le @Path de la méthode
        String methodPath = extractMethodPath(method);
        String fullPath = combinePaths(classLevelPath, methodPath);
        
        // Extraire la méthode HTTP
        Set<Endpoint.HttpMethod> httpMethods = extractHttpMethods(method);
        
        // Extraire les paramètres
        List<Endpoint.Parameter> parameters = extractMethodParameters(method);
        
        // Extraire les types de contenu
        Map<String, Object> metadata = extractJaxRsMetadata(method);
        
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
                .metadata(metadata)
                .build();
    }
    
    private String extractMethodPath(MethodDeclaration method) {
        Optional<AnnotationExpr> pathAnnotation = method.getAnnotations().stream()
                .filter(ann -> ann.getNameAsString().equals("Path"))
                .findFirst();
        
        if (pathAnnotation.isPresent()) {
            return extractPathValue(pathAnnotation.get());
        }
        
        return "";
    }
    
    private String extractPathValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            Expression value = ((SingleMemberAnnotationExpr) annotation).getMemberValue();
            if (value instanceof StringLiteralExpr) {
                return ((StringLiteralExpr) value).getValue();
            }
        } else if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if (pair.getNameAsString().equals("value")) {
                    if (pair.getValue() instanceof StringLiteralExpr) {
                        return ((StringLiteralExpr) pair.getValue()).getValue();
                    }
                }
            }
        }
        
        return "";
    }
    
    private Set<Endpoint.HttpMethod> extractHttpMethods(MethodDeclaration method) {
        Set<Endpoint.HttpMethod> methods = new HashSet<>();
        
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String annotationName = annotation.getNameAsString();
            
            switch (annotationName) {
                case "GET":
                    methods.add(Endpoint.HttpMethod.GET);
                    break;
                case "POST":
                    methods.add(Endpoint.HttpMethod.POST);
                    break;
                case "PUT":
                    methods.add(Endpoint.HttpMethod.PUT);
                    break;
                case "DELETE":
                    methods.add(Endpoint.HttpMethod.DELETE);
                    break;
                case "HEAD":
                    methods.add(Endpoint.HttpMethod.HEAD);
                    break;
                case "OPTIONS":
                    methods.add(Endpoint.HttpMethod.OPTIONS);
                    break;
                case "PATCH":
                    methods.add(Endpoint.HttpMethod.PATCH);
                    break;
            }
        }
        
        return methods;
    }
    
    private List<Endpoint.Parameter> extractMethodParameters(MethodDeclaration method) {
        List<Endpoint.Parameter> parameters = new ArrayList<>();
        
        method.getParameters().forEach(param -> {
            // Rechercher les annotations JAX-RS sur les paramètres
            param.getAnnotations().forEach(ann -> {
                String annName = ann.getNameAsString();
                
                Endpoint.Parameter endpointParam = null;
                
                switch (annName) {
                    case "QueryParam":
                        endpointParam = createQueryParam(param, ann);
                        break;
                    case "PathParam":
                        endpointParam = createPathParam(param, ann);
                        break;
                    case "FormParam":
                        endpointParam = createFormParam(param, ann);
                        break;
                    case "HeaderParam":
                        endpointParam = createHeaderParam(param, ann);
                        break;
                    case "CookieParam":
                        endpointParam = createCookieParam(param, ann);
                        break;
                    case "MatrixParam":
                        endpointParam = createMatrixParam(param, ann);
                        break;
                }
                
                if (endpointParam != null) {
                    // Vérifier s'il y a @DefaultValue
                    Optional<AnnotationExpr> defaultValueAnn = param.getAnnotations().stream()
                            .filter(a -> a.getNameAsString().equals("DefaultValue"))
                            .findFirst();
                    
                    if (defaultValueAnn.isPresent()) {
                        String defaultValue = extractStringValue(defaultValueAnn.get());
                        endpointParam.setDefaultValue(defaultValue);
                    }
                    
                    parameters.add(endpointParam);
                }
            });
            
            // Si pas d'annotation, vérifier si c'est un body
            if (param.getAnnotations().isEmpty() || 
                param.getAnnotations().stream().noneMatch(ann -> 
                    isJaxRsParamAnnotation(ann.getNameAsString()))) {
                
                // Probablement un body parameter
                parameters.add(Endpoint.Parameter.builder()
                        .name(param.getNameAsString())
                        .type(param.getTypeAsString())
                        .source("BODY")
                        .required(true)
                        .build());
            }
        });
        
        return parameters;
    }
    
    private boolean isJaxRsParamAnnotation(String annotationName) {
        return annotationName.equals("QueryParam") ||
               annotationName.equals("PathParam") ||
               annotationName.equals("FormParam") ||
               annotationName.equals("HeaderParam") ||
               annotationName.equals("CookieParam") ||
               annotationName.equals("MatrixParam") ||
               annotationName.equals("Context");
    }
    
    private Endpoint.Parameter createQueryParam(com.github.javaparser.ast.body.Parameter param,
                                              AnnotationExpr annotation) {
        String paramName = extractStringValue(annotation);
        
        return Endpoint.Parameter.builder()
                .name(paramName)
                .type(param.getTypeAsString())
                .source("QUERY")
                .required(false) // Les query params sont généralement optionnels
                .build();
    }
    
    private Endpoint.Parameter createPathParam(com.github.javaparser.ast.body.Parameter param,
                                             AnnotationExpr annotation) {
        String paramName = extractStringValue(annotation);
        
        return Endpoint.Parameter.builder()
                .name(paramName)
                .type(param.getTypeAsString())
                .source("PATH")
                .required(true) // Les path params sont toujours requis
                .build();
    }
    
    private Endpoint.Parameter createFormParam(com.github.javaparser.ast.body.Parameter param,
                                             AnnotationExpr annotation) {
        String paramName = extractStringValue(annotation);
        
        return Endpoint.Parameter.builder()
                .name(paramName)
                .type(param.getTypeAsString())
                .source("FORM")
                .required(false)
                .build();
    }
    
    private Endpoint.Parameter createHeaderParam(com.github.javaparser.ast.body.Parameter param,
                                               AnnotationExpr annotation) {
        String paramName = extractStringValue(annotation);
        
        return Endpoint.Parameter.builder()
                .name(paramName)
                .type(param.getTypeAsString())
                .source("HEADER")
                .required(false)
                .build();
    }
    
    private Endpoint.Parameter createCookieParam(com.github.javaparser.ast.body.Parameter param,
                                               AnnotationExpr annotation) {
        String paramName = extractStringValue(annotation);
        
        return Endpoint.Parameter.builder()
                .name(paramName)
                .type(param.getTypeAsString())
                .source("COOKIE")
                .required(false)
                .build();
    }
    
    private Endpoint.Parameter createMatrixParam(com.github.javaparser.ast.body.Parameter param,
                                               AnnotationExpr annotation) {
        String paramName = extractStringValue(annotation);
        
        return Endpoint.Parameter.builder()
                .name(paramName)
                .type(param.getTypeAsString())
                .source("MATRIX")
                .required(false)
                .build();
    }
    
    private String extractStringValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            Expression value = ((SingleMemberAnnotationExpr) annotation).getMemberValue();
            if (value instanceof StringLiteralExpr) {
                return ((StringLiteralExpr) value).getValue();
            }
        } else if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if (pair.getNameAsString().equals("value")) {
                    if (pair.getValue() instanceof StringLiteralExpr) {
                        return ((StringLiteralExpr) pair.getValue()).getValue();
                    }
                }
            }
        }
        
        return "";
    }
    
    private Map<String, Object> extractJaxRsMetadata(MethodDeclaration method) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("framework", "jax-rs");
        
        // Extraire @Produces
        Optional<AnnotationExpr> producesAnn = method.getAnnotations().stream()
                .filter(ann -> ann.getNameAsString().equals("Produces"))
                .findFirst();
        
        if (producesAnn.isPresent()) {
            List<String> produces = extractMediaTypes(producesAnn.get());
            metadata.put("produces", produces);
        }
        
        // Extraire @Consumes
        Optional<AnnotationExpr> consumesAnn = method.getAnnotations().stream()
                .filter(ann -> ann.getNameAsString().equals("Consumes"))
                .findFirst();
        
        if (consumesAnn.isPresent()) {
            List<String> consumes = extractMediaTypes(consumesAnn.get());
            metadata.put("consumes", consumes);
        }
        
        // Extraire @RolesAllowed
        Optional<AnnotationExpr> rolesAnn = method.getAnnotations().stream()
                .filter(ann -> ann.getNameAsString().equals("RolesAllowed"))
                .findFirst();
        
        if (rolesAnn.isPresent()) {
            List<String> roles = extractStringArray(rolesAnn.get());
            metadata.put("rolesAllowed", roles);
        }
        
        return metadata;
    }
    
    private List<String> extractMediaTypes(AnnotationExpr annotation) {
        List<String> mediaTypes = new ArrayList<>();
        
        if (annotation instanceof SingleMemberAnnotationExpr) {
            Expression value = ((SingleMemberAnnotationExpr) annotation).getMemberValue();
            
            if (value instanceof StringLiteralExpr) {
                mediaTypes.add(((StringLiteralExpr) value).getValue());
            } else if (value instanceof ArrayInitializerExpr) {
                ArrayInitializerExpr array = (ArrayInitializerExpr) value;
                for (Expression expr : array.getValues()) {
                    if (expr instanceof StringLiteralExpr) {
                        mediaTypes.add(((StringLiteralExpr) expr).getValue());
                    } else if (expr instanceof FieldAccessExpr) {
                        // MediaType.APPLICATION_JSON
                        mediaTypes.add(expr.toString());
                    }
                }
            } else if (value instanceof FieldAccessExpr) {
                mediaTypes.add(value.toString());
            }
        }
        
        return mediaTypes;
    }
    
    private List<String> extractStringArray(AnnotationExpr annotation) {
        List<String> values = new ArrayList<>();
        
        if (annotation instanceof SingleMemberAnnotationExpr) {
            Expression value = ((SingleMemberAnnotationExpr) annotation).getMemberValue();
            
            if (value instanceof StringLiteralExpr) {
                values.add(((StringLiteralExpr) value).getValue());
            } else if (value instanceof ArrayInitializerExpr) {
                ArrayInitializerExpr array = (ArrayInitializerExpr) value;
                for (Expression expr : array.getValues()) {
                    if (expr instanceof StringLiteralExpr) {
                        values.add(((StringLiteralExpr) expr).getValue());
                    }
                }
            }
        }
        
        return values;
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
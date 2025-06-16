package com.analyzer.parsers.framework;

import com.analyzer.model.technical.Endpoint;
import com.analyzer.model.technical.EndpointDetails;
import com.analyzer.model.technical.SourceLocation;
import com.analyzer.parsers.common.EntryPointParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SpringAnnotationParser implements EntryPointParser {

    @Override
    public boolean supports(File file) {
        return file.getName().endsWith(".java");
    }

    @Override
    public List<Endpoint> parse(File javaFile, Path projectRoot) {
        List<Endpoint> foundEndpoints = new ArrayList<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

            for (ClassOrInterfaceDeclaration clas : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (isSpringController(clas)) {
                    String baseClassPath = clas.getAnnotationByName("RequestMapping")
                            .map(this::extractPathFromAnnotation).orElse("");
                    
                    String fullClassName = packageName + "." + clas.getNameAsString();

                    for (MethodDeclaration method : clas.getMethods()) {
                        processMethod(method, baseClassPath, fullClassName, projectRoot, foundEndpoints, javaFile);
                    }
                }
            }
        } catch (IOException | IllegalStateException e) {
            System.err.println("Erreur lors de l'analyse du fichier Spring : " + javaFile.getAbsolutePath() + " - " + e.getMessage());
        }
        return foundEndpoints;
    }

    private void processMethod(MethodDeclaration method, String baseClassPath, String fullClassName, Path projectRoot, List<Endpoint> endpointList, File javaFile) {
        handleMappingAnnotation(method, "GetMapping", "GET", baseClassPath, fullClassName, projectRoot, endpointList, javaFile);
        handleMappingAnnotation(method, "PostMapping", "POST", baseClassPath, fullClassName, projectRoot, endpointList, javaFile);
        handleMappingAnnotation(method, "PutMapping", "PUT", baseClassPath, fullClassName, projectRoot, endpointList, javaFile);
        handleMappingAnnotation(method, "DeleteMapping", "DELETE", baseClassPath, fullClassName, projectRoot, endpointList, javaFile);
        handleMappingAnnotation(method, "PatchMapping", "PATCH", baseClassPath, fullClassName, projectRoot, endpointList, javaFile);
        handleMappingAnnotation(method, "RequestMapping", null, baseClassPath, fullClassName, projectRoot, endpointList, javaFile);
    }
    
    private void handleMappingAnnotation(MethodDeclaration method, String annotationName, String defaultHttpMethod, String baseClassPath, String fullClassName, Path projectRoot, List<Endpoint> endpointList, File javaFile) {
        method.getAnnotationByName(annotationName).ifPresent(annotation -> {
            Endpoint endpoint = new Endpoint();
            String methodPath = extractPathFromAnnotation(annotation);
            
            String url = (baseClassPath + methodPath).replaceAll("/+", "/");
            endpoint.fullUrl = url.isEmpty() ? "/" : url;
            
            endpoint.httpMethod = Optional.ofNullable(defaultHttpMethod)
                .orElseGet(() -> extractMethodFromRequestMapping(annotation));

            EndpointDetails details = new EndpointDetails();
            details.controllerClass = fullClassName;
            details.handlerMethod = method.getSignature().asString();
            details.returnType = method.getType().asString();
            
            SourceLocation location = new SourceLocation();
            location.file = projectRoot.relativize(javaFile.toPath()).toString();
            location.lineNumber = method.getBegin().map(p -> p.line).orElse(0);
            details.sourceLocation = location;
            
            endpoint.details = details;
            endpointList.add(endpoint);
        });
    }

    private boolean isSpringController(ClassOrInterfaceDeclaration clas) {
        return clas.isAnnotationPresent("RestController") || clas.isAnnotationPresent("Controller");
    }

    // --- MÉTHODE CORRIGÉE ---
    private String extractPathFromAnnotation(AnnotationExpr annotation) {
        Expression valueExpr = null;

        if (annotation.isSingleMemberAnnotationExpr()) {
            valueExpr = annotation.asSingleMemberAnnotationExpr().getMemberValue();
        } else if (annotation.isNormalAnnotationExpr()) {
            valueExpr = annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path"))
                    .findFirst()
                    .map(pair -> pair.getValue())
                    .orElse(null);
        }

        if (valueExpr == null) {
            return "";
        }
        
        // Gère le cas où la valeur est un tableau : @RequestMapping({"/path1", "/path2"})
        if (valueExpr.isArrayInitializerExpr()) {
            ArrayInitializerExpr arrayExpr = valueExpr.asArrayInitializerExpr();
            // On prend le premier élément du tableau comme chemin de référence.
            if (!arrayExpr.getValues().isEmpty()) {
                return arrayExpr.getValues().get(0).asStringLiteralExpr().asString();
            }
        } 
        // Gère le cas où la valeur est une simple chaîne : @RequestMapping("/path")
        else if (valueExpr.isStringLiteralExpr()) {
            return valueExpr.asStringLiteralExpr().asString();
        }

        return "";
    }
    
    private String extractMethodFromRequestMapping(AnnotationExpr annotation) {
        if (annotation.isNormalAnnotationExpr()) {
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(p -> p.getNameAsString().equals("method"))
                .findFirst()
                .map(p -> p.getValue().toString().replace("RequestMethod.", ""))
                .orElse("GET");
        }
        return "GET";
    }
}

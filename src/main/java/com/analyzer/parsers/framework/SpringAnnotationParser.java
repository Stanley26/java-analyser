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
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Un parseur de point d'entrée spécialisé dans la détection des endpoints
 * déclarés avec les annotations de Spring MVC.
 */
public class SpringAnnotationParser implements EntryPointParser {

    /**
     * Détermine si ce parseur doit analyser le fichier fourni.
     * Il ne s'intéresse qu'aux fichiers de code source Java.
     *
     * @param file Le fichier à tester.
     * @return true si le nom du fichier se termine par ".java", false sinon.
     */
    @Override
    public boolean supports(File file) {
        return file.getName().endsWith(".java");
    }

    /**
     * Analyse un fichier source Java pour y trouver des contrôleurs et des endpoints Spring.
     *
     * @param javaFile Le fichier Java à analyser.
     * @param projectRoot Le chemin racine du projet pour calculer les chemins relatifs.
     * @return Une liste d'objets Endpoint trouvés.
     */
    @Override
    public List<Endpoint> parse(File javaFile, Path projectRoot) {
        List<Endpoint> foundEndpoints = new ArrayList<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

            for (ClassOrInterfaceDeclaration clas : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                // On ne s'intéresse qu'aux classes qui sont des contrôleurs Spring.
                if (isSpringController(clas)) {
                    // Récupère le chemin de base défini au niveau de la classe.
                    String baseClassPath = clas.getAnnotationByName("RequestMapping")
                            .map(this::extractPathFromAnnotation).orElse("");
                    
                    String fullClassName = packageName.isEmpty() ? clas.getNameAsString() : packageName + "." + clas.getNameAsString();

                    // Analyse chaque méthode de la classe.
                    for (MethodDeclaration method : clas.getMethods()) {
                        foundEndpoints.addAll(processMethod(method, baseClassPath, fullClassName, projectRoot, javaFile));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'analyse du fichier Java : " + javaFile.getAbsolutePath());
        }
        return foundEndpoints;
    }

    /**
     * Traite une méthode pour y trouver toutes les annotations de mapping possibles.
     */
    private List<Endpoint> processMethod(MethodDeclaration method, String baseClassPath, String fullClassName, Path projectRoot, File javaFile) {
        List<Endpoint> endpoints = new ArrayList<>();
        handleMappingAnnotation(method, "GetMapping", "GET", baseClassPath, fullClassName, projectRoot, javaFile).ifPresent(endpoints::add);
        handleMappingAnnotation(method, "PostMapping", "POST", baseClassPath, fullClassName, projectRoot, javaFile).ifPresent(endpoints::add);
        handleMappingAnnotation(method, "PutMapping", "PUT", baseClassPath, fullClassName, projectRoot, javaFile).ifPresent(endpoints::add);
        handleMappingAnnotation(method, "DeleteMapping", "DELETE", baseClassPath, fullClassName, projectRoot, javaFile).ifPresent(endpoints::add);
        handleMappingAnnotation(method, "PatchMapping", "PATCH", baseClassPath, fullClassName, projectRoot, javaFile).ifPresent(endpoints::add);
        handleMappingAnnotation(method, "RequestMapping", null, baseClassPath, fullClassName, projectRoot, javaFile).ifPresent(endpoints::add);
        return endpoints;
    }
    
    /**
     * Gère une annotation de mapping spécifique (@GetMapping, @PostMapping, etc.).
     * Si l'annotation est trouvée, crée et configure un objet Endpoint.
     */
    private Optional<Endpoint> handleMappingAnnotation(MethodDeclaration method, String annotationName, String defaultHttpMethod, String baseClassPath, String fullClassName, Path projectRoot, File javaFile) {
        return method.getAnnotationByName(annotationName).map(annotation -> {
            Endpoint endpoint = new Endpoint();
            endpoint.framework = "Spring MVC";
            String methodPath = extractPathFromAnnotation(annotation);
            
            // Construit l'URL complète en combinant le chemin de la classe et celui de la méthode.
            endpoint.fullUrl = (baseClassPath + methodPath).replaceAll("/+", "/");
            if(endpoint.fullUrl.isEmpty()) endpoint.fullUrl = "/";
            
            // Détermine la méthode HTTP. Si elle n'est pas spécifiée (cas de @RequestMapping),
            // on l'extrait de l'annotation ou on utilise GET par défaut.
            endpoint.httpMethod = Optional.ofNullable(defaultHttpMethod)
                .orElseGet(() -> extractMethodFromRequestMapping(annotation));

            // Remplit les détails techniques de l'endpoint.
            endpoint.details = new EndpointDetails();
            endpoint.details.controllerClass = fullClassName;
            endpoint.details.handlerMethod = method.getSignature().asString();
            endpoint.details.returnType = method.getType().asString();
            
            // Remplit la localisation du code source.
            endpoint.details.sourceLocation = new SourceLocation();
            endpoint.details.sourceLocation.file = projectRoot.relativize(javaFile.toPath()).toString();
            endpoint.details.sourceLocation.lineNumber = method.getBegin().map(p -> p.line).orElse(0);

            return endpoint;
        });
    }

    /**
     * Vérifie si une classe est un contrôleur Spring en cherchant les annotations pertinentes.
     */
    private boolean isSpringController(ClassOrInterfaceDeclaration clas) {
        return clas.isAnnotationPresent("RestController") || clas.isAnnotationPresent("Controller");
    }

    /**
     * Extrait la valeur du chemin d'URL d'une annotation de mapping.
     * Gère les formats @RequestMapping("/path") et @RequestMapping(value = "/path").
     */
    private String extractPathFromAnnotation(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return annotation.asSingleMemberAnnotationExpr().getMemberValue().asStringLiteralExpr().asString();
        }
        if (annotation.isNormalAnnotationExpr()) {
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path"))
                    .findFirst()
                    .map(pair -> pair.getValue().toString().replace("\"", ""))
                    .orElse("");
        }
        return "";
    }
    
    /**
     * Extrait la méthode HTTP spécifiée dans une annotation @RequestMapping.
     */
    private String extractMethodFromRequestMapping(AnnotationExpr annotation) {
        if (annotation.isNormalAnnotationExpr()) {
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(p -> p.getNameAsString().equals("method"))
                .findFirst()
                .map(p -> p.getValue().toString().replace("RequestMethod.", ""))
                .orElse("GET"); // Par défaut GET si non spécifié
        }
        return "GET"; // Par défaut GET si non spécifié
    }
}

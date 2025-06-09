package com.legacy.analyzer.extractors.endpoints;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.legacy.analyzer.model.Endpoint;
import com.legacy.analyzer.parser.WebXmlParser;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ServletEndpointExtractor {
    
    private final JavaParser javaParser = new JavaParser();
    private final WebXmlParser webXmlParser;
    
    public List<Endpoint> extractEndpoints(Path modulePath, String applicationName, 
                                         String moduleName) throws IOException {
        log.debug("Extraction des endpoints Servlet depuis: {}", modulePath);
        
        List<Endpoint> endpoints = new ArrayList<>();
        
        // 1. Analyser web.xml pour les mappings de servlets
        Map<String, String> servletMappings = extractServletMappingsFromWebXml(modulePath);
        
        // 2. Scanner les classes Java
        Path classesPath = modulePath.resolve("WEB-INF/classes");
        if (!Files.exists(classesPath)) {
            classesPath = modulePath.resolve("classes");
        }
        
        if (Files.exists(classesPath)) {
            extractFromJavaFiles(classesPath, applicationName, moduleName, 
                               servletMappings, endpoints);
        }
        
        // 3. Scanner les sources si disponibles
        Path srcPath = modulePath.resolve("src");
        if (Files.exists(srcPath)) {
            extractFromJavaFiles(srcPath, applicationName, moduleName, 
                               servletMappings, endpoints);
        }
        
        return endpoints;
    }
    
    private Map<String, String> extractServletMappingsFromWebXml(Path modulePath) {
        try {
            Path webXml = modulePath.resolve("WEB-INF/web.xml");
            if (Files.exists(webXml)) {
                return webXmlParser.parseServletMappings(webXml);
            }
        } catch (Exception e) {
            log.error("Erreur lors du parsing de web.xml", e);
        }
        return new HashMap<>();
    }
    
    private void extractFromJavaFiles(Path path, String applicationName, String moduleName,
                                    Map<String, String> servletMappings, 
                                    List<Endpoint> endpoints) throws IOException {
        
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                         extractFromJavaFile(javaFile, applicationName, moduleName, 
                                           servletMappings, endpoints);
                     } catch (Exception e) {
                         log.error("Erreur lors de l'analyse du fichier: {}", javaFile, e);
                     }
                 });
        }
    }
    
    private void extractFromJavaFile(Path javaFile, String applicationName, 
                                   String moduleName, Map<String, String> servletMappings,
                                   List<Endpoint> endpoints) throws IOException {
        
        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
        if (!parseResult.isSuccessful()) {
            log.warn("Impossible de parser le fichier: {}", javaFile);
            return;
        }
        
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return;
        
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            if (isServletClass(classDecl)) {
                extractServletEndpoints(classDecl, applicationName, moduleName, 
                                      servletMappings, endpoints, javaFile);
            }
        });
    }
    
    private boolean isServletClass(ClassOrInterfaceDeclaration classDecl) {
        // Vérifier l'héritage de HttpServlet
        if (classDecl.getExtendedTypes().stream()
                .anyMatch(type -> type.getNameAsString().contains("HttpServlet"))) {
            return true;
        }
        
        // Vérifier l'annotation @WebServlet
        return classDecl.getAnnotations().stream()
                .anyMatch(ann -> ann.getNameAsString().equals("WebServlet"));
    }
    
    private void extractServletEndpoints(ClassOrInterfaceDeclaration classDecl,
                                       String applicationName, String moduleName,
                                       Map<String, String> servletMappings,
                                       List<Endpoint> endpoints, Path sourceFile) {
        
        String className = classDecl.getNameAsString();
        String fullClassName = getFullClassName(classDecl);
        
        // Récupérer l'URL depuis @WebServlet ou web.xml
        List<String> urls = extractServletUrls(classDecl, fullClassName, servletMappings);
        
        // Extraire les méthodes HTTP
        List<MethodDeclaration> httpMethods = classDecl.getMethods().stream()
                .filter(this::isHttpMethod)
                .collect(Collectors.toList());
        
        for (String url : urls) {
            for (MethodDeclaration method : httpMethods) {
                Endpoint endpoint = createServletEndpoint(
                        applicationName, moduleName, className, 
                        method, url, sourceFile
                );
                endpoints.add(endpoint);
            }
        }
    }
    
    private List<String> extractServletUrls(ClassOrInterfaceDeclaration classDecl,
                                          String fullClassName,
                                          Map<String, String> servletMappings) {
        List<String> urls = new ArrayList<>();
        
        // 1. Chercher l'annotation @WebServlet
        Optional<AnnotationExpr> webServletAnnotation = classDecl.getAnnotations().stream()
                .filter(ann -> ann.getNameAsString().equals("WebServlet"))
                .findFirst();
        
        if (webServletAnnotation.isPresent()) {
            urls.addAll(extractUrlsFromWebServletAnnotation(webServletAnnotation.get()));
        }
        
        // 2. Chercher dans web.xml
        String mappingFromXml = servletMappings.get(fullClassName);
        if (mappingFromXml != null) {
            urls.add(mappingFromXml);
        }
        
        // Si aucune URL trouvée, utiliser le nom de la classe
        if (urls.isEmpty()) {
            urls.add("/servlet/" + classDecl.getNameAsString());
        }
        
        return urls;
    }
    
    private List<String> extractUrlsFromWebServletAnnotation(AnnotationExpr annotation) {
        List<String> urls = new ArrayList<>();
        
        annotation.accept(new com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void>() {
            @Override
            public void visit(com.github.javaparser.ast.expr.MemberValuePair pair, Void arg) {
                if (pair.getNameAsString().equals("urlPatterns") || 
                    pair.getNameAsString().equals("value")) {
                    
                    Expression value = pair.getValue();
                    if (value instanceof StringLiteralExpr) {
                        urls.add(((StringLiteralExpr) value).getValue());
                    } else if (value instanceof com.github.javaparser.ast.expr.ArrayInitializerExpr) {
                        value.asArrayInitializerExpr().getValues().forEach(v -> {
                            if (v instanceof StringLiteralExpr) {
                                urls.add(((StringLiteralExpr) v).getValue());
                            }
                        });
                    }
                }
                super.visit(pair, arg);
            }
        }, null);
        
        return urls;
    }
    
    private boolean isHttpMethod(MethodDeclaration method) {
        String methodName = method.getNameAsString();
        return methodName.equals("doGet") || methodName.equals("doPost") ||
               methodName.equals("doPut") || methodName.equals("doDelete") ||
               methodName.equals("doHead") || methodName.equals("doOptions") ||
               methodName.equals("service");
    }
    
    private Endpoint createServletEndpoint(String applicationName, String moduleName,
                                         String className, MethodDeclaration method,
                                         String url, Path sourceFile) {
        
        Set<Endpoint.HttpMethod> httpMethods = determineHttpMethods(method);
        
        return Endpoint.builder()
                .id(generateEndpointId(applicationName, className, method.getNameAsString()))
                .applicationName(applicationName)
                .moduleName(moduleName)
                .className(className)
                .methodName(method.getNameAsString())
                .url(url)
                .httpMethods(httpMethods)
                .parameters(extractParameters(method))
                .sourceLocation(createSourceLocation(sourceFile, method))
                .build();
    }
    
    private Set<Endpoint.HttpMethod> determineHttpMethods(MethodDeclaration method) {
        Set<Endpoint.HttpMethod> methods = new HashSet<>();
        String methodName = method.getNameAsString();
        
        switch (methodName) {
            case "doGet":
                methods.add(Endpoint.HttpMethod.GET);
                break;
            case "doPost":
                methods.add(Endpoint.HttpMethod.POST);
                break;
            case "doPut":
                methods.add(Endpoint.HttpMethod.PUT);
                break;
            case "doDelete":
                methods.add(Endpoint.HttpMethod.DELETE);
                break;
            case "doHead":
                methods.add(Endpoint.HttpMethod.HEAD);
                break;
            case "doOptions":
                methods.add(Endpoint.HttpMethod.OPTIONS);
                break;
            case "service":
                // La méthode service peut gérer toutes les méthodes HTTP
                methods.addAll(Arrays.asList(Endpoint.HttpMethod.values()));
                break;
        }
        
        return methods;
    }
    
    private List<Endpoint.Parameter> extractParameters(MethodDeclaration method) {
        List<Endpoint.Parameter> parameters = new ArrayList<>();
        
        // Analyse du corps de la méthode pour trouver les getParameter()
        method.accept(new com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void>() {
            @Override
            public void visit(com.github.javaparser.ast.expr.MethodCallExpr expr, Void arg) {
                if (expr.getNameAsString().equals("getParameter") && 
                    expr.getArguments().size() == 1) {
                    
                    Expression paramNameExpr = expr.getArguments().get(0);
                    if (paramNameExpr instanceof StringLiteralExpr) {
                        String paramName = ((StringLiteralExpr) paramNameExpr).getValue();
                        
                        Endpoint.Parameter param = Endpoint.Parameter.builder()
                                .name(paramName)
                                .source("QUERY")
                                .type("String")
                                .required(false)
                                .build();
                        
                        parameters.add(param);
                    }
                }
                super.visit(expr, arg);
            }
        }, null);
        
        return parameters;
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
}
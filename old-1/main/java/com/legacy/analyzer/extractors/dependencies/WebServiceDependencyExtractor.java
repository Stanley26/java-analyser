package com.legacy.analyzer.extractors.dependencies;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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
public class WebServiceDependencyExtractor {
    
    private final JavaParser javaParser = new JavaParser();
    
    // Patterns pour détecter les URLs de web services
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern WSDL_PATTERN = Pattern.compile(
            "\\.wsdl(\\?.*)?$",
            Pattern.CASE_INSENSITIVE
    );
    
    public List<Dependencies.WebServiceDependency> extractDependencies(Path path,
                                                                     WebLogicApplication application)
            throws IOException {
        
        Map<String, Dependencies.WebServiceDependency> dependencyMap = new HashMap<>();
        
        // 1. Scanner les fichiers Java
        scanJavaFiles(path, dependencyMap);
        
        // 2. Scanner les fichiers WSDL
        scanWSDLFiles(path, dependencyMap);
        
        // 3. Scanner les fichiers de configuration
        scanConfigFiles(path, dependencyMap);
        
        return new ArrayList<>(dependencyMap.values());
    }
    
    private void scanJavaFiles(Path path, Map<String, Dependencies.WebServiceDependency> dependencyMap)
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
                                   Map<String, Dependencies.WebServiceDependency> dependencyMap)
            throws IOException {
        
        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
        if (!parseResult.isSuccessful()) {
            return;
        }
        
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return;
        
        // Vérifier si c'est un client ou service web
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            if (isWebServiceClass(classDecl)) {
                extractWebServiceDefinition(classDecl, dependencyMap);
            }
        });
        
        // Visitor pour extraire les appels de web services
        cu.accept(new WebServiceCallVisitor(dependencyMap, javaFile), null);
    }
    
    private boolean isWebServiceClass(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getAnnotations().stream()
                .anyMatch(ann -> {
                    String name = ann.getNameAsString();
                    return name.equals("WebService") || 
                           name.equals("WebServiceClient") ||
                           name.equals("WebServiceProvider");
                });
    }
    
    private void extractWebServiceDefinition(ClassOrInterfaceDeclaration classDecl,
                                           Map<String, Dependencies.WebServiceDependency> dependencyMap) {
        
        Dependencies.WebServiceDependency.WebServiceDependencyBuilder builder = 
                Dependencies.WebServiceDependency.builder();
        
        String className = classDecl.getNameAsString();
        builder.serviceName(className);
        builder.type("SOAP");
        
        // Extraire les annotations
        classDecl.getAnnotations().forEach(ann -> {
            if (ann.getNameAsString().equals("WebService")) {
                extractWebServiceAnnotation(ann, builder);
            }
        });
        
        // Extraire les opérations
        List<String> operations = new ArrayList<>();
        classDecl.getMethods().forEach(method -> {
            if (method.isPublic() && !method.isStatic()) {
                operations.add(method.getNameAsString());
            }
        });
        builder.operations(operations);
        
        Dependencies.WebServiceDependency ws = builder.build();
        dependencyMap.put(ws.getServiceName(), ws);
    }
    
    private void extractWebServiceAnnotation(AnnotationExpr annotation,
                                           Dependencies.WebServiceDependency.WebServiceDependencyBuilder builder) {
        
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            
            normalAnnotation.getPairs().forEach(pair -> {
                String memberName = pair.getNameAsString();
                Expression value = pair.getValue();
                
                switch (memberName) {
                    case "name":
                        if (value instanceof StringLiteralExpr) {
                            builder.serviceName(((StringLiteralExpr) value).getValue());
                        }
                        break;
                    case "targetNamespace":
                        if (value instanceof StringLiteralExpr) {
                            Map<String, String> headers = new HashMap<>();
                            headers.put("targetNamespace", ((StringLiteralExpr) value).getValue());
                            builder.headers(headers);
                        }
                        break;
                    case "wsdlLocation":
                        if (value instanceof StringLiteralExpr) {
                            builder.wsdlLocation(((StringLiteralExpr) value).getValue());
                        }
                        break;
                }
            });
        }
    }
    
    private class WebServiceCallVisitor extends VoidVisitorAdapter<Void> {
        private final Map<String, Dependencies.WebServiceDependency> dependencyMap;
        private final Path sourceFile;
        
        public WebServiceCallVisitor(Map<String, Dependencies.WebServiceDependency> dependencyMap,
                                   Path sourceFile) {
            this.dependencyMap = dependencyMap;
            this.sourceFile = sourceFile;
        }
        
        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            String className = expr.getTypeAsString();
            
            // Détecter les clients SOAP (JAX-WS)
            if (className.endsWith("Service") || className.contains("PortType") ||
                className.contains("Stub") || className.contains("Proxy")) {
                extractSOAPClient(expr, className);
            }
            
            // Détecter les clients REST
            if (className.contains("RestTemplate") || className.contains("WebClient") ||
                className.contains("HttpClient") || className.contains("WebTarget")) {
                extractRESTClient(expr, className);
            }
            
            // Apache CXF
            if (className.contains("JaxWsProxyFactoryBean") || className.contains("ClientProxyFactoryBean")) {
                extractCXFClient(expr);
            }
            
            super.visit(expr, arg);
        }
        
        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            // Détecter les appels REST
            if (isRESTMethod(methodName)) {
                extractRESTCall(expr);
            }
            
            // Détecter la création de services SOAP
            if (methodName.equals("create") && expr.getScope().isPresent() &&
                expr.getScope().get().toString().contains("Service")) {
                extractSOAPServiceCreation(expr);
            }
            
            // Détecter les appels WebServiceTemplate (Spring WS)
            if (methodName.equals("marshalSendAndReceive") || methodName.equals("sendAndReceive")) {
                extractSpringWSCall(expr);
            }
            
            super.visit(expr, arg);
        }
        
        @Override
        public void visit(StringLiteralExpr expr, Void arg) {
            String value = expr.getValue();
            
            // Détecter les URLs de services web
            if (URL_PATTERN.matcher(value).matches()) {
                if (value.contains("service") || value.contains("api") || 
                    value.contains("ws") || WSDL_PATTERN.matcher(value).find()) {
                    
                    Dependencies.WebServiceDependency ws = Dependencies.WebServiceDependency.builder()
                            .serviceName(extractServiceNameFromURL(value))
                            .url(value)
                            .type(WSDL_PATTERN.matcher(value).find() ? "SOAP" : "REST")
                            .wsdlLocation(WSDL_PATTERN.matcher(value).find() ? value : null)
                            .build();
                    
                    dependencyMap.put(ws.getServiceName(), ws);
                }
            }
            
            super.visit(expr, arg);
        }
        
        private void extractSOAPClient(ObjectCreationExpr expr, String className) {
            Dependencies.WebServiceDependency.WebServiceDependencyBuilder builder = 
                    Dependencies.WebServiceDependency.builder();
            
            builder.serviceName(className);
            builder.type("SOAP");
            
            // Extraire l'URL du WSDL si disponible
            if (!expr.getArguments().isEmpty()) {
                Expression arg = expr.getArgument(0);
                if (arg instanceof ObjectCreationExpr) {
                    ObjectCreationExpr urlExpr = (ObjectCreationExpr) arg;
                    if (urlExpr.getTypeAsString().equals("URL") && !urlExpr.getArguments().isEmpty()) {
                        Expression urlArg = urlExpr.getArgument(0);
                        if (urlArg instanceof StringLiteralExpr) {
                            String wsdlUrl = ((StringLiteralExpr) urlArg).getValue();
                            builder.wsdlLocation(wsdlUrl);
                            builder.url(wsdlUrl);
                        }
                    }
                }
            }
            
            Dependencies.WebServiceDependency ws = builder.build();
            dependencyMap.put(ws.getServiceName(), ws);
        }
        
        private void extractRESTClient(ObjectCreationExpr expr, String className) {
            Dependencies.WebServiceDependency ws = Dependencies.WebServiceDependency.builder()
                    .serviceName("REST_CLIENT_" + className)
                    .type("REST")
                    .build();
            
            dependencyMap.put(ws.getServiceName(), ws);
        }
        
        private void extractCXFClient(ObjectCreationExpr expr) {
            Dependencies.WebServiceDependency.WebServiceDependencyBuilder builder = 
                    Dependencies.WebServiceDependency.builder();
            
            builder.serviceName("CXF_SERVICE");
            builder.type("SOAP");
            
            // Analyser la configuration CXF
            expr.getScope().ifPresent(scope -> {
                // Extraire plus d'informations si possible
            });
            
            Dependencies.WebServiceDependency ws = builder.build();
            dependencyMap.put(ws.getServiceName(), ws);
        }
        
        private void extractRESTCall(MethodCallExpr expr) {
            String methodName = expr.getNameAsString();
            String httpMethod = methodName.toUpperCase();
            
            // Extraire l'URL
            String url = null;
            if (!expr.getArguments().isEmpty()) {
                Expression urlArg = expr.getArgument(0);
                if (urlArg instanceof StringLiteralExpr) {
                    url = ((StringLiteralExpr) urlArg).getValue();
                }
            }
            
            if (url != null) {
                Dependencies.WebServiceDependency ws = Dependencies.WebServiceDependency.builder()
                        .serviceName(extractServiceNameFromURL(url))
                        .url(url)
                        .type("REST")
                        .operations(List.of(httpMethod))
                        .build();
                
                dependencyMap.put(ws.getServiceName(), ws);
            }
        }
        
        private void extractSOAPServiceCreation(MethodCallExpr expr) {
            Dependencies.WebServiceDependency ws = Dependencies.WebServiceDependency.builder()
                    .serviceName("SOAP_SERVICE_" + expr.getScope().map(Object::toString).orElse("unknown"))
                    .type("SOAP")
                    .build();
            
            dependencyMap.put(ws.getServiceName(), ws);
        }
        
        private void extractSpringWSCall(MethodCallExpr expr) {
            Dependencies.WebServiceDependency ws = Dependencies.WebServiceDependency.builder()
                    .serviceName("SPRING_WS_SERVICE")
                    .type("SOAP")
                    .build();
            
            // Extraire l'URL si disponible
            if (expr.getArguments().size() >= 2) {
                Expression urlArg = expr.getArgument(0);
                if (urlArg instanceof StringLiteralExpr) {
                    ws.setUrl(((StringLiteralExpr) urlArg).getValue());
                }
            }
            
            dependencyMap.put(ws.getServiceName(), ws);
        }
        
        private boolean isRESTMethod(String methodName) {
            return methodName.equalsIgnoreCase("get") ||
                   methodName.equalsIgnoreCase("post") ||
                   methodName.equalsIgnoreCase("put") ||
                   methodName.equalsIgnoreCase("delete") ||
                   methodName.equalsIgnoreCase("patch") ||
                   methodName.equalsIgnoreCase("exchange") ||
                   methodName.equalsIgnoreCase("getForObject") ||
                   methodName.equalsIgnoreCase("postForObject");
        }
        
        private String extractServiceNameFromURL(String url) {
            // Extraire un nom de service depuis l'URL
            try {
                if (url.contains("://")) {
                    String afterProtocol = url.substring(url.indexOf("://") + 3);
                    String host = afterProtocol.split("/")[0];
                    String path = afterProtocol.contains("/") ? 
                            afterProtocol.substring(afterProtocol.indexOf("/")) : "";
                    
                    // Essayer d'extraire un nom significatif
                    if (path.contains("/")) {
                        String[] parts = path.split("/");
                        for (String part : parts) {
                            if (!part.isEmpty() && !part.equals("api") && !part.equals("ws") &&
                                !part.equals("service") && !part.equals("v1") && !part.equals("v2")) {
                                return part.toUpperCase() + "_SERVICE";
                            }
                        }
                    }
                    
                    return host.replace(".", "_").toUpperCase() + "_SERVICE";
                }
            } catch (Exception e) {
                // Ignorer les erreurs de parsing
            }
            
            return "WEB_SERVICE_" + url.hashCode();
        }
    }
    
    private void scanWSDLFiles(Path path, Map<String, Dependencies.WebServiceDependency> dependencyMap)
            throws IOException {
        
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith(".wsdl"))
                 .forEach(wsdlFile -> {
                     try {
                         extractFromWSDL(wsdlFile, dependencyMap);
                     } catch (Exception e) {
                         log.error("Erreur lors de l'analyse du WSDL: {}", wsdlFile, e);
                     }
                 });
        }
    }
    
    private void extractFromWSDL(Path wsdlFile, Map<String, Dependencies.WebServiceDependency> dependencyMap) {
        // TODO: Parser le WSDL pour extraire les services et opérations
        log.debug("Analyse du fichier WSDL: {}", wsdlFile);
        
        Dependencies.WebServiceDependency ws = Dependencies.WebServiceDependency.builder()
                .serviceName(wsdlFile.getFileName().toString().replace(".wsdl", ""))
                .type("SOAP")
                .wsdlLocation(wsdlFile.toString())
                .build();
        
        dependencyMap.put(ws.getServiceName(), ws);
    }
    
    private void scanConfigFiles(Path path, Map<String, Dependencies.WebServiceDependency> dependencyMap)
            throws IOException {
        
        // Scanner les fichiers de configuration Spring, CXF, etc.
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> (p.toString().endsWith(".xml") || p.toString().endsWith(".properties")) &&
                             (p.toString().contains("ws") || p.toString().contains("service") ||
                              p.toString().contains("endpoint")))
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
                                     Map<String, Dependencies.WebServiceDependency> dependencyMap) 
            throws IOException {
        
        String content = Files.readString(configFile);
        
        // Rechercher les URLs de services
        Matcher matcher = URL_PATTERN.matcher(content);
        while (matcher.find()) {
            String url = matcher.group();
            
            if (url.contains("service") || url.contains("api") || url.contains("ws")) {
                Dependencies.WebServiceDependency ws = Dependencies.WebServiceDependency.builder()
                        .serviceName(extractServiceNameFromURL(url))
                        .url(url)
                        .type(url.contains("wsdl") ? "SOAP" : "REST")
                        .build();
                
                dependencyMap.put(ws.getServiceName(), ws);
            }
        }
    }
}
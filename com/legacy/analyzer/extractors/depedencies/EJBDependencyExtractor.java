package com.legacy.analyzer.extractors.dependencies;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.legacy.analyzer.model.Dependencies;
import com.legacy.analyzer.model.WebLogicApplication;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component
public class EJBDependencyExtractor {
    
    private final JavaParser javaParser = new JavaParser();
    private final SAXBuilder saxBuilder = new SAXBuilder();
    
    public List<Dependencies.EJBDependency> extractDependencies(Path path, 
                                                              WebLogicApplication application) 
            throws IOException {
        
        Map<String, Dependencies.EJBDependency> dependencyMap = new HashMap<>();
        
        // 1. Parser ejb-jar.xml pour les EJB déclarés
        extractFromEjbDescriptors(path, dependencyMap);
        
        // 2. Scanner les fichiers Java pour les annotations et lookups
        scanJavaFiles(path, dependencyMap);
        
        // 3. Analyser les fichiers de configuration WebLogic
        extractFromWebLogicDescriptors(path, dependencyMap);
        
        return new ArrayList<>(dependencyMap.values());
    }
    
    private void extractFromEjbDescriptors(Path path, 
                                         Map<String, Dependencies.EJBDependency> dependencyMap) {
        
        Path ejbJarXml = path.resolve("META-INF/ejb-jar.xml");
        if (Files.exists(ejbJarXml)) {
            try {
                Document document = saxBuilder.build(ejbJarXml.toFile());
                Element root = document.getRootElement();
                
                // Parser les session beans
                Element enterpriseBeans = root.getChild("enterprise-beans");
                if (enterpriseBeans != null) {
                    // Session beans
                    List<Element> sessionBeans = enterpriseBeans.getChildren("session");
                    for (Element sessionBean : sessionBeans) {
                        Dependencies.EJBDependency ejb = parseSessionBean(sessionBean);
                        dependencyMap.put(ejb.getEjbName(), ejb);
                    }
                    
                    // Entity beans (legacy)
                    List<Element> entityBeans = enterpriseBeans.getChildren("entity");
                    for (Element entityBean : entityBeans) {
                        Dependencies.EJBDependency ejb = parseEntityBean(entityBean);
                        dependencyMap.put(ejb.getEjbName(), ejb);
                    }
                    
                    // Message-driven beans
                    List<Element> messageBeans = enterpriseBeans.getChildren("message-driven");
                    for (Element messageBean : messageBeans) {
                        Dependencies.EJBDependency ejb = parseMessageDrivenBean(messageBean);
                        dependencyMap.put(ejb.getEjbName(), ejb);
                    }
                }
                
            } catch (Exception e) {
                log.error("Erreur lors du parsing de ejb-jar.xml", e);
            }
        }
    }
    
    private Dependencies.EJBDependency parseSessionBean(Element sessionBean) {
        Dependencies.EJBDependency.EJBDependencyBuilder builder = Dependencies.EJBDependency.builder();
        
        builder.ejbName(sessionBean.getChildText("ejb-name"));
        builder.interfaceClass(sessionBean.getChildText("ejb-class"));
        builder.homeInterface(sessionBean.getChildText("home"));
        builder.remoteInterface(sessionBean.getChildText("remote"));
        
        String sessionType = sessionBean.getChildText("session-type");
        builder.isStateless("Stateless".equalsIgnoreCase(sessionType));
        
        builder.isLocal(sessionBean.getChild("local") != null);
        builder.version("2.x"); // EJB 2.x si déclaré dans ejb-jar.xml
        
        // JNDI name depuis ejb-ref
        String jndiName = sessionBean.getChildText("ejb-ref-name");
        if (jndiName == null) {
            jndiName = "ejb/" + sessionBean.getChildText("ejb-name");
        }
        builder.jndiName(jndiName);
        
        return builder.build();
    }
    
    private Dependencies.EJBDependency parseEntityBean(Element entityBean) {
        // Parser pour les entity beans (EJB 2.x)
        return Dependencies.EJBDependency.builder()
                .ejbName(entityBean.getChildText("ejb-name"))
                .interfaceClass(entityBean.getChildText("ejb-class"))
                .homeInterface(entityBean.getChildText("home"))
                .remoteInterface(entityBean.getChildText("remote"))
                .version("2.x")
                .build();
    }
    
    private Dependencies.EJBDependency parseMessageDrivenBean(Element messageBean) {
        // Parser pour les MDB
        return Dependencies.EJBDependency.builder()
                .ejbName(messageBean.getChildText("ejb-name"))
                .interfaceClass(messageBean.getChildText("ejb-class"))
                .version("2.x")
                .build();
    }
    
    private void scanJavaFiles(Path path, Map<String, Dependencies.EJBDependency> dependencyMap) 
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
                                   Map<String, Dependencies.EJBDependency> dependencyMap) 
            throws IOException {
        
        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
        if (!parseResult.isSuccessful()) {
            return;
        }
        
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return;
        
        // Visitor pour extraire les dépendances EJB
        cu.accept(new EjbDependencyVisitor(dependencyMap), null);
        
        // Vérifier si c'est un EJB lui-même
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            if (isEJBClass(classDecl)) {
                extractEJBDefinition(classDecl, dependencyMap);
            }
        });
    }
    
    private class EjbDependencyVisitor extends VoidVisitorAdapter<Void> {
        private final Map<String, Dependencies.EJBDependency> dependencyMap;
        
        public EjbDependencyVisitor(Map<String, Dependencies.EJBDependency> dependencyMap) {
            this.dependencyMap = dependencyMap;
        }
        
        @Override
        public void visit(FieldDeclaration field, Void arg) {
            // Rechercher les annotations @EJB
            field.getAnnotations().forEach(annotation -> {
                if (annotation.getNameAsString().equals("EJB")) {
                    Dependencies.EJBDependency ejb = extractEJBFromAnnotation(field, annotation);
                    if (ejb.getEjbName() != null) {
                        dependencyMap.put(ejb.getEjbName(), ejb);
                    }
                }
            });
            
            super.visit(field, arg);
        }
        
        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            // Détecter les lookups JNDI
            if (methodName.equals("lookup") && expr.getScope().isPresent()) {
                String scope = expr.getScope().get().toString();
                
                if (scope.contains("InitialContext") || scope.contains("Context")) {
                    if (!expr.getArguments().isEmpty() && 
                        expr.getArgument(0) instanceof StringLiteralExpr) {
                        
                        String jndiName = ((StringLiteralExpr) expr.getArgument(0)).getValue();
                        
                        if (isEJBJndiName(jndiName)) {
                            Dependencies.EJBDependency ejb = Dependencies.EJBDependency.builder()
                                    .jndiName(jndiName)
                                    .ejbName(extractEJBNameFromJndi(jndiName))
                                    .methodsCalled(new ArrayList<>())
                                    .build();
                            
                            dependencyMap.put(ejb.getEjbName(), ejb);
                        }
                    }
                }
            }
            
            // Détecter les appels de méthodes EJB
            if (expr.getScope().isPresent()) {
                String scopeName = expr.getScope().get().toString();
                
                // Vérifier si c'est un appel sur un EJB connu
                dependencyMap.values().forEach(ejb -> {
                    if (ejb.getEjbName() != null && scopeName.contains(ejb.getEjbName())) {
                        if (ejb.getMethodsCalled() == null) {
                            ejb.setMethodsCalled(new ArrayList<>());
                        }
                        ejb.getMethodsCalled().add(methodName);
                    }
                });
            }
            
            super.visit(expr, arg);
        }
    }
    
    private Dependencies.EJBDependency extractEJBFromAnnotation(FieldDeclaration field, 
                                                              AnnotationExpr annotation) {
        Dependencies.EJBDependency.EJBDependencyBuilder builder = Dependencies.EJBDependency.builder();
        
        // Type du field
        String fieldType = field.getVariable(0).getTypeAsString();
        builder.interfaceClass(fieldType);
        
        // Nom du field comme nom EJB par défaut
        String fieldName = field.getVariable(0).getNameAsString();
        builder.ejbName(fieldName);
        
        // Extraire les attributs de l'annotation @EJB
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                String memberName = pair.getNameAsString();
                Expression value = pair.getValue();
                
                switch (memberName) {
                    case "name":
                        if (value instanceof StringLiteralExpr) {
                            builder.ejbName(((StringLiteralExpr) value).getValue());
                        }
                        break;
                    case "beanName":
                        if (value instanceof StringLiteralExpr) {
                            builder.ejbName(((StringLiteralExpr) value).getValue());
                        }
                        break;
                    case "mappedName":
                        if (value instanceof StringLiteralExpr) {
                            builder.jndiName(((StringLiteralExpr) value).getValue());
                        }
                        break;
                    case "beanInterface":
                        if (value instanceof ClassExpr) {
                            builder.interfaceClass(((ClassExpr) value).getTypeAsString());
                        }
                        break;
                }
            }
        }
        
        builder.version("3.x"); // Annotation @EJB = EJB 3.x
        builder.methodsCalled(new ArrayList<>());
        
        return builder.build();
    }
    
    private boolean isEJBClass(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getAnnotations().stream()
                .anyMatch(ann -> {
                    String name = ann.getNameAsString();
                    return name.equals("Stateless") || 
                           name.equals("Stateful") ||
                           name.equals("Singleton") ||
                           name.equals("MessageDriven") ||
                           name.equals("Entity");
                });
    }
    
    private void extractEJBDefinition(ClassOrInterfaceDeclaration classDecl,
                                    Map<String, Dependencies.EJBDependency> dependencyMap) {
        
        Dependencies.EJBDependency.EJBDependencyBuilder builder = Dependencies.EJBDependency.builder();
        
        String className = classDecl.getNameAsString();
        builder.ejbName(className);
        builder.interfaceClass(getFullClassName(classDecl));
        
        // Analyser les annotations
        classDecl.getAnnotations().forEach(ann -> {
            String annName = ann.getNameAsString();
            
            switch (annName) {
                case "Stateless":
                    builder.isStateless(true);
                    builder.version("3.x");
                    extractEJBAnnotationAttributes(ann, builder);
                    break;
                case "Stateful":
                    builder.isStateless(false);
                    builder.version("3.x");
                    extractEJBAnnotationAttributes(ann, builder);
                    break;
                case "Local":
                    builder.isLocal(true);
                    break;
                case "Remote":
                    builder.isLocal(false);
                    break;
            }
        });
        
        // Interfaces implémentées
        classDecl.getImplementedTypes().forEach(type -> {
            String interfaceName = type.getNameAsString();
            if (interfaceName.endsWith("Local")) {
                builder.isLocal(true);
            } else if (interfaceName.endsWith("Remote")) {
                builder.isLocal(false);
                builder.remoteInterface(type.toString());
            }
        });
        
        Dependencies.EJBDependency ejb = builder.build();
        if (ejb.getJndiName() == null) {
            ejb.setJndiName("ejb/" + className);
        }
        
        dependencyMap.put(ejb.getEjbName(), ejb);
    }
    
    private void extractEJBAnnotationAttributes(AnnotationExpr annotation,
                                              Dependencies.EJBDependency.EJBDependencyBuilder builder) {
        
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if (pair.getNameAsString().equals("name") && 
                    pair.getValue() instanceof StringLiteralExpr) {
                    builder.ejbName(((StringLiteralExpr) pair.getValue()).getValue());
                } else if (pair.getNameAsString().equals("mappedName") &&
                          pair.getValue() instanceof StringLiteralExpr) {
                    builder.jndiName(((StringLiteralExpr) pair.getValue()).getValue());
                }
            }
        }
    }
    
    private boolean isEJBJndiName(String jndiName) {
        return jndiName.startsWith("ejb/") || 
               jndiName.startsWith("java:comp/env/ejb/") ||
               jndiName.startsWith("java:global/") ||
               jndiName.startsWith("java:app/") ||
               jndiName.startsWith("java:module/");
    }
    
    private String extractEJBNameFromJndi(String jndiName) {
        // Extraire le nom de l'EJB depuis le JNDI
        if (jndiName.contains("/")) {
            String[] parts = jndiName.split("/");
            return parts[parts.length - 1];
        }
        return jndiName;
    }
    
    private void extractFromWebLogicDescriptors(Path path,
                                              Map<String, Dependencies.EJBDependency> dependencyMap) {
        
        Path weblogicEjbJar = path.resolve("META-INF/weblogic-ejb-jar.xml");
        if (Files.exists(weblogicEjbJar)) {
            try {
                Document document = saxBuilder.build(weblogicEjbJar.toFile());
                Element root = document.getRootElement();
                
                // Enrichir les EJB avec les infos WebLogic
                List<Element> ejbDescriptors = root.getChildren("weblogic-enterprise-bean");
                for (Element ejbDesc : ejbDescriptors) {
                    String ejbName = ejbDesc.getChildText("ejb-name");
                    
                    Dependencies.EJBDependency ejb = dependencyMap.get(ejbName);
                    if (ejb != null) {
                        // JNDI name WebLogic
                        Element jndiBinding = ejbDesc.getChild("jndi-binding");
                        if (jndiBinding != null) {
                            String jndiName = jndiBinding.getChildText("jndi-name");
                            if (jndiName != null) {
                                ejb.setJndiName(jndiName);
                            }
                        }
                    }
                }
                
            } catch (Exception e) {
                log.error("Erreur lors du parsing de weblogic-ejb-jar.xml", e);
            }
        }
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
}
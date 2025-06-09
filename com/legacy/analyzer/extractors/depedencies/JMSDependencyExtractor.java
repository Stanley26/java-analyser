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
import java.util.stream.Stream;

@Slf4j
@Component
public class JMSDependencyExtractor {
    
    private final JavaParser javaParser = new JavaParser();
    
    public List<Dependencies.JMSDependency> extractDependencies(Path path,
                                                              WebLogicApplication application)
            throws IOException {
        
        Map<String, Dependencies.JMSDependency> dependencyMap = new HashMap<>();
        
        // 1. Scanner les fichiers Java
        scanJavaFiles(path, dependencyMap);
        
        // 2. Scanner les descripteurs d'EJB (pour les MDB)
        scanEJBDescriptors(path, dependencyMap);
        
        // 3. Scanner les fichiers de configuration Spring
        scanSpringConfigs(path, dependencyMap);
        
        return new ArrayList<>(dependencyMap.values());
    }
    
    private void scanJavaFiles(Path path, Map<String, Dependencies.JMSDependency> dependencyMap)
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
                                   Map<String, Dependencies.JMSDependency> dependencyMap)
            throws IOException {
        
        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
        if (!parseResult.isSuccessful()) {
            return;
        }
        
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return;
        
        // Vérifier si c'est un MDB
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            if (isMessageDrivenBean(classDecl)) {
                extractMDBDefinition(classDecl, dependencyMap);
            }
        });
        
        // Visitor pour extraire les utilisations JMS
        cu.accept(new JMSUsageVisitor(dependencyMap, javaFile), null);
    }
    
    private boolean isMessageDrivenBean(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getAnnotations().stream()
                .anyMatch(ann -> ann.getNameAsString().equals("MessageDriven")) ||
               classDecl.getImplementedTypes().stream()
                .anyMatch(type -> type.getNameAsString().equals("MessageListener"));
    }
    
    private void extractMDBDefinition(ClassOrInterfaceDeclaration classDecl,
                                    Map<String, Dependencies.JMSDependency> dependencyMap) {
        
        Dependencies.JMSDependency.JMSDependencyBuilder builder = 
                Dependencies.JMSDependency.builder();
        
        String className = classDecl.getNameAsString();
        builder.messageType(className);
        builder.isConsumer(true);
        builder.isProducer(false);
        
        // Extraire les annotations @MessageDriven
        classDecl.getAnnotations().forEach(ann -> {
            if (ann.getNameAsString().equals("MessageDriven")) {
                extractMessageDrivenAnnotation(ann, builder);
            }
        });
        
        Dependencies.JMSDependency jms = builder.build();
        String key = jms.getQueueName() != null ? jms.getQueueName() : 
                    (jms.getTopicName() != null ? jms.getTopicName() : className);
        dependencyMap.put(key, jms);
    }
    
    private void extractMessageDrivenAnnotation(AnnotationExpr annotation,
                                              Dependencies.JMSDependency.JMSDependencyBuilder builder) {
        
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            
            Map<String, String> properties = new HashMap<>();
            
            normalAnnotation.getPairs().forEach(pair -> {
                String memberName = pair.getNameAsString();
                
                if (memberName.equals("activationConfig") && 
                    pair.getValue() instanceof ArrayInitializerExpr) {
                    
                    ArrayInitializerExpr array = (ArrayInitializerExpr) pair.getValue();
                    array.getValues().forEach(value -> {
                        if (value instanceof NormalAnnotationExpr) {
                            extractActivationConfigProperty((NormalAnnotationExpr) value, 
                                                          builder, properties);
                        }
                    });
                } else if (memberName.equals("mappedName") && 
                          pair.getValue() instanceof StringLiteralExpr) {
                    String jndiName = ((StringLiteralExpr) pair.getValue()).getValue();
                    builder.jndiName(jndiName);
                }
            });
            
            builder.properties(properties);
        }
    }
    
    private void extractActivationConfigProperty(NormalAnnotationExpr propAnnotation,
                                               Dependencies.JMSDependency.JMSDependencyBuilder builder,
                                               Map<String, String> properties) {
        
        String propertyName = null;
        String propertyValue = null;
        
        for (MemberValuePair pair : propAnnotation.getPairs()) {
            if (pair.getNameAsString().equals("propertyName") && 
                pair.getValue() instanceof StringLiteralExpr) {
                propertyName = ((StringLiteralExpr) pair.getValue()).getValue();
            } else if (pair.getNameAsString().equals("propertyValue") && 
                      pair.getValue() instanceof StringLiteralExpr) {
                propertyValue = ((StringLiteralExpr) pair.getValue()).getValue();
            }
        }
        
        if (propertyName != null && propertyValue != null) {
            properties.put(propertyName, propertyValue);
            
            // Extraire les infos spécifiques
            switch (propertyName) {
                case "destinationType":
                    if (propertyValue.equals("javax.jms.Queue")) {
                        builder.queueName("MDB_QUEUE");
                    } else if (propertyValue.equals("javax.jms.Topic")) {
                        builder.topicName("MDB_TOPIC");
                    }
                    break;
                case "destination":
                    if (properties.get("destinationType") != null && 
                        properties.get("destinationType").contains("Queue")) {
                        builder.queueName(propertyValue);
                    } else {
                        builder.topicName(propertyValue);
                    }
                    break;
                case "connectionFactory":
                    builder.connectionFactory(propertyValue);
                    break;
            }
        }
    }
    
    private class JMSUsageVisitor extends VoidVisitorAdapter<Void> {
        private final Map<String, Dependencies.JMSDependency> dependencyMap;
        private final Path sourceFile;
        
        public JMSUsageVisitor(Map<String, Dependencies.JMSDependency> dependencyMap,
                             Path sourceFile) {
            this.dependencyMap = dependencyMap;
            this.sourceFile = sourceFile;
        }
        
        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            String className = expr.getTypeAsString();
            
            // Détecter les créations de connexions JMS
            if (className.contains("QueueConnection") || className.contains("TopicConnection") ||
                className.contains("Connection") && isJMSContext(expr)) {
                extractJMSConnection(expr);
            }
            
            // Détecter les créations de destinations
            if (className.contains("Queue") || className.contains("Topic")) {
                extractJMSDestination(expr, className);
            }
            
            super.visit(expr, arg);
        }
        
        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            // Détecter les lookups JNDI pour JMS
            if (methodName.equals("lookup") && expr.getScope().isPresent()) {
                String scope = expr.getScope().get().toString();
                
                if (scope.contains("Context") && !expr.getArguments().isEmpty()) {
                    Expression jndiArg = expr.getArgument(0);
                    if (jndiArg instanceof StringLiteralExpr) {
                        String jndiName = ((StringLiteralExpr) jndiArg).getValue();
                        
                        if (isJMSJndiName(jndiName)) {
                            extractJMSFromJNDI(jndiName);
                        }
                    }
                }
            }
            
            // Détecter les envois de messages
            if (methodName.equals("send") || methodName.equals("publish")) {
                extractMessageSend(expr);
            }
            
            // Détecter les réceptions de messages
            if (methodName.equals("receive") || methodName.equals("onMessage")) {
                extractMessageReceive(expr);
            }
            
            // Détecter createQueue/createTopic
            if (methodName.equals("createQueue") || methodName.equals("createTopic")) {
                extractDestinationCreation(expr, methodName);
            }
            
            super.visit(expr, arg);
        }
        
        private boolean isJMSContext(Expression expr) {
            // Heuristique pour déterminer si c'est dans un contexte JMS
            return expr.toString().contains("jms") || expr.toString().contains("JMS") ||
                   expr.toString().contains("javax.jms");
        }
        
        private void extractJMSConnection(ObjectCreationExpr expr) {
            Dependencies.JMSDependency jms = Dependencies.JMSDependency.builder()
                    .connectionFactory("JMS_CONNECTION")
                    .properties(Map.of("sourceFile", sourceFile.toString()))
                    .build();
            
            dependencyMap.put("JMS_CONNECTION_" + sourceFile.getFileName(), jms);
        }
        
        private void extractJMSDestination(ObjectCreationExpr expr, String className) {
            Dependencies.JMSDependency.JMSDependencyBuilder builder = 
                    Dependencies.JMSDependency.builder();
            
            if (className.contains("Queue")) {
                builder.queueName("QUEUE_" + expr.hashCode());
            } else if (className.contains("Topic")) {
                builder.topicName("TOPIC_" + expr.hashCode());
            }
            
            builder.properties(Map.of("sourceFile", sourceFile.toString()));
            
            Dependencies.JMSDependency jms = builder.build();
            String key = jms.getQueueName() != null ? jms.getQueueName() : jms.getTopicName();
            dependencyMap.put(key, jms);
        }
        
        private boolean isJMSJndiName(String jndiName) {
            return jndiName.contains("jms/") || 
                   jndiName.contains("queue/") || 
                   jndiName.contains("topic/") ||
                   jndiName.contains("ConnectionFactory");
        }
        
        private void extractJMSFromJNDI(String jndiName) {
            Dependencies.JMSDependency.JMSDependencyBuilder builder = 
                    Dependencies.JMSDependency.builder();
            
            builder.jndiName(jndiName);
            
            if (jndiName.contains("ConnectionFactory")) {
                builder.connectionFactory(jndiName);
            } else if (jndiName.contains("queue/") || jndiName.contains("Queue")) {
                String queueName = extractDestinationName(jndiName);
                builder.queueName(queueName);
            } else if (jndiName.contains("topic/") || jndiName.contains("Topic")) {
                String topicName = extractDestinationName(jndiName);
                builder.topicName(topicName);
            }
            
            Dependencies.JMSDependency jms = builder.build();
            dependencyMap.put(jndiName, jms);
        }
        
        private String extractDestinationName(String jndiName) {
            if (jndiName.contains("/")) {
                String[] parts = jndiName.split("/");
                return parts[parts.length - 1];
            }
            return jndiName;
        }
        
        private void extractMessageSend(MethodCallExpr expr) {
            // Marquer comme producteur
            String destination = "UNKNOWN_DESTINATION";
            
            if (expr.getScope().isPresent()) {
                destination = expr.getScope().get().toString();
            }
            
            Dependencies.JMSDependency jms = dependencyMap.computeIfAbsent(
                    destination,
                    k -> Dependencies.JMSDependency.builder()
                            .queueName(destination)
                            .build()
            );
            
            jms.setIsProducer(true);
        }
        
        private void extractMessageReceive(MethodCallExpr expr) {
            // Marquer comme consommateur
            String destination = "UNKNOWN_DESTINATION";
            
            if (expr.getScope().isPresent()) {
                destination = expr.getScope().get().toString();
            }
            
            Dependencies.JMSDependency jms = dependencyMap.computeIfAbsent(
                    destination,
                    k -> Dependencies.JMSDependency.builder()
                            .queueName(destination)
                            .build()
            );
            
            jms.setIsConsumer(true);
        }
        
        private void extractDestinationCreation(MethodCallExpr expr, String methodName) {
            if (!expr.getArguments().isEmpty() && 
                expr.getArgument(0) instanceof StringLiteralExpr) {
                
                String destinationName = ((StringLiteralExpr) expr.getArgument(0)).getValue();
                
                Dependencies.JMSDependency.JMSDependencyBuilder builder = 
                        Dependencies.JMSDependency.builder();
                
                if (methodName.equals("createQueue")) {
                    builder.queueName(destinationName);
                } else {
                    builder.topicName(destinationName);
                }
                
                Dependencies.JMSDependency jms = builder.build();
                dependencyMap.put(destinationName, jms);
            }
        }
    }
    
    private void scanEJBDescriptors(Path path, Map<String, Dependencies.JMSDependency> dependencyMap) {
        Path ejbJarXml = path.resolve("META-INF/ejb-jar.xml");
        if (Files.exists(ejbJarXml)) {
            try {
                // TODO: Parser ejb-jar.xml pour les MDB
                log.debug("Analyse de ejb-jar.xml pour JMS");
            } catch (Exception e) {
                log.error("Erreur lors du parsing de ejb-jar.xml", e);
            }
        }
    }
    
    private void scanSpringConfigs(Path path, Map<String, Dependencies.JMSDependency> dependencyMap) 
            throws IOException {
        
        // Scanner les fichiers de configuration Spring
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(p -> p.toString().endsWith(".xml") && 
                             (p.toString().contains("spring") || p.toString().contains("context")))
                 .forEach(configFile -> {
                     try {
                         extractFromSpringConfig(configFile, dependencyMap);
                     } catch (Exception e) {
                         log.error("Erreur lors de l'analyse de la config Spring: {}", configFile, e);
                     }
                 });
        }
    }
    
    private void extractFromSpringConfig(Path configFile, 
                                       Map<String, Dependencies.JMSDependency> dependencyMap) 
            throws IOException {
        
        String content = Files.readString(configFile);
        
        // Rechercher les beans JMS
        if (content.contains("jms:") || content.contains("JmsTemplate") || 
            content.contains("MessageListenerContainer")) {
            
            log.debug("Configuration JMS détectée dans: {}", configFile);
            
            // Extraire basique - en réalité il faudrait parser le XML
            Dependencies.JMSDependency jms = Dependencies.JMSDependency.builder()
                    .connectionFactory("SPRING_JMS")
                    .properties(Map.of("configFile", configFile.toString()))
                    .build();
            
            dependencyMap.put("SPRING_JMS_" + configFile.getFileName(), jms);
        }
    }
}
// Fichier: src/main/java/com/votre_entreprise/analyzer/spoon/endpoint/SpringEndpointFinder.java
package com.votre_entreprise.analyzer.spoon.endpoint;

import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.AnnotationFilter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SpringEndpointFinder implements EndpointFinder {

    private final Factory factory;
    private final Map<CtMethod<?>, String> pathCache = new ConcurrentHashMap<>();
    private final Map<CtMethod<?>, String> httpMethodCache = new ConcurrentHashMap<>();
    private static final List<String> MAPPING_ANNOTATIONS = Arrays.asList(
        "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping"
    );

    public SpringEndpointFinder(Launcher spoonLauncher) {
        this.factory = spoonLauncher.getFactory();
    }

    @Override
    public List<CtMethod<?>> findEndpoints() {
        List<CtMethod<?>> allEndpoints = new ArrayList<>();
        // Chercher toutes les annotations de mapping
        for (String annotationName : MAPPING_ANNOTATIONS) {
            try {
                // On utilise le class loader pour trouver la vraie classe d'annotation
                String fullAnnotationName = "org.springframework.web.bind.annotation." + annotationName;
                Class<? extends Annotation> annotationClass = (Class<? extends Annotation>) Class.forName(fullAnnotationName);
                
                List<CtMethod<?>> methods = factory.getModel().getElements(new AnnotationFilter<>(annotationClass));
                
                for(CtMethod<?> method : methods) {
                    Annotation actualAnnotation = method.getAnnotation(annotationClass);
                    cacheEndpointInfo(method, actualAnnotation);
                }
                allEndpoints.addAll(methods);
            } catch (ClassNotFoundException e) {
                // Ignore si une annotation n'est pas trouvée (par exemple, si Spring n'est pas une dépendance)
            }
        }
        return allEndpoints;
    }

    private void cacheEndpointInfo(CtMethod<?> method, Annotation annotation) {
        try {
            // Récupère le chemin de l'annotation de la méthode
            Method valueMethod = annotation.annotationType().getMethod("value");
            String[] paths = (String[]) valueMethod.invoke(annotation);
            String methodPath = (paths.length > 0) ? paths[0] : "";

            // Récupère le chemin de base défini sur la classe, s'il existe
            String basePath = "";
            CtType<?> declaringType = method.getDeclaringType();
            if (declaringType != null) {
                // On utilise Class.forName() pour obtenir l'objet Class de RequestMapping
                Class<? extends Annotation> requestMappingClass = (Class<? extends Annotation>) Class.forName("org.springframework.web.bind.annotation.RequestMapping");
                
                // On vérifie si la classe a cette annotation
                if (declaringType.hasAnnotation(requestMappingClass)) {
                    Annotation classAnnotation = declaringType.getAnnotation(requestMappingClass);
                    Method classValueMethod = classAnnotation.annotationType().getMethod("value");
                    String[] basePaths = (String[]) classValueMethod.invoke(classAnnotation);
                    if (basePaths.length > 0) {
                        basePath = basePaths[0];
                    }
                }
            }
            
            // Combine le chemin de base et le chemin de la méthode
            String fullPath = basePath + methodPath;
            // Assure un path propre (pas de double slash)
            String cleanedPath = fullPath.replaceAll("//+", "/");

            pathCache.put(method, cleanedPath.isEmpty() ? "/" : cleanedPath);
            httpMethodCache.put(method, annotation.annotationType().getSimpleName().replace("Mapping", "").toUpperCase());

        } catch (Exception e) {
            pathCache.put(method, "/");
            httpMethodCache.put(method, "UNKNOWN");
        }
    }

    @Override
    public String getPathFor(CtMethod<?> method) {
        return pathCache.getOrDefault(method, "N/A");
    }

    @Override
    public String getHttpMethodFor(CtMethod<?> method) {
        return httpMethodCache.getOrDefault(method, "N/A");
    }
}

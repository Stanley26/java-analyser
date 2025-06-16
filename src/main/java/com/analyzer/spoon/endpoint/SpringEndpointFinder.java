// Fichier: src/main/java/com/votre_entreprise/analyzer/spoon/endpoint/SpringEndpointFinder.java
package com.votre_entreprise.analyzer.spoon.endpoint;

import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
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
                String fullAnnotationName = "org.springframework.web.bind.annotation." + annotationName;
                Class<? extends Annotation> annotationClass = (Class<? extends Annotation>) Class.forName(fullAnnotationName);
                
                List<CtMethod<?>> methods = factory.getModel().getElements(new AnnotationFilter<>(annotationClass));
                
                for(CtMethod<?> method : methods) {
                    Annotation actualAnnotation = method.getAnnotation(annotationClass);
                    cacheEndpointInfo(method, actualAnnotation);
                }
                allEndpoints.addAll(methods);
            } catch (ClassNotFoundException e) {
                // Ignore si une annotation n'est pas trouvée
            }
        }
        return allEndpoints;
    }

    private void cacheEndpointInfo(CtMethod<?> method, Annotation annotation) {
        try {
            Method valueMethod = annotation.annotationType().getMethod("value");
            String[] paths = (String[]) valueMethod.invoke(annotation);
            String path = (paths.length > 0) ? paths[0] : "/";
            
            String basePath = "";
            CtTypeReference<?> requestMappingRef = factory.Type().createReference("org.springframework.web.bind.annotation.RequestMapping");
            if(method.getDeclaringType() != null && method.getDeclaringType().getAnnotation(requestMappingRef) != null) {
                 Annotation classAnnotation = method.getDeclaringType().getAnnotation(requestMappingRef).getActualAnnotation();
                 String[] basePaths = (String[]) classAnnotation.annotationType().getMethod("value").invoke(classAnnotation);
                 basePath = (basePaths.length > 0) ? basePaths[0] : "";
            }
            
            // Assure un path propre (pas de double slash)
            pathCache.put(method, (basePath + path).replaceAll("//", "/"));
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

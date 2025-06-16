// Fichier: src/main/java/com/votre_entreprise/analyzer/spoon/DependencyAnalyzer.java
package com.votre_entreprise.analyzer.spoon;

import com.votre_entreprise.analyzer.model.Dependency;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.code.CtInvocation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DependencyAnalyzer {
    
    public List<Dependency> analyze(CtMethod<?> startMethod) {
        List<Dependency> dependencies = new ArrayList<>();
        Set<CtMethod<?>> visitedMethods = new HashSet<>();
        
        // On lance le scanner récursif
        recursiveScan(startMethod, dependencies, visitedMethods, 0);
        
        return dependencies;
    }

    private void recursiveScan(CtMethod<?> method, List<Dependency> dependencies, Set<CtMethod<?>> visitedMethods, int depth) {
        if (method == null || visitedMethods.contains(method) || depth > 10) { // Limite de profondeur pour éviter les boucles infinies
            return;
        }
        
        visitedMethods.add(method);

        CtScanner scanner = new CtScanner() {
            @Override
            public <T> void visitCtInvocation(CtInvocation<T> invocation) {
                super.visitCtInvocation(invocation);

                CtExecutableReference<T> execRef = invocation.getExecutable();
                CtMethod<T> calledMethod = execRef.getDeclaration();

                if (calledMethod != null && calledMethod.getDeclaringType() != null) {
                    CtType<?> declaringType = calledMethod.getDeclaringType();
                    // Ignore les appels aux classes de base Java pour garder le rapport concis
                    if (declaringType.getQualifiedName().startsWith("java.")) {
                        return;
                    }

                    dependencies.add(new Dependency(
                        getDependencyType(declaringType),
                        declaringType.getQualifiedName(),
                        calledMethod.getSignature(),
                        depth + 1
                    ));
                    
                    // Appel récursif pour explorer plus profondément
                    recursiveScan(calledMethod, dependencies, visitedMethods, depth + 1);
                }
            }
        };

        scanner.scan(method.getBody());
    }

    private String getDependencyType(CtType<?> type) {
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            String annotationName = annotation.getAnnotationType().getSimpleName();
            if (annotationName.contains("Service")) return "Service";
            if (annotationName.contains("Repository")) return "Repository";
            if (annotationName.contains("Component")) return "Component";
            if (annotationName.contains("RestController")) return "Controller";
        }
        // Heuristique simple basée sur le nom si aucune annotation n'est trouvée
        String className = type.getSimpleName();
        if (className.endsWith("Service")) return "Service";
        if (className.endsWith("Repository")) return "Repository";
        if (className.endsWith("Controller")) return "Controller";
        if (className.endsWith("Client")) return "External API";
        
        return "Component";
    }
}

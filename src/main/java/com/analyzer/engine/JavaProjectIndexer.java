package com.analyzer.engine;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Indexe toutes les déclarations de méthodes d'un projet pour une résolution rapide.
 * Crée une carte de "nom_complet_classe.signature_methode" -> MethodDeclaration.
 */
public class JavaProjectIndexer {

    private final Map<String, MethodDeclaration> methodIndex = new HashMap<>();
    private final Map<String, TypeDeclaration<?>> typeIndex = new HashMap<>();


    public void indexProject(Path projectRoot) {
        Path sourceRoot = projectRoot.resolve("src/main/java");
        if (!Files.exists(sourceRoot)) return;

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                  .forEach(this::indexFile);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'indexation du projet : " + e.getMessage());
        }
    }

    private void indexFile(Path javaFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            for (TypeDeclaration<?> type : cu.getTypes()) {
                String className = type.getFullyQualifiedName().orElse(type.getNameAsString());
                typeIndex.put(className, type);

                for (MethodDeclaration method : type.getMethods()) {
                    String methodKey = className + "." + method.getSignature().asString();
                    methodIndex.put(methodKey, method);
                }
            }
        } catch (IOException e) {
            // Ignorer les fichiers qui ne peuvent pas être parsés
        }
    }

    public MethodDeclaration getMethod(String className, String methodSignature) {
        return methodIndex.get(className + "." + methodSignature);
    }
    
    public TypeDeclaration<?> getType(String className) {
        return typeIndex.get(className);
    }
}

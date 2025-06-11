package com.analyzer.engine;

import com.analyzer.model.technical.EndpointDetails;
import com.analyzer.parsers.common.DependencyParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Résout le graphe d'appels à partir d'une méthode de point d'entrée
 * et analyse chaque méthode visitée pour y trouver des dépendances.
 */
public class CallGraphResolver {

    private final List<DependencyParser> dependencyParsers;
    private final JavaProjectIndexer indexer;

    public CallGraphResolver(List<DependencyParser> dependencyParsers, JavaProjectIndexer indexer, File projectDir) {
        this.dependencyParsers = dependencyParsers;
        this.indexer = indexer;
        
        // Configuration du Symbol Solver de JavaParser pour résoudre les types et les appels de méthode
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(new File(projectDir, "src/main/java")));
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        StaticJavaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
    }

    public void resolveAndAnalyze(MethodDeclaration startMethod, EndpointDetails endpointDetails) {
        resolveRecursively(startMethod, endpointDetails, new HashSet<>());
    }

    private void resolveRecursively(MethodDeclaration currentMethod, EndpointDetails endpointDetails, Set<String> visitedMethods) {
        String methodSignature = currentMethod.getSignature().asString();
        String className = currentMethod.findAncestor(TypeDeclaration.class)
                .flatMap(TypeDeclaration::getFullyQualifiedName).orElse("");
        String methodKey = className + "." + methodSignature;

        if (visitedMethods.contains(methodKey)) {
            return; // Éviter les boucles infinies en cas de récursion
        }
        visitedMethods.add(methodKey);
        
        // Étape 1: Analyser la méthode actuelle pour les dépendances directes
        for (DependencyParser parser : dependencyParsers) {
            endpointDetails.externalCalls.addAll(parser.findDependencies(currentMethod, currentMethod.findAncestor(TypeDeclaration.class).get()));
        }

        // Étape 2: Trouver tous les appels de méthode et continuer l'exploration
        currentMethod.findAll(MethodCallExpr.class).forEach(call -> {
            try {
                ResolvedMethodDeclaration resolvedMethod = call.resolve();
                String targetClassName = resolvedMethod.getQualifiedName().replace("." + resolvedMethod.getName(), "");
                String targetMethodSignature = resolvedMethod.getSignature();
                
                // Vérifier si la méthode appelée fait partie de notre code (et non d'une librairie externe)
                MethodDeclaration nextMethod = indexer.getMethod(targetClassName, targetMethodSignature);
                if (nextMethod != null) {
                    endpointDetails.internalCalls.add(targetClassName + "." + targetMethodSignature);
                    resolveRecursively(nextMethod, endpointDetails, visitedMethods);
                }
            } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
                // Ignorer les appels qui ne peuvent pas être résolus (librairies externes, etc.)
            }
        });
    }
}

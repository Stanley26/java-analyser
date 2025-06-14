package com.analyzer.engine;

import com.analyzer.model.technical.EndpointDetails;
import com.analyzer.parsers.common.DependencyParser;
import com.analyzer.parsers.security.SecurityParser;
import com.github.javaparser.StaticJavaParser;
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
import java.util.Optional;
import java.util.Set;

/**
 * Résout le graphe d'appels à partir d'une méthode de point d'entrée.
 * C'est le cœur de l'analyse en profondeur, capable de naviguer à travers
 * les couches de l'application pour y trouver des dépendances.
 */
public class CallGraphResolver {

    private final List<DependencyParser> dependencyParsers;
    private final SecurityParser securityParser;
    private final JavaProjectIndexer indexer;

    /**
     * Construit le résolveur de graphe d'appels.
     * @param dependencyParsers La liste des parseurs de dépendances (JDBC, EJB, etc.).
     * @param securityParser Le parseur pour les règles de sécurité.
     * @param indexer L'index du projet pour une résolution rapide des méthodes.
     * @param projectDir Le répertoire du projet en cours d'analyse.
     */
    public CallGraphResolver(List<DependencyParser> dependencyParsers, SecurityParser securityParser, JavaProjectIndexer indexer, File projectDir) {
        this.dependencyParsers = dependencyParsers;
        this.securityParser = securityParser;
        this.indexer = indexer;

        // --- Configuration du Symbol Solver de JavaParser ---
        // Le Symbol Solver est essentiel pour comprendre les types et les appels de méthode.
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        
        // Ajoute la capacité de résoudre les types du JDK (ex: java.lang.String).
        typeSolver.add(new ReflectionTypeSolver());
        
        // Ajoute la capacité de résoudre les types définis dans le code source du projet.
        File sourceDir = new File(projectDir, "src/main/java");
        if (sourceDir.exists()) {
             typeSolver.add(new JavaParserTypeSolver(sourceDir));
        }
        
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        StaticJavaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
    }

    /**
     * Point d'entrée pour lancer l'analyse en profondeur à partir d'une méthode de départ.
     * @param startMethod La méthode de l'endpoint.
     * @param endpointDetails L'objet qui stockera les résultats de l'analyse.
     */
    public void resolveAndAnalyze(MethodDeclaration startMethod, EndpointDetails endpointDetails) {
        resolveRecursively(startMethod, endpointDetails, new HashSet<>());
    }

    /**
     * Méthode récursive qui explore le graphe d'appels.
     */
    private void resolveRecursively(MethodDeclaration currentMethod, EndpointDetails endpointDetails, Set<String> visitedMethods) {
        Optional<TypeDeclaration<?>> enclosingClassOpt = currentMethod.findAncestor(TypeDeclaration.class);
        if (enclosingClassOpt.isEmpty()) {
            return; // Impossible de trouver la classe parente.
        }
        TypeDeclaration<?> enclosingClass = enclosingClassOpt.get();

        String className = enclosingClass.getFullyQualifiedName().orElse("");
        if (className.isEmpty()) {
            return;
        }

        String methodSignature = currentMethod.getSignature().asString();
        String methodKey = className + "." + methodSignature;

        if (visitedMethods.contains(methodKey)) {
            return; // Cycle détecté.
        }
        visitedMethods.add(methodKey);

        // Étape 1 : Analyser la méthode actuelle pour les règles de sécurité.
        endpointDetails.securityRules.addAll(securityParser.findSecurityRules(currentMethod));
        
        // Étape 2 : Analyser la méthode pour les dépendances externes (JDBC, EJB).
        for (DependencyParser parser : dependencyParsers) {
            endpointDetails.externalCalls.addAll(parser.findDependencies(currentMethod, enclosingClass));
        }

        // Étape 3 : Trouver les appels internes et continuer l'exploration.
        currentMethod.findAll(MethodCallExpr.class).forEach(call -> {
            try {
                ResolvedMethodDeclaration resolvedMethod = call.resolve();
                String targetClassName = resolvedMethod.getQualifiedName().replace("." + resolvedMethod.getName(), "");
                String targetMethodSignature = resolvedMethod.getSignature();
                
                MethodDeclaration nextMethod = indexer.getMethod(targetClassName, targetMethodSignature);
                if (nextMethod != null) {
                    endpointDetails.internalCalls.add(targetClassName + "." + targetMethodSignature);
                    resolveRecursively(nextMethod, endpointDetails, visitedMethods);
                }
            } catch (UnsolvedSymbolException | UnsupportedOperationException | InternalError e) {
                // Ignorer les appels qui ne peuvent pas être résolus. C'est normal.
            }
        });
    }
}

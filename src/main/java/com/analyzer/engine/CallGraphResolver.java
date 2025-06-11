package com.analyzer.engine;

import com.analyzer.model.technical.EndpointDetails;
import com.analyzer.parsers.common.DependencyParser;
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
import java.util.Set;

/**
 * Résout le graphe d'appels à partir d'une méthode de point d'entrée
 * et analyse chaque méthode visitée pour y trouver des dépendances.
 * C'est le cœur de l'analyse en profondeur.
 */
public class CallGraphResolver {

    private final List<DependencyParser> dependencyParsers;
    private final JavaProjectIndexer indexer;

    /**
     * Construit un nouveau résolveur de graphe d'appels.
     * La configuration du Symbol Solver est cruciale ici. Il permet à JavaParser
     * de comprendre les types et de lier les appels de méthode à leurs déclarations.
     *
     * @param dependencyParsers La liste des parseurs de dépendances (JDBC, EJB, etc.).
     * @param indexer L'index pré-calculé du projet pour une recherche rapide des méthodes.
     * @param projectDir Le répertoire du projet en cours d'analyse.
     */
    public CallGraphResolver(List<DependencyParser> dependencyParsers, JavaProjectIndexer indexer, File projectDir) {
        this.dependencyParsers = dependencyParsers;
        this.indexer = indexer;

        // Configuration du Symbol Solver de JavaParser. C'est ce qui rend l'analyse fiable.
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        // Le ReflectionTypeSolver peut résoudre les types du JDK (ex: java.lang.String).
        typeSolver.add(new ReflectionTypeSolver());
        // Le JavaParserTypeSolver peut résoudre les types définis dans notre propre code source.
        File sourceDir = new File(projectDir, "src/main/java");
        if (sourceDir.exists()) {
             typeSolver.add(new JavaParserTypeSolver(sourceDir));
        }
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        StaticJavaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
    }

    /**
     * Point d'entrée pour lancer l'analyse en profondeur à partir d'une méthode de départ.
     * @param startMethod La méthode de l'endpoint (ex: la méthode annotée avec @GetMapping).
     * @param endpointDetails L'objet qui stockera les résultats de l'analyse.
     */
    public void resolveAndAnalyze(MethodDeclaration startMethod, EndpointDetails endpointDetails) {
        resolveRecursively(startMethod, endpointDetails, new HashSet<>());
    }

    /**
     * Méthode récursive qui explore le graphe d'appels.
     * @param currentMethod La méthode en cours d'analyse.
     * @param endpointDetails L'objet de rapport à enrichir.
     * @param visitedMethods Un ensemble pour garder la trace des méthodes déjà visitées et éviter les boucles infinies.
     */
    private void resolveRecursively(MethodDeclaration currentMethod, EndpointDetails endpointDetails, Set<String> visitedMethods) {
        String methodSignature = currentMethod.getSignature().asString();
        String className = currentMethod.findAncestor(TypeDeclaration.class)
                .flatMap(TypeDeclaration::getFullyQualifiedName)
                .orElse(""); // Fournir une chaîne vide si le nom n'est pas trouvé.
        String methodKey = className + "." + methodSignature;

        if (visitedMethods.contains(methodKey)) {
            return; // Cycle détecté, on arrête l'exploration de cette branche.
        }
        visitedMethods.add(methodKey);

        // Étape 1: Analyser la méthode actuelle pour y trouver des dépendances directes (JDBC, EJB...).
        currentMethod.findAncestor(TypeDeclaration.class).ifPresent(enclosingClass -> {
            for (DependencyParser parser : dependencyParsers) {
                endpointDetails.externalCalls.addAll(parser.findDependencies(currentMethod, enclosingClass));
            }
        });

        // Étape 2: Trouver tous les appels de méthode dans le corps de la méthode actuelle.
        currentMethod.findAll(MethodCallExpr.class).forEach(call -> {
            try {
                // Tente de "résoudre" l'appel pour trouver la déclaration exacte de la méthode appelée.
                ResolvedMethodDeclaration resolvedMethod = call.resolve();
                String targetClassName = resolvedMethod.getQualifiedName().replace("." + resolvedMethod.getName(), "");
                String targetMethodSignature = resolvedMethod.getSignature();

                // Vérifier si la méthode appelée fait partie de notre code (grâce à l'index)
                // et n'est pas une méthode d'une librairie externe (ex: java.util.List.add).
                MethodDeclaration nextMethod = indexer.getMethod(targetClassName, targetMethodSignature);
                if (nextMethod != null) {
                    // La méthode fait partie de notre projet, on l'ajoute au rapport et on continue l'exploration.
                    endpointDetails.internalCalls.add(targetClassName + "." + targetMethodSignature);
                    resolveRecursively(nextMethod, endpointDetails, visitedMethods);
                }
            } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
                // C'est normal d'avoir des erreurs de résolution pour les librairies externes
                // ou pour du code trop dynamique. On ignore ces cas et on continue l'analyse.
            }
        });
    }
}

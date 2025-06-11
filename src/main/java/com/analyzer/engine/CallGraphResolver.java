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
import java.util.Optional;

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

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
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
        // --- BLOC DE CODE CORRIGÉ ---

        // 1. Obtenir la signature de la méthode actuelle.
        String methodSignature = currentMethod.getSignature().asString();
        
        // 2. Trouver le nom complet de la classe contenant la méthode.
        // On s'assure que la chaîne de Optional est correctement résolue en une String.
        String className = currentMethod.findAncestor(TypeDeclaration.class)
                .flatMap(TypeDeclaration::getFullyQualifiedName)
                .orElse(""); // Si le nom n'est pas trouvé, on retourne une chaîne vide.

        // Si le nom de la classe est vide, on ne peut pas continuer.
        if (className.isEmpty()) {
            return;
        }

        // 3. Créer une clé unique pour cette méthode afin d'éviter les cycles.
        String methodKey = className + "." + methodSignature;

        if (visitedMethods.contains(methodKey)) {
            return; // Cycle détecté, on arrête l'exploration de cette branche.
        }
        visitedMethods.add(methodKey);

        // --- FIN DU BLOC CORRIGÉ ---

        // Étape 1 : Analyser la méthode actuelle pour les dépendances directes
        Optional<TypeDeclaration<?>> enclosingClassOpt = currentMethod.findAncestor(TypeDeclaration.class);
        if (enclosingClassOpt.isPresent()) {
            TypeDeclaration<?> enclosingClass = enclosingClassOpt.get();
            for (DependencyParser parser : dependencyParsers) {
                endpointDetails.externalCalls.addAll(parser.findDependencies(currentMethod, enclosingClass));
            }
        }

        // Étape 2 : Trouver tous les appels de méthode dans le corps de la méthode actuelle.
        currentMethod.findAll(MethodCallExpr.class).forEach(call -> {
            try {
                // Tente de "résoudre" l'appel pour trouver la déclaration exacte de la méthode appelée.
                ResolvedMethodDeclaration resolvedMethod = call.resolve();
                String targetClassName = resolvedMethod.getQualifiedName().replace("." + resolvedMethod.getName(), "");
                String targetMethodSignature = resolvedMethod.getSignature();

                // Vérifier si la méthode appelée fait partie de notre code (grâce à l'index)
                MethodDeclaration nextMethod = indexer.getMethod(targetClassName, targetMethodSignature);
                if (nextMethod != null) {
                    // La méthode fait partie de notre projet, on l'ajoute au rapport et on continue l'exploration.
                    endpointDetails.internalCalls.add(targetClassName + "." + targetMethodSignature);
                    resolveRecursively(nextMethod, endpointDetails, visitedMethods);
                }
            } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
                // Ignorer les appels qui ne peuvent pas être résolus (librairies externes, etc.)
            }
        });
    }
}

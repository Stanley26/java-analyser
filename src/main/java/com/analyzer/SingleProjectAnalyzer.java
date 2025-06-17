// Fichier: src/main/java/com/votre_entreprise/analyzer/SingleProjectAnalyzer.java
package com.votre_entreprise.analyzer;

import com.votre_entreprise.analyzer.model.AnalyzedEndpoint;
import com.votre_entreprise.analyzer.model.BusinessRule;
import com.votre_entreprise.analyzer.model.Dependency;
import com.votre_entreprise.analyzer.spoon.DependencyAnalyzer;
import com.votre_entreprise.analyzer.spoon.FrameworkDetector;
import com.votre_entreprise.analyzer.spoon.endpoint.EndpointFinder;
import com.votre_entreprise.analyzer.spoon.endpoint.SpringEndpointFinder;
import com.votre_entreprise.analyzer.spoon.endpoint.StrutsEndpointFinder;

import spoon.Launcher;
import spoon.compiler.SpoonResource; // Import nécessaire pour le Predicate
import spoon.reflect.declaration.CtMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate; // Import de l'interface Predicate

public class SingleProjectAnalyzer {

    private final String projectPath;

    public SingleProjectAnalyzer(String projectPath) {
        this.projectPath = projectPath;
    }

    public List<AnalyzedEndpoint> analyze() throws Exception {
        System.out.println("   - Initialisation de Spoon pour le projet Maven : " + projectPath);
        Launcher spoonLauncher = new Launcher();

        // Étape 1 : On donne le chemin racine du projet Maven.
        // Spoon va l'utiliser pour trouver les pom.xml, comprendre les modules
        // et construire le classpath complet (liens inter-modules + dépendances externes).
        spoonLauncher.addInputResource(projectPath);

        // Étape 2 : On applique un filtre pour exclure les fichiers de test.
        spoonLauncher.getEnvironment().setInputFilter(new Predicate<SpoonResource>() {
            @Override
            public boolean test(SpoonResource resource) {
                // On ne veut analyser que les fichiers, pas les répertoires.
                if (!resource.isFile()) {
                    return true; // On laisse Spoon parcourir les répertoires.
                }

                String path = resource.getPath().replace('\\', '/');
                
                // La règle : on inclut le fichier SEULEMENT s'il se termine par .java
                // ET que son chemin ne contient PAS de segment '/test/'.
                boolean isJavaFile = path.endsWith(".java");
                boolean isTestFile = path.contains("/test/");

                return isJavaFile && !isTestFile;
            }
        });

        // Configuration du reste de l'environnement de Spoon
        spoonLauncher.getEnvironment().setIgnoreSyntaxErrors(true);
        spoonLauncher.getEnvironment().setComplianceLevel(8);
        spoonLauncher.getEnvironment().setNoClasspath(false);

        System.out.println("   - Construction du modèle de code (cela peut prendre un moment)...");
        spoonLauncher.buildModel();
        System.out.println("   - Modèle construit.");

        System.out.println("   - Détection du framework...");
        FrameworkDetector.FrameworkType framework = FrameworkDetector.detect(projectPath);
        System.out.println("   - Framework détecté : " + framework);

        EndpointFinder finder;
        if (framework == FrameworkDetector.FrameworkType.SPRING) {
            finder = new SpringEndpointFinder(spoonLauncher);
        } else {
            finder = new StrutsEndpointFinder(spoonLauncher, projectPath);
        }

        List<CtMethod<?>> entryPointMethods = finder.findEndpoints();
        System.out.println("   - " + entryPointMethods.size() + " endpoints de production trouvés.");

        List<AnalyzedEndpoint> results = new ArrayList<>();
        DependencyAnalyzer dependencyAnalyzer = new DependencyAnalyzer();

        for (CtMethod<?> method : entryPointMethods) {
            System.out.println("     -> Analyse de l'endpoint : " + method.getSignature());
            List<Dependency> dependencies = dependencyAnalyzer.analyze(method);
            
            List<BusinessRule> businessRules = new ArrayList<>();

            results.add(new AnalyzedEndpoint(
                finder.getPathFor(method),
                finder.getHttpMethodFor(method),
                framework.toString(),
                method.getSignature(),
                dependencies,
                businessRules
            ));
        }

        return results;
    }
}

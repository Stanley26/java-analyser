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
import spoon.compiler.SpoonFileFilter;
import spoon.reflect.declaration.CtMethod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SingleProjectAnalyzer {

    private final String projectPath;

    public SingleProjectAnalyzer(String projectPath) {
        this.projectPath = projectPath;
    }

    public List<AnalyzedEndpoint> analyze() throws Exception {
        System.out.println("   - Initialisation de Spoon pour le projet Maven : " + projectPath);
        Launcher spoonLauncher = new Launcher();

        // On donne simplement le chemin racine du projet Maven à Spoon.
        spoonLauncher.addInputResource(projectPath);

        spoonLauncher.getEnvironment().setInputFilter(new SpoonFileFilter() {
            @Override
            public boolean accept(File file) {
                String path = file.getAbsolutePath().replace('\\', '/');
                return path.endsWith(".java") && !path.contains("/test/");
            }
        });
        spoonLauncher.getEnvironment().setIgnoreSyntaxErrors(true);
        spoonLauncher.getEnvironment().setComplianceLevel(8);
        spoonLauncher.getEnvironment().setNoClasspath(false);
        spoonLauncher.buildModel();

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

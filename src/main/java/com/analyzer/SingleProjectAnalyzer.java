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

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SingleProjectAnalyzer {

    private final String projectPath;

    public SingleProjectAnalyzer(String projectPath) {
        this.projectPath = projectPath;
    }

    public List<AnalyzedEndpoint> analyze() throws Exception {
        System.out.println("   - Initialisation de Spoon pour le projet multi-module : " + projectPath);
        Launcher spoonLauncher = new Launcher();

        // On lit le pom.xml racine pour trouver tous les sous-modules.
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        Model rootModel = mavenReader.read(new FileReader(Paths.get(projectPath, "pom.xml").toFile()));
        List<String> modules = rootModel.getModules();
        
        System.out.println("   - Modules trouvés : " + modules);

        boolean sourceAdded = false;
        // On ajoute le code source de chaque module comme une ressource d'entrée pour Spoon.
        for (String moduleName : modules) {
            Path modulePath = Paths.get(projectPath, moduleName);
            Path mainJavaPath = modulePath.resolve("src").resolve("main").resolve("java");
            
            if (Files.exists(mainJavaPath) && Files.isDirectory(mainJavaPath)) {
                System.out.println("     -> Ajout du répertoire source : " + mainJavaPath);
                spoonLauncher.addInputResource(mainJavaPath.toString());
                sourceAdded = true;
            }
        }

        if (!sourceAdded) {
            System.err.println("Avertissement : Aucun répertoire 'src/main/java' trouvé dans les modules listés. L'analyse risque d'être vide.");
        }

        // Configuration de l'environnement de Spoon
        spoonLauncher.getEnvironment().setIgnoreSyntaxErrors(true);
        spoonLauncher.getEnvironment().setComplianceLevel(8);
        spoonLauncher.getEnvironment().setNoClasspath(false); // Indique à Spoon de construire son propre classpath

        // Construit le modèle avec toutes les sources des modules
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
        System.out.println("   - " + entryPointMethods.size() + " endpoints trouvés.");

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

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

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SingleProjectAnalyzer {

    private final String projectPath;

    public SingleProjectAnalyzer(String projectPath) {
        this.projectPath = projectPath;
    }

    public List<AnalyzedEndpoint> analyze() throws Exception {
        System.out.println("   - Initialisation de Spoon pour le projet Maven : " + projectPath);
        Launcher spoonLauncher = new Launcher();

        // --- DÉBUT DE LA CORRECTION MAJEURE ---
        // Étape 1: Lire le classpath généré par la commande 'mvn dependency:build-classpath'
        Path classpathFilePath = Paths.get(projectPath, "classpath.txt");
        if (!Files.exists(classpathFilePath)) {
            throw new IOException("Le fichier classpath.txt est introuvable. Veuillez l'exécuter 'mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt' sur le projet cible d'abord.");
        }
        String classpath = Files.readString(classpathFilePath);
        spoonLauncher.getEnvironment().setSourceClasspath(classpath.split(File.pathSeparator));
        System.out.println("   - Classpath chargé depuis classpath.txt.");

        // Étape 2: Trouver et ajouter les répertoires de code source de production
        List<String> productionSourceRoots = findProductionSourceRoots(projectPath);
        if (productionSourceRoots.isEmpty()) {
            System.err.println("Avertissement : Aucun répertoire 'src/main/java' trouvé. L'analyse sera vide.");
            return new ArrayList<>();
        }
        productionSourceRoots.forEach(spoonLauncher::addInputResource);
        // --- FIN DE LA CORRECTION MAJEURE ---

        // Configuration du reste de l'environnement de Spoon
        spoonLauncher.getEnvironment().setIgnoreSyntaxErrors(true);
        spoonLauncher.getEnvironment().setComplianceLevel(8);
        
        // On a fourni le classpath manuellement, donc pas besoin de 'noClasspath(true)'
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

    private List<String> findProductionSourceRoots(String projectRootPath) throws Exception {
        List<String> sourceRoots = new ArrayList<>();
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        Model rootModel = mavenReader.read(new FileReader(Paths.get(projectRootPath, "pom.xml").toFile()));
        
        List<String> modules = new ArrayList<>(rootModel.getModules());
        // Ajoute le projet racine lui-même, au cas où il contiendrait du code
        modules.add("."); 

        for (String moduleName : modules) {
            Path mainJavaPath = Paths.get(projectRootPath, moduleName, "src", "main", "java");
            if (Files.exists(mainJavaPath) && Files.isDirectory(mainJavaPath)) {
                System.out.println("     -> Répertoire source de production trouvé : " + mainJavaPath);
                sourceRoots.add(mainJavaPath.toString());
            }
        }
        return sourceRoots;
    }
}

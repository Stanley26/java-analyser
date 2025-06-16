// Fichier: src/main/java/com/votre_entreprise/analyzer/spoon/FrameworkDetector.java
package com.votre_entreprise.analyzer.spoon;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import java.io.FileReader;
import java.nio.file.Paths;

public class FrameworkDetector {
    public enum FrameworkType { SPRING, STRUTS, UNKNOWN }

    public static FrameworkType detect(String projectPath) {
        try (FileReader reader = new FileReader(Paths.get(projectPath, "pom.xml").toFile())) {
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            Model model = mavenReader.read(reader);
            
            for (org.apache.maven.model.Dependency dep : model.getDependencies()) {
                if (dep.getGroupId().contains("org.springframework.boot") && dep.getArtifactId().contains("spring-boot-starter-web")) {
                    return FrameworkType.SPRING;
                }
                if (dep.getGroupId().contains("org.apache.struts") && dep.getArtifactId().contains("struts2-core")) {
                    return FrameworkType.STRUTS;
                }
            }
        } catch (Exception e) {
            System.err.println("Avertissement: Impossible de lire le pom.xml pour la détection de framework. " + e.getMessage());
        }
        return FrameworkType.UNKNOWN;
    }
}

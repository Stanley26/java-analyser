// Fichier: src/main/java/com/votre_entreprise/analyzer/discovery/ProjectDiscoverer.java
package com.votre_entreprise.analyzer.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class ProjectDiscoverer {
    public static List<Path> findMavenProjects(String rootDirectoryPath) throws IOException {
        Path root = Paths.get(rootDirectoryPath);
        return Files.list(root)
                .filter(Files::isDirectory)
                .filter(projectDir -> Files.exists(projectDir.resolve("pom.xml")))
                .collect(Collectors.toList());
    }
}

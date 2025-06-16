package com.analyzer.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Identifie les projets Java valides à analyser dans un répertoire racine.
 */
public class ProjectScanner {

    /**
     * Trouve tous les sous-dossiers contenant un fichier `build.gradle.kts` ou `pom.xml`.
     * @param rootDirectory Le dossier racine où chercher les projets.
     * @return Une liste de répertoires de projets.
     */
    public List<File> findProjects(File rootDirectory) {
        List<File> projects = new ArrayList<>();
        File[] subDirectories = rootDirectory.listFiles(File::isDirectory);

        if (subDirectories == null) {
            return projects;
        }

        for (File dir : subDirectories) {
            File gradleBuildFile = new File(dir, "build.gradle.kts");
            File mavenBuildFile = new File(dir, "pom.xml");
            if (gradleBuildFile.exists() || mavenBuildFile.exists()) {
                projects.add(dir);
            }
        }
        return projects;
    }
}

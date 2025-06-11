package com.analyzer.parsers.config;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Analyse les fichiers de configuration Spring (XML pour l'instant)
 * pour créer une carte de l'injection de dépendances.
 */
public class SpringConfigParser {

    /**
     * Crée une carte de "nom du bean" -> "nom de la classe d'implémentation".
     * @param projectDir Le répertoire du projet.
     * @param activeProfile Le profil Spring actif (ex: "prod").
     * @return La carte des beans.
     */
    public Map<String, String> buildBeanMap(File projectDir, String activeProfile) {
        Map<String, String> beanMap = new HashMap<>();
        Path sourceRoot = projectDir.toPath().resolve("src/main/resources");

        if (!Files.exists(sourceRoot)) return beanMap;

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(path -> path.toString().endsWith(".xml"))
                  .forEach(path -> {
                      try {
                          parseXmlConfig(path.toFile(), activeProfile, beanMap);
                      } catch (Exception e) {
                          System.err.println("Erreur lors de l'analyse du fichier de config Spring : " + path);
                      }
                  });
        } catch (IOException e) {
            // ...
        }
        return beanMap;
    }

    private void parseXmlConfig(File xmlFile, String activeProfile, Map<String, String> beanMap) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        NodeList beanList = doc.getElementsByTagName("bean");
        for (int i = 0; i < beanList.getLength(); i++) {
            Element beanElement = (Element) beanList.item(i);
            String beanId = beanElement.getAttribute("id");
            String beanClass = beanElement.getAttribute("class");

            // Gestion de base des profils : on ne charge que les beans qui correspondent
            // ou qui n'ont pas de profil. Une vraie gestion serait plus complexe.
            if (beanElement.hasAttribute("profile")) {
                if (beanElement.getAttribute("profile").equals(activeProfile)) {
                    beanMap.put(beanId, beanClass);
                }
            } else {
                 beanMap.put(beanId, beanClass);
            }
        }
    }
}

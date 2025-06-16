// Fichier: src/main/java/com/votre_entreprise/analyzer/spoon/endpoint/StrutsEndpointFinder.java
package com.votre_entreprise.analyzer.spoon.endpoint;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StrutsEndpointFinder implements EndpointFinder {

    private final Launcher spoonLauncher;
    private final String projectPath;
    private final Map<CtMethod<?>, String> pathCache = new HashMap<>();

    public StrutsEndpointFinder(Launcher spoonLauncher, String projectPath) {
        this.spoonLauncher = spoonLauncher;
        this.projectPath = projectPath;
    }

    @Override
    public List<CtMethod<?>> findEndpoints() {
        List<CtMethod<?>> endpoints = new ArrayList<>();
        Optional<Path> strutsXmlPath = findStrutsXml();
        
        if (strutsXmlPath.isEmpty()) {
            System.err.println("Avertissement: Le fichier struts.xml n'a pas été trouvé dans " + projectPath);
            return endpoints;
        }

        try {
            File xmlFile = strutsXmlPath.get().toFile();
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList actionNodes = doc.getElementsByTagName("action");

            for (int i = 0; i < actionNodes.getLength(); i++) {
                Node node = actionNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String actionName = element.getAttribute("name");
                    String className = element.getAttribute("class");
                    String methodName = element.hasAttribute("method") ? element.getAttribute("method") : "execute";

                    CtType<?> actionClass = spoonLauncher.getFactory().Type().get(className);
                    if (actionClass != null) {
                        for (CtMethod<?> method : actionClass.getMethods()) {
                            if (method.getSimpleName().equals(methodName)) {
                                endpoints.add(method);
                                pathCache.put(method, "/" + actionName + ".action");
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return endpoints;
    }

    private Optional<Path> findStrutsXml() {
        try {
            // Recherche du fichier struts.xml dans les répertoires de ressources des modules
            return Files.walk(Paths.get(projectPath))
                    .filter(path -> path.endsWith("src/main/resources/struts.xml"))
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public String getPathFor(CtMethod<?> method) {
        return pathCache.getOrDefault(method, "N/A");
    }

    @Override
    public String getHttpMethodFor(CtMethod<?> method) {
        // Struts ne définit pas de méthode HTTP de manière standard dans le XML,
        // cela dépend souvent de conventions ou de plugins supplémentaires (ex: REST plugin).
        return "POST/GET";
    }
}

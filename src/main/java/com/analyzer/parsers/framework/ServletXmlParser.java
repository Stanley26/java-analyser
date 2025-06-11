package com.analyzer.parsers.framework;

import com.analyzer.model.technical.Endpoint;
import com.analyzer.model.technical.EndpointDetails;
import com.analyzer.model.technical.SourceLocation;
import com.analyzer.parsers.common.EntryPointParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Un parseur de point d'entrée qui analyse les descripteurs de déploiement Java EE (web.xml).
 * Il identifie les déclarations de Servlets et leurs mappings d'URL.
 */
public class ServletXmlParser implements EntryPointParser {

    /**
     * Indique que ce parseur ne s'intéresse qu'au fichier standard de configuration web Java EE.
     */
    @Override
    public boolean supports(File file) {
        return file.getName().equals("web.xml");
    }

    /**
     * Analyse le fichier web.xml pour en extraire les servlets et leurs mappings.
     * @param xmlFile Le fichier web.xml à analyser.
     * @param projectRoot Le chemin racine du projet pour contextualiser les chemins de fichiers.
     * @return Une liste d'objets Endpoint trouvés.
     */
    @Override
    public List<Endpoint> parse(File xmlFile, Path projectRoot) {
        List<Endpoint> endpoints = new ArrayList<>();
        Map<String, String> servletNameToClass = new HashMap<>();

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbFactory.setXIncludeAware(false);
            dbFactory.setExpandEntityReferences(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            // Étape 1: Créer une carte qui associe un nom de servlet à sa classe Java.
            NodeList servletList = doc.getElementsByTagName("servlet");
            for (int i = 0; i < servletList.getLength(); i++) {
                Node node = servletList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String servletName = getElementTextContent(element, "servlet-name");
                    String servletClass = getElementTextContent(element, "servlet-class");
                    if (servletName != null && servletClass != null) {
                        servletNameToClass.put(servletName, servletClass);
                    }
                }
            }

            // Étape 2: Parcourir les mappings d'URL pour construire les Endpoints.
            NodeList mappingList = doc.getElementsByTagName("servlet-mapping");
            for (int i = 0; i < mappingList.getLength(); i++) {
                Node node = mappingList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String servletName = getElementTextContent(element, "servlet-name");
                    String urlPattern = getElementTextContent(element, "url-pattern");

                    if (servletName != null && urlPattern != null && servletNameToClass.containsKey(servletName)) {
                        Endpoint endpoint = new Endpoint();
                        endpoint.framework = "Java Servlet";
                        endpoint.fullUrl = urlPattern;
                        endpoint.httpMethod = "GET/POST";

                        EndpointDetails details = new EndpointDetails();
                        details.controllerClass = servletNameToClass.get(servletName);
                        details.handlerMethod = "service(HttpServletRequest, HttpServletResponse)";

                        SourceLocation location = new SourceLocation();
                        location.file = projectRoot.relativize(xmlFile.toPath()).toString();
                        location.lineNumber = 0; 
                        details.sourceLocation = location;
                        
                        endpoint.details = details;
                        endpoints.add(endpoint);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de l'analyse du fichier web.xml: " + xmlFile.getAbsolutePath());
            e.printStackTrace();
        }
        return endpoints;
    }

    private String getElementTextContent(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
}

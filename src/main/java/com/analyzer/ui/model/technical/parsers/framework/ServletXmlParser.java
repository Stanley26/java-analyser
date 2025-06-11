package com.analyzer.parsers.framework;

import com.analyzer.model.technical.Endpoint;
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

public class ServletXmlParser implements EntryPointParser {

    @Override
    public boolean supports(File file) {
        return file.getName().equals("web.xml");
    }

    @Override
    public List<Endpoint> parse(File xmlFile, Path projectRoot) {
        List<Endpoint> endpoints = new ArrayList<>();
        Map<String, String> servletNameToClass = new HashMap<>();

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            // Étape 1: Mapper les noms de servlet à leurs classes
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

            // Étape 2: Mapper les URL patterns aux noms de servlet, et construire les Endpoints
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
                        endpoint.httpMethod = "GET/POST"; // Les servlets gèrent toutes les méthodes par défaut

                        endpoint.details.controllerClass = servletNameToClass.get(servletName);
                        endpoint.details.handlerMethod = "service(req, res)"; // Méthode de base

                        endpoint.details.sourceLocation = new SourceLocation();
                        endpoint.details.sourceLocation.file = projectRoot.relativize(xmlFile.toPath()).toString();
                        endpoint.details.sourceLocation.lineNumber = 0; // Difficile à déterminer pour une balise XML

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

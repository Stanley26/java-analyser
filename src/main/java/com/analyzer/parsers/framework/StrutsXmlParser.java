package com.analyzer.parsers.framework;

import com.analyzer.model.technical.AnalysisReport;
import com.analyzer.model.technical.Endpoint;
import com.analyzer.model.technical.SourceLocation;
import com.analyzer.parsers.common.FrameworkParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Path;

public class StrutsXmlParser implements FrameworkParser {

    @Override
    public boolean supports(File file) {
        return file.getName().equals("struts-config.xml");
    }

    @Override
    public void parse(File xmlFile, Path projectRoot, AnalysisReport report) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            // Pour des raisons de sécurité, désactiver les entités externes
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList actionMappings = doc.getElementsByTagName("action");

            for (int i = 0; i < actionMappings.getLength(); i++) {
                Node node = actionMappings.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;

                    Endpoint endpoint = new Endpoint();
                    endpoint.framework = "Struts 1.x";
                    
                    // L'URL de l'action
                    endpoint.fullUrl = element.getAttribute("path"); 
                    // La méthode HTTP est souvent implicite en Struts 1. On met une valeur générique.
                    endpoint.httpMethod = "GET/POST";

                    // Remplir les détails
                    endpoint.details.controllerClass = element.getAttribute("type");
                    endpoint.details.handlerMethod = "execute(...)"; // La méthode standard pour les Actions Struts
                    endpoint.details.formBean = element.getAttribute("name");

                    // Remplir la localisation du code
                    endpoint.details.sourceLocation = new SourceLocation();
                    // Pour Struts, la "source" est le fichier de config lui-même
                    endpoint.details.sourceLocation.file = projectRoot.relativize(xmlFile.toPath()).toString();
                    // Note: La localisation de la ligne est complexe en XML, on met 0 pour l'instant.
                    endpoint.details.sourceLocation.lineNumber = 0; 
                    
                    report.endpoints.add(endpoint);
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de l'analyse du fichier struts-config.xml : " + xmlFile.getAbsolutePath());
            e.printStackTrace();
        }
    }
}

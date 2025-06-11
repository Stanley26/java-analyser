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
import java.util.List;

public class StrutsXmlParser implements EntryPointParser {

    @Override
    public boolean supports(File file) {
        return file.getName().equals("struts-config.xml");
    }

    @Override
    public List<Endpoint> parse(File xmlFile, Path projectRoot) {
        List<Endpoint> endpoints = new ArrayList<>();
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
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
                    endpoint.fullUrl = element.getAttribute("path");
                    endpoint.httpMethod = "GET/POST";

                    EndpointDetails details = new EndpointDetails();
                    details.controllerClass = element.getAttribute("type");
                    details.handlerMethod = "execute(ActionMapping, ActionForm, ...)";
                    details.formBean = element.getAttribute("name");

                    SourceLocation location = new SourceLocation();
                    location.file = projectRoot.relativize(xmlFile.toPath()).toString();
                    location.lineNumber = 0;
                    details.sourceLocation = location;
                    
                    endpoint.details = details;
                    endpoints.add(endpoint);
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de l'analyse du fichier struts-config.xml : " + xmlFile.getAbsolutePath());
            e.printStackTrace();
        }
        return endpoints;
    }
}

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

/**
 * Un parseur de point d'entrée qui analyse les fichiers de configuration Struts 1.x (struts-config.xml).
 * Il identifie les 'actions' qui définissent les URLs et les classes Java qui les gèrent.
 */
public class StrutsXmlParser implements EntryPointParser {

    /**
     * Indique que ce parseur ne s'intéresse qu'au fichier de configuration standard de Struts.
     */
    @Override
    public boolean supports(File file) {
        return file.getName().equals("struts-config.xml");
    }

    /**
     * Analyse le fichier struts-config.xml pour en extraire les définitions d'actions.
     * @param xmlFile Le fichier struts-config.xml à analyser.
     * @param projectRoot Le chemin racine du projet pour contextualiser les chemins de fichiers.
     * @return Une liste d'objets Endpoint trouvés.
     */
    @Override
    public List<Endpoint> parse(File xmlFile, Path projectRoot) {
        List<Endpoint> endpoints = new ArrayList<>();
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            // Pour des raisons de sécurité, désactiver les entités externes pour éviter les attaques XXE.
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
                    
                    // L'URL de l'action, se termine souvent par .do
                    endpoint.fullUrl = element.getAttribute("path");
                    // La méthode HTTP est souvent implicite en Struts 1.
                    // Elle peut être GET ou POST, nous utilisons une valeur générique.
                    endpoint.httpMethod = "GET/POST";

                    // Remplir les détails
                    EndpointDetails details = new EndpointDetails();
                    details.controllerClass = element.getAttribute("type");
                    details.handlerMethod = "execute(ActionMapping, ActionForm, ...)"; // La méthode standard pour les Actions Struts
                    details.formBean = element.getAttribute("name"); // Le nom du form-bean associé

                    // Remplir la localisation du code
                    SourceLocation location = new SourceLocation();
                    // Pour Struts, la "source" est le fichier de config lui-même.
                    location.file = projectRoot.relativize(xmlFile.toPath()).toString();
                    // Note: La localisation précise de la ligne est complexe à obtenir de manière fiable en XML.
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

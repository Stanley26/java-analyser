package com.legacy.analyzer.parser;

import lombok.extern.slf4j.Slf4j;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WebXmlParser {
    
    private final SAXBuilder saxBuilder = new SAXBuilder();
    
    public Map<String, String> parseServletMappings(Path webXmlPath) {
        Map<String, String> mappings = new HashMap<>();
        
        try {
            Document document = saxBuilder.build(webXmlPath.toFile());
            Element root = document.getRootElement();
            
            // Extraire les servlets
            Map<String, String> servletNameToClass = new HashMap<>();
            List<Element> servlets = root.getChildren("servlet");
            
            for (Element servlet : servlets) {
                String servletName = servlet.getChildText("servlet-name");
                String servletClass = servlet.getChildText("servlet-class");
                
                if (servletName != null && servletClass != null) {
                    servletNameToClass.put(servletName, servletClass);
                }
            }
            
            // Extraire les mappings
            List<Element> servletMappings = root.getChildren("servlet-mapping");
            
            for (Element mapping : servletMappings) {
                String servletName = mapping.getChildText("servlet-name");
                String urlPattern = mapping.getChildText("url-pattern");
                
                if (servletName != null && urlPattern != null) {
                    String servletClass = servletNameToClass.get(servletName);
                    if (servletClass != null) {
                        mappings.put(servletClass, urlPattern);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Erreur lors du parsing de web.xml: {}", webXmlPath, e);
        }
        
        return mappings;
    }
    
    public Map<String, Object> parseWebXml(Path webXmlPath) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Document document = saxBuilder.build(webXmlPath.toFile());
            Element root = document.getRootElement();
            
            // Display name
            String displayName = root.getChildText("display-name");
            if (displayName != null) {
                result.put("displayName", displayName);
            }
            
            // Description
            String description = root.getChildText("description");
            if (description != null) {
                result.put("description", description);
            }
            
            // Context params
            Map<String, String> contextParams = new HashMap<>();
            List<Element> contextParamElements = root.getChildren("context-param");
            for (Element param : contextParamElements) {
                String name = param.getChildText("param-name");
                String value = param.getChildText("param-value");
                if (name != null && value != null) {
                    contextParams.put(name, value);
                }
            }
            result.put("contextParams", contextParams);
            
            // Filters
            List<Map<String, String>> filters = parseFilters(root);
            result.put("filters", filters);
            
            // Listeners
            List<String> listeners = parseListeners(root);
            result.put("listeners", listeners);
            
            // Security constraints
            List<Map<String, Object>> securityConstraints = parseSecurityConstraints(root);
            result.put("securityConstraints", securityConstraints);
            
            // Error pages
            Map<String, String> errorPages = parseErrorPages(root);
            result.put("errorPages", errorPages);
            
        } catch (Exception e) {
            log.error("Erreur lors du parsing complet de web.xml: {}", webXmlPath, e);
        }
        
        return result;
    }
    
    private List<Map<String, String>> parseFilters(Element root) {
        List<Map<String, String>> filters = new java.util.ArrayList<>();
        
        List<Element> filterElements = root.getChildren("filter");
        for (Element filter : filterElements) {
            Map<String, String> filterInfo = new HashMap<>();
            filterInfo.put("name", filter.getChildText("filter-name"));
            filterInfo.put("class", filter.getChildText("filter-class"));
            filters.add(filterInfo);
        }
        
        return filters;
    }
    
    private List<String> parseListeners(Element root) {
        List<String> listeners = new java.util.ArrayList<>();
        
        List<Element> listenerElements = root.getChildren("listener");
        for (Element listener : listenerElements) {
            String listenerClass = listener.getChildText("listener-class");
            if (listenerClass != null) {
                listeners.add(listenerClass);
            }
        }
        
        return listeners;
    }
    
    private List<Map<String, Object>> parseSecurityConstraints(Element root) {
        List<Map<String, Object>> constraints = new java.util.ArrayList<>();
        
        List<Element> constraintElements = root.getChildren("security-constraint");
        for (Element constraint : constraintElements) {
            Map<String, Object> constraintInfo = new HashMap<>();
            
            // Web resource collection
            Element webResourceCollection = constraint.getChild("web-resource-collection");
            if (webResourceCollection != null) {
                Map<String, Object> resourceInfo = new HashMap<>();
                resourceInfo.put("name", webResourceCollection.getChildText("web-resource-name"));
                
                List<String> urlPatterns = new java.util.ArrayList<>();
                List<Element> urlPatternElements = webResourceCollection.getChildren("url-pattern");
                for (Element pattern : urlPatternElements) {
                    urlPatterns.add(pattern.getText());
                }
                resourceInfo.put("urlPatterns", urlPatterns);
                
                List<String> httpMethods = new java.util.ArrayList<>();
                List<Element> httpMethodElements = webResourceCollection.getChildren("http-method");
                for (Element method : httpMethodElements) {
                    httpMethods.add(method.getText());
                }
                resourceInfo.put("httpMethods", httpMethods);
                
                constraintInfo.put("webResourceCollection", resourceInfo);
            }
            
            // Auth constraint
            Element authConstraint = constraint.getChild("auth-constraint");
            if (authConstraint != null) {
                List<String> roles = new java.util.ArrayList<>();
                List<Element> roleElements = authConstraint.getChildren("role-name");
                for (Element role : roleElements) {
                    roles.add(role.getText());
                }
                constraintInfo.put("roles", roles);
            }
            
            constraints.add(constraintInfo);
        }
        
        return constraints;
    }
    
    private Map<String, String> parseErrorPages(Element root) {
        Map<String, String> errorPages = new HashMap<>();
        
        List<Element> errorPageElements = root.getChildren("error-page");
        for (Element errorPage : errorPageElements) {
            String errorCode = errorPage.getChildText("error-code");
            String exceptionType = errorPage.getChildText("exception-type");
            String location = errorPage.getChildText("location");
            
            if (location != null) {
                if (errorCode != null) {
                    errorPages.put("error-" + errorCode, location);
                } else if (exceptionType != null) {
                    errorPages.put("exception-" + exceptionType, location);
                }
            }
        }
        
        return errorPages;
    }
}
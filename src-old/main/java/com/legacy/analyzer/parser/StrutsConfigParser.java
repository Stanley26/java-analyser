package com.legacy.analyzer.parser;

import com.legacy.analyzer.extractors.endpoints.StrutsEndpointExtractor.StrutsActionMapping;
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
public class StrutsConfigParser {
    
    private final SAXBuilder saxBuilder = new SAXBuilder();
    
    public Map<String, StrutsActionMapping> parseStrutsConfig(Path strutsConfigPath) {
        Map<String, StrutsActionMapping> mappings = new HashMap<>();
        
        try {
            Document document = saxBuilder.build(strutsConfigPath.toFile());
            Element root = document.getRootElement();
            
            // Parser les form-beans pour référence
            Map<String, String> formBeans = parseFormBeans(root);
            
            // Parser les action-mappings
            Element actionMappings = root.getChild("action-mappings");
            if (actionMappings != null) {
                List<Element> actions = actionMappings.getChildren("action");
                
                for (Element action : actions) {
                    StrutsActionMapping mapping = parseActionMapping(action, formBeans);
                    if (mapping != null && mapping.getPath() != null) {
                        mappings.put(mapping.getPath(), mapping);
                    }
                }
            }
            
            log.debug("Nombre de mappings Struts trouvés: {}", mappings.size());
            
        } catch (Exception e) {
            log.error("Erreur lors du parsing de struts-config.xml: {}", strutsConfigPath, e);
        }
        
        return mappings;
    }
    
    private Map<String, String> parseFormBeans(Element root) {
        Map<String, String> formBeans = new HashMap<>();
        
        Element formBeansElement = root.getChild("form-beans");
        if (formBeansElement != null) {
            List<Element> formBeanElements = formBeansElement.getChildren("form-bean");
            
            for (Element formBean : formBeanElements) {
                String name = formBean.getAttributeValue("name");
                String type = formBean.getAttributeValue("type");
                
                if (name != null && type != null) {
                    formBeans.put(name, type);
                }
            }
        }
        
        return formBeans;
    }
    
    private StrutsActionMapping parseActionMapping(Element action, Map<String, String> formBeans) {
        StrutsActionMapping mapping = new StrutsActionMapping();
        
        // Attributs principaux
        mapping.setPath(action.getAttributeValue("path"));
        mapping.setType(action.getAttributeValue("type"));
        mapping.setName(action.getAttributeValue("name"));
        mapping.setScope(action.getAttributeValue("scope", "session"));
        mapping.setValidate(action.getAttributeValue("validate", "true"));
        
        // Paramètre pour DispatchAction
        String parameter = action.getAttributeValue("parameter");
        if (parameter != null) {
            mapping.setMethods(List.of(parameter));
        }
        
        // Parser les forwards
        Map<String, String> forwards = new HashMap<>();
        List<Element> forwardElements = action.getChildren("forward");
        
        for (Element forward : forwardElements) {
            String name = forward.getAttributeValue("name");
            String path = forward.getAttributeValue("path");
            
            if (name != null && path != null) {
                forwards.put(name, path);
            }
        }
        
        mapping.setForwards(forwards);
        
        return mapping;
    }
    
    public Map<String, Object> parseStrutsConfigComplete(Path strutsConfigPath) {
        Map<String, Object> config = new HashMap<>();
        
        try {
            Document document = saxBuilder.build(strutsConfigPath.toFile());
            Element root = document.getRootElement();
            
            // Form beans
            config.put("formBeans", parseFormBeansComplete(root));
            
            // Global exceptions
            config.put("globalExceptions", parseGlobalExceptions(root));
            
            // Global forwards
            config.put("globalForwards", parseGlobalForwards(root));
            
            // Action mappings
            config.put("actionMappings", parseActionMappingsComplete(root));
            
            // Controller
            config.put("controller", parseController(root));
            
            // Message resources
            config.put("messageResources", parseMessageResources(root));
            
            // Plug-ins
            config.put("plugins", parsePlugins(root));
            
        } catch (Exception e) {
            log.error("Erreur lors du parsing complet de struts-config.xml", e);
        }
        
        return config;
    }
    
    private List<Map<String, String>> parseFormBeansComplete(Element root) {
        List<Map<String, String>> formBeans = new java.util.ArrayList<>();
        
        Element formBeansElement = root.getChild("form-beans");
        if (formBeansElement != null) {
            List<Element> formBeanElements = formBeansElement.getChildren("form-bean");
            
            for (Element formBean : formBeanElements) {
                Map<String, String> bean = new HashMap<>();
                bean.put("name", formBean.getAttributeValue("name"));
                bean.put("type", formBean.getAttributeValue("type"));
                bean.put("dynamic", formBean.getAttributeValue("dynamic", "false"));
                
                formBeans.add(bean);
            }
        }
        
        return formBeans;
    }
    
    private List<Map<String, String>> parseGlobalExceptions(Element root) {
        List<Map<String, String>> exceptions = new java.util.ArrayList<>();
        
        Element globalExceptionsElement = root.getChild("global-exceptions");
        if (globalExceptionsElement != null) {
            List<Element> exceptionElements = globalExceptionsElement.getChildren("exception");
            
            for (Element exception : exceptionElements) {
                Map<String, String> exc = new HashMap<>();
                exc.put("key", exception.getAttributeValue("key"));
                exc.put("type", exception.getAttributeValue("type"));
                exc.put("path", exception.getAttributeValue("path"));
                exc.put("handler", exception.getAttributeValue("handler"));
                
                exceptions.add(exc);
            }
        }
        
        return exceptions;
    }
    
    private List<Map<String, String>> parseGlobalForwards(Element root) {
        List<Map<String, String>> forwards = new java.util.ArrayList<>();
        
        Element globalForwardsElement = root.getChild("global-forwards");
        if (globalForwardsElement != null) {
            List<Element> forwardElements = globalForwardsElement.getChildren("forward");
            
            for (Element forward : forwardElements) {
                Map<String, String> fwd = new HashMap<>();
                fwd.put("name", forward.getAttributeValue("name"));
                fwd.put("path", forward.getAttributeValue("path"));
                fwd.put("redirect", forward.getAttributeValue("redirect", "false"));
                
                forwards.add(fwd);
            }
        }
        
        return forwards;
    }
    
    private List<Map<String, Object>> parseActionMappingsComplete(Element root) {
        List<Map<String, Object>> actions = new java.util.ArrayList<>();
        
        Element actionMappingsElement = root.getChild("action-mappings");
        if (actionMappingsElement != null) {
            List<Element> actionElements = actionMappingsElement.getChildren("action");
            
            for (Element action : actionElements) {
                Map<String, Object> act = new HashMap<>();
                act.put("path", action.getAttributeValue("path"));
                act.put("type", action.getAttributeValue("type"));
                act.put("name", action.getAttributeValue("name"));
                act.put("scope", action.getAttributeValue("scope"));
                act.put("validate", action.getAttributeValue("validate"));
                act.put("input", action.getAttributeValue("input"));
                act.put("parameter", action.getAttributeValue("parameter"));
                act.put("attribute", action.getAttributeValue("attribute"));
                act.put("forward", action.getAttributeValue("forward"));
                act.put("include", action.getAttributeValue("include"));
                act.put("unknown", action.getAttributeValue("unknown"));
                
                // Forwards locaux
                List<Map<String, String>> localForwards = new java.util.ArrayList<>();
                List<Element> forwardElements = action.getChildren("forward");
                for (Element forward : forwardElements) {
                    Map<String, String> fwd = new HashMap<>();
                    fwd.put("name", forward.getAttributeValue("name"));
                    fwd.put("path", forward.getAttributeValue("path"));
                    fwd.put("redirect", forward.getAttributeValue("redirect"));
                    localForwards.add(fwd);
                }
                act.put("forwards", localForwards);
                
                // Exceptions locales
                List<Map<String, String>> localExceptions = new java.util.ArrayList<>();
                List<Element> exceptionElements = action.getChildren("exception");
                for (Element exception : exceptionElements) {
                    Map<String, String> exc = new HashMap<>();
                    exc.put("key", exception.getAttributeValue("key"));
                    exc.put("type", exception.getAttributeValue("type"));
                    exc.put("path", exception.getAttributeValue("path"));
                    localExceptions.add(exc);
                }
                act.put("exceptions", localExceptions);
                
                actions.add(act);
            }
        }
        
        return actions;
    }
    
    private Map<String, String> parseController(Element root) {
        Map<String, String> controller = new HashMap<>();
        
        Element controllerElement = root.getChild("controller");
        if (controllerElement != null) {
            controller.put("processorClass", controllerElement.getAttributeValue("processorClass"));
            controller.put("contentType", controllerElement.getAttributeValue("contentType"));
            controller.put("nocache", controllerElement.getAttributeValue("nocache"));
            controller.put("locale", controllerElement.getAttributeValue("locale"));
        }
        
        return controller;
    }
    
    private List<Map<String, String>> parseMessageResources(Element root) {
        List<Map<String, String>> resources = new java.util.ArrayList<>();
        
        List<Element> resourceElements = root.getChildren("message-resources");
        for (Element resource : resourceElements) {
            Map<String, String> res = new HashMap<>();
            res.put("parameter", resource.getAttributeValue("parameter"));
            res.put("key", resource.getAttributeValue("key"));
            res.put("null", resource.getAttributeValue("null"));
            
            resources.add(res);
        }
        
        return resources;
    }
    
    private List<Map<String, String>> parsePlugins(Element root) {
        List<Map<String, String>> plugins = new java.util.ArrayList<>();
        
        List<Element> pluginElements = root.getChildren("plug-in");
        for (Element plugin : pluginElements) {
            Map<String, String> plg = new HashMap<>();
            plg.put("className", plugin.getAttributeValue("className"));
            
            // Propriétés du plugin
            List<Element> properties = plugin.getChildren("set-property");
            for (Element property : properties) {
                String propName = property.getAttributeValue("property");
                String propValue = property.getAttributeValue("value");
                if (propName != null && propValue != null) {
                    plg.put("property." + propName, propValue);
                }
            }
            
            plugins.add(plg);
        }
        
        return plugins;
    }
}
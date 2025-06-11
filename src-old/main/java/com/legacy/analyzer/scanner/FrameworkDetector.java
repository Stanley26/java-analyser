package com.legacy.analyzer.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Component
public class FrameworkDetector {
    
    public Set<String> detectFrameworks(Path applicationPath) {
        Set<String> frameworks = new HashSet<>();
        
        try {
            // Détection par fichiers de configuration
            detectByConfigFiles(applicationPath, frameworks);
            
            // Détection par structure de répertoires
            detectByDirectoryStructure(applicationPath, frameworks);
            
            // Détection par bibliothèques
            detectByLibraries(applicationPath, frameworks);
            
            // Détection par descripteurs
            detectByDescriptors(applicationPath, frameworks);
            
        } catch (IOException e) {
            log.error("Erreur lors de la détection des frameworks", e);
        }
        
        log.debug("Frameworks détectés pour {}: {}", applicationPath, frameworks);
        return frameworks;
    }
    
    private void detectByConfigFiles(Path appPath, Set<String> frameworks) throws IOException {
        // Struts
        if (Files.exists(appPath.resolve("WEB-INF/struts-config.xml"))) {
            frameworks.add("struts");
            
            // Détection de la version Struts
            String content = Files.readString(appPath.resolve("WEB-INF/struts-config.xml"));
            if (content.contains("struts-config_1_1.dtd") || content.contains("struts-config_1_2.dtd")) {
                frameworks.add("struts-1.x");
            } else if (content.contains("struts-2")) {
                frameworks.add("struts-2.x");
            }
        }
        
        // Spring
        if (Files.exists(appPath.resolve("WEB-INF/applicationContext.xml")) ||
            Files.exists(appPath.resolve("WEB-INF/spring")) ||
            Files.exists(appPath.resolve("WEB-INF/dispatcher-servlet.xml"))) {
            frameworks.add("spring");
            frameworks.add("spring-mvc");
        }
        
        // JSF
        if (Files.exists(appPath.resolve("WEB-INF/faces-config.xml"))) {
            frameworks.add("jsf");
        }
        
        // Hibernate
        if (Files.exists(appPath.resolve("WEB-INF/classes/hibernate.cfg.xml")) ||
            existsInClasspath(appPath, "hibernate.properties")) {
            frameworks.add("hibernate");
        }
        
        // MyBatis
        if (existsInClasspath(appPath, "mybatis-config.xml") ||
            existsInClasspath(appPath, "sqlmap-config.xml")) {
            frameworks.add("mybatis");
        }
    }
    
    private void detectByDirectoryStructure(Path appPath, Set<String> frameworks) {
        // Structure Maven standard
        if (Files.exists(appPath.resolve("src/main/java"))) {
            frameworks.add("maven-structure");
        }
        
        // EJB
        if (Files.exists(appPath.resolve("META-INF/ejb-jar.xml"))) {
            frameworks.add("ejb");
            
            try {
                String content = Files.readString(appPath.resolve("META-INF/ejb-jar.xml"));
                if (content.contains("ejb-jar_2_1.xsd")) {
                    frameworks.add("ejb-2.x");
                } else if (content.contains("ejb-jar_3_0.xsd") || content.contains("ejb-jar_3_1.xsd")) {
                    frameworks.add("ejb-3.x");
                }
            } catch (IOException e) {
                log.error("Erreur lors de la lecture de ejb-jar.xml", e);
            }
        }
    }
    
    private void detectByLibraries(Path appPath, Set<String> frameworks) throws IOException {
        Path libPath = appPath.resolve("WEB-INF/lib");
        if (Files.exists(libPath)) {
            try (Stream<Path> libs = Files.list(libPath)) {
                libs.filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jar -> {
                        String jarName = jar.getFileName().toString().toLowerCase();
                        
                        // Struts
                        if (jarName.contains("struts")) {
                            frameworks.add("struts");
                            if (jarName.contains("struts-1") || jarName.contains("struts1")) {
                                frameworks.add("struts-1.x");
                            } else if (jarName.contains("struts2") || jarName.contains("struts-2")) {
                                frameworks.add("struts-2.x");
                            }
                        }
                        
                        // Spring
                        if (jarName.contains("spring")) {
                            frameworks.add("spring");
                            if (jarName.contains("spring-mvc") || jarName.contains("spring-webmvc")) {
                                frameworks.add("spring-mvc");
                            }
                            if (jarName.contains("spring-boot")) {
                                frameworks.add("spring-boot");
                            }
                        }
                        
                        // JAX-RS
                        if (jarName.contains("jersey") || jarName.contains("resteasy") || 
                            jarName.contains("cxf") || jarName.contains("jaxrs")) {
                            frameworks.add("jax-rs");
                        }
                        
                        // JSF
                        if (jarName.contains("jsf") || jarName.contains("myfaces") || 
                            jarName.contains("richfaces") || jarName.contains("primefaces")) {
                            frameworks.add("jsf");
                        }
                        
                        // Hibernate
                        if (jarName.contains("hibernate")) {
                            frameworks.add("hibernate");
                        }
                        
                        // JPA
                        if (jarName.contains("jpa") || jarName.contains("eclipselink") || 
                            jarName.contains("openjpa")) {
                            frameworks.add("jpa");
                        }
                        
                        // MyBatis
                        if (jarName.contains("mybatis") || jarName.contains("ibatis")) {
                            frameworks.add("mybatis");
                        }
                        
                        // Apache CXF
                        if (jarName.contains("cxf")) {
                            frameworks.add("apache-cxf");
                            frameworks.add("soap-ws");
                        }
                        
                        // Axis
                        if (jarName.contains("axis")) {
                            frameworks.add("axis");
                            frameworks.add("soap-ws");
                        }
                    });
            }
        }
    }
    
    private void detectByDescriptors(Path appPath, Set<String> frameworks) throws IOException {
        // Analyse du web.xml
        Path webXml = appPath.resolve("WEB-INF/web.xml");
        if (Files.exists(webXml)) {
            String content = Files.readString(webXml);
            
            // Servlets
            if (content.contains("<servlet>") && content.contains("<servlet-class>")) {
                frameworks.add("servlet");
                
                // Version Servlet
                if (content.contains("web-app_2_5.xsd")) {
                    frameworks.add("servlet-2.5");
                } else if (content.contains("web-app_3_0.xsd")) {
                    frameworks.add("servlet-3.0");
                } else if (content.contains("web-app_3_1.xsd")) {
                    frameworks.add("servlet-3.1");
                } else if (content.contains("web-app_4_0.xsd")) {
                    frameworks.add("servlet-4.0");
                }
            }
            
            // Struts
            if (content.contains("org.apache.struts.action.ActionServlet")) {
                frameworks.add("struts");
                frameworks.add("struts-1.x");
            }
            if (content.contains("org.apache.struts2")) {
                frameworks.add("struts");
                frameworks.add("struts-2.x");
            }
            
            // Spring
            if (content.contains("org.springframework.web.servlet.DispatcherServlet")) {
                frameworks.add("spring");
                frameworks.add("spring-mvc");
            }
            
            // JSF
            if (content.contains("javax.faces.webapp.FacesServlet")) {
                frameworks.add("jsf");
            }
            
            // JAX-RS
            if (content.contains("jersey.spi.container.servlet.ServletContainer") ||
                content.contains("org.jboss.resteasy.plugins.server.servlet")) {
                frameworks.add("jax-rs");
            }
            
            // JAX-WS
            if (content.contains("com.sun.xml.ws.transport.http.servlet.WSServlet")) {
                frameworks.add("jax-ws");
                frameworks.add("soap-ws");
            }
        }
    }
    
    private boolean existsInClasspath(Path appPath, String filename) {
        return Files.exists(appPath.resolve("WEB-INF/classes/" + filename)) ||
               Files.exists(appPath.resolve("classes/" + filename));
    }
}
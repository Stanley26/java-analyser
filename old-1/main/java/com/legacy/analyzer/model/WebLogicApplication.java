package com.legacy.analyzer.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebLogicApplication {
    
    private String id;
    private String name;
    private String version;
    private ApplicationType type; // EAR, WAR, JAR
    private Path sourcePath;
    private Path extractedPath;
    private DeploymentInfo deploymentInfo;
    private List<Module> modules;
    private List<Endpoint> endpoints;
    private Set<String> frameworks;
    private List<DataSource> dataSources;
    private List<Library> libraries;
    private Dependencies globalDependencies;
    private Statistics statistics;
    private Map<String, Object> metadata;
    
    public enum ApplicationType {
        EAR, WAR, JAR, EXPLODED_EAR, EXPLODED_WAR
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeploymentInfo {
        private String contextRoot;
        private String displayName;
        private String description;
        private List<String> virtualHosts;
        private SecurityConfiguration security;
        private Map<String, String> weblogicDescriptors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Module {
        private String name;
        private ModuleType type;
        private String contextRoot;
        private Path path;
        private List<Endpoint> endpoints;
        private Set<String> frameworks;
        private Dependencies dependencies;
        private Map<String, String> descriptors;
    }
    
    public enum ModuleType {
        WEB, EJB, CLIENT, CONNECTOR, UTILITY
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataSource {
        private String name;
        private String jndiName;
        private String driverClass;
        private String url;
        private String username;
        private Integer minPool;
        private Integer maxPool;
        private Map<String, String> properties;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Library {
        private String groupId;
        private String artifactId;
        private String version;
        private String scope;
        private String jarFile;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityConfiguration {
        private String realmName;
        private List<SecurityRole> roles;
        private List<SecurityConstraint> constraints;
        private String authMethod;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityRole {
        private String roleName;
        private String description;
        private List<String> principals;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityConstraint {
        private String displayName;
        private List<String> urlPatterns;
        private List<String> httpMethods;
        private List<String> roles;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Statistics {
        private Integer totalEndpoints;
        private Integer totalClasses;
        private Integer totalMethods;
        private Integer linesOfCode;
        private Map<String, Integer> endpointsByFramework;
        private Map<String, Integer> dependenciesByType;
        private ComplexityMetrics complexity;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplexityMetrics {
        private Double averageCyclomaticComplexity;
        private Integer maxMethodComplexity;
        private Integer maxClassComplexity;
        private Double averageMethodLength;
        private Integer deepestCallDepth;
    }
}
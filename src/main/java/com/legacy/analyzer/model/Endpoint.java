package com.legacy.analyzer.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Endpoint {
    
    private String id;
    private String applicationName;
    private String moduleName;
    private String className;
    private String methodName;
    private String url;
    private Set<HttpMethod> httpMethods;
    private List<Parameter> parameters;
    private List<Parameter> responseParameters;
    private SecurityInfo security;
    private TransactionInfo transaction;
    private List<BusinessRule> businessRules;
    private PseudoCode pseudoCode;
    private Dependencies dependencies;
    private Map<String, Object> metadata;
    private SourceLocation sourceLocation;
    
    public enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameter {
        private String name;
        private String type;
        private String source; // PATH, QUERY, BODY, HEADER
        private boolean required;
        private String defaultValue;
        private List<String> validations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityInfo {
        private boolean requiresAuthentication;
        private List<String> roles;
        private String authenticationMethod;
        private Map<String, String> securityConstraints;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionInfo {
        private boolean transactional;
        private String propagation;
        private String isolationLevel;
        private Integer timeout;
        private boolean readOnly;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessRule {
        private String id;
        private String description;
        private String condition;
        private String action;
        private List<String> relatedTables;
        private List<String> relatedServices;
        private Integer lineNumber;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PseudoCode {
        private String simplified;
        private String detailed;
        private List<PseudoCodeBlock> blocks;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PseudoCodeBlock {
        private String type; // CONDITION, LOOP, CALL, ASSIGNMENT
        private String content;
        private List<PseudoCodeBlock> children;
        private Integer startLine;
        private Integer endLine;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceLocation {
        private String filePath;
        private Integer startLine;
        private Integer endLine;
        private Integer startColumn;
        private Integer endColumn;
    }
}
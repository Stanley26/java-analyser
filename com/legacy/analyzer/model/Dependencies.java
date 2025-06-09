package com.legacy.analyzer.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Dependencies {
    
    private List<DatabaseDependency> databases;
    private List<EJBDependency> ejbs;
    private List<CobolDependency> cobolPrograms;
    private List<WebServiceDependency> webServices;
    private List<JMSDependency> jmsQueues;
    private List<FileDependency> files;
    private List<ExternalSystemDependency> externalSystems;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseDependency {
        private String connectionName;
        private String dataSourceName;
        private List<String> tables;
        private List<String> views;
        private List<String> storedProcedures;
        private List<SQLQuery> queries;
        private String databaseType; // ORACLE, DB2, SQL_SERVER, etc.
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SQLQuery {
        private String id;
        private String type; // SELECT, INSERT, UPDATE, DELETE, CALL
        private String rawQuery;
        private String normalizedQuery;
        private List<String> tables;
        private List<String> parameters;
        private Integer lineNumber;
        private boolean isDynamic;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EJBDependency {
        private String ejbName;
        private String jndiName;
        private String interfaceClass;
        private String homeInterface;
        private String remoteInterface;
        private List<String> methodsCalled;
        private boolean isLocal;
        private boolean isStateless;
        private String version; // 2.x or 3.x
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CobolDependency {
        private String programName;
        private String connectionType; // SOCKET, JNI, FILE, MQ
        private String host;
        private Integer port;
        private String protocol;
        private List<String> inputParameters;
        private List<String> outputParameters;
        private Map<String, String> connectionDetails;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebServiceDependency {
        private String serviceName;
        private String url;
        private String type; // SOAP, REST
        private String wsdlLocation;
        private List<String> operations;
        private String authentication;
        private Map<String, String> headers;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JMSDependency {
        private String queueName;
        private String topicName;
        private String connectionFactory;
        private String jndiName;
        private String messageType;
        private boolean isProducer;
        private boolean isConsumer;
        private Map<String, String> properties;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileDependency {
        private String filePath;
        private String fileType;
        private String accessType; // READ, WRITE, READ_WRITE
        private String format; // XML, CSV, FIXED, JSON
        private boolean isShared;
        private String encoding;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalSystemDependency {
        private String systemName;
        private String type;
        private String connectionMethod;
        private Map<String, String> connectionParameters;
    }
}
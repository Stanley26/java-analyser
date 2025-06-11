package com.analyzer.model.technical;

public class Endpoint {
    public String fullUrl;
    public String httpMethod;
    public String framework = "Spring MVC"; // Sera défini par le parseur
    public EndpointDetails details;

    public Endpoint() {
        this.details = new EndpointDetails();
    }
}
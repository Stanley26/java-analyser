package com.analyzer.model.business;

import com.analyzer.model.technical.Endpoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente une fonction d'affaires unique et la liste des endpoints techniques qui la composent.
 */
public class BusinessFunction {
    public String name;
    public List<Endpoint> endpoints = new ArrayList<>();

    public BusinessFunction(String name) {
        this.name = name;
    }
}
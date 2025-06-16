package com.analyzer.model.business;

import com.analyzer.model.technical.Endpoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente le rapport consolidé qui regroupe les endpoints par fonction d'affaires.
 */
public class BusinessFunctionReport {
    public List<BusinessFunction> businessFunctions = new ArrayList<>();
    public List<Endpoint> unmappedEndpoints = new ArrayList<>();
}
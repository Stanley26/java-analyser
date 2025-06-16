package com.analyzer.correlation;

import com.analyzer.model.business.BusinessFunction;
import com.analyzer.model.business.BusinessFunctionReport;
import com.analyzer.model.technical.AnalysisReport;
import com.analyzer.model.technical.Endpoint;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Corrèle les endpoints techniques avec la carte des fonctions d'affaires.
 */
public class BusinessFunctionCorrelator {

    public BusinessFunctionReport correlate(List<AnalysisReport> technicalReports, Map<String, String> businessMap) {
        BusinessFunctionReport businessReport = new BusinessFunctionReport();
        Map<String, BusinessFunction> functions = new HashMap<>();

        List<Endpoint> allEndpoints = technicalReports.stream()
                .flatMap(report -> report.endpoints.stream())
                .collect(Collectors.toList());

        for (Endpoint endpoint : allEndpoints) {
            String key = endpoint.httpMethod.toUpperCase() + ":" + endpoint.fullUrl;
            
            if (businessMap.containsKey(key)) {
                String functionName = businessMap.get(key);
                // Récupère ou crée la fonction d'affaires
                BusinessFunction bf = functions.computeIfAbsent(functionName, BusinessFunction::new);
                bf.endpoints.add(endpoint);
            } else {
                businessReport.unmappedEndpoints.add(endpoint);
            }
        }

        businessReport.businessFunctions.addAll(functions.values());
        return businessReport;
    }
}

package com.legacy.analyzer.generators;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacy.analyzer.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DependencyGraphGenerator {
    
    private final ObjectMapper objectMapper;
    
    public void generateDependencyGraphs(List<AnalysisResult> results, Path outputDir) throws IOException {
        Path graphsDir = outputDir.resolve("dependency-graphs");
        Files.createDirectories(graphsDir);
        
        // Générer différents formats de graphiques
        generateMermaidGraph(results, graphsDir);
        generateGraphvizDot(results, graphsDir);
        generateD3JsonGraph(results, graphsDir);
        generateInteractiveHTML(results, graphsDir);
        
        // Générer des graphiques par type de dépendance
        generateDatabaseDependencyGraph(results, graphsDir);
        generateEJBDependencyGraph(results, graphsDir);
        generateWebServiceDependencyGraph(results, graphsDir);
        generateCobolDependencyGraph(results, graphsDir);
    }
    
    private void generateMermaidGraph(List<AnalysisResult> results, Path outputDir) throws IOException {
        StringBuilder mermaid = new StringBuilder();
        mermaid.append("graph TB\n");
        mermaid.append("    classDef application fill:#f9f,stroke:#333,stroke-width:4px\n");
        mermaid.append("    classDef database fill:#bbf,stroke:#333,stroke-width:2px\n");
        mermaid.append("    classDef ejb fill:#bfb,stroke:#333,stroke-width:2px\n");
        mermaid.append("    classDef cobol fill:#fbb,stroke:#333,stroke-width:2px\n");
        mermaid.append("    classDef webservice fill:#fbf,stroke:#333,stroke-width:2px\n\n");
        
        Set<String> addedNodes = new HashSet<>();
        
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            String appId = sanitizeId(app.getName());
            
            // Nœud de l'application
            mermaid.append("    ").append(appId).append("[\"").append(app.getName())
                   .append("<br/>").append(app.getType()).append("\"]:::application\n");
            
            if (app.getGlobalDependencies() != null) {
                // Bases de données
                if (app.getGlobalDependencies().getDatabases() != null) {
                    for (Dependencies.DatabaseDependency db : app.getGlobalDependencies().getDatabases()) {
                        String dbId = sanitizeId("DB_" + db.getDataSourceName());
                        
                        if (!addedNodes.contains(dbId)) {
                            mermaid.append("    ").append(dbId).append("[(").append(db.getDataSourceName())
                                   .append("<br/>").append(db.getDatabaseType()).append(")]:::database\n");
                            addedNodes.add(dbId);
                        }
                        
                        mermaid.append("    ").append(appId).append(" --> ").append(dbId).append("\n");
                    }
                }
                
                // EJB
                if (app.getGlobalDependencies().getEjbs() != null) {
                    for (Dependencies.EJBDependency ejb : app.getGlobalDependencies().getEjbs()) {
                        String ejbId = sanitizeId("EJB_" + ejb.getEjbName());
                        
                        if (!addedNodes.contains(ejbId)) {
                            mermaid.append("    ").append(ejbId).append("{{").append(ejb.getEjbName())
                                   .append("}}:::ejb\n");
                            addedNodes.add(ejbId);
                        }
                        
                        mermaid.append("    ").append(appId).append(" --> ").append(ejbId).append("\n");
                    }
                }
                
                // Cobol
                if (app.getGlobalDependencies().getCobolPrograms() != null) {
                    for (Dependencies.CobolDependency cobol : app.getGlobalDependencies().getCobolPrograms()) {
                        String cobolId = sanitizeId("COBOL_" + cobol.getProgramName());
                        
                        if (!addedNodes.contains(cobolId)) {
                            mermaid.append("    ").append(cobolId).append("[/").append(cobol.getProgramName())
                                   .append("<br/>").append(cobol.getConnectionType()).append("/]:::cobol\n");
                            addedNodes.add(cobolId);
                        }
                        
                        mermaid.append("    ").append(appId).append(" --> ").append(cobolId).append("\n");
                    }
                }
                
                // Web Services
                if (app.getGlobalDependencies().getWebServices() != null) {
                    for (Dependencies.WebServiceDependency ws : app.getGlobalDependencies().getWebServices()) {
                        String wsId = sanitizeId("WS_" + ws.getServiceName());
                        
                        if (!addedNodes.contains(wsId)) {
                            mermaid.append("    ").append(wsId).append("(").append(ws.getServiceName())
                                   .append("<br/>").append(ws.getType()).append("):::webservice\n");
                            addedNodes.add(wsId);
                        }
                        
                        mermaid.append("    ").append(appId).append(" --> ").append(wsId).append("\n");
                    }
                }
            }
        }
        
        // Ajouter les liens inter-applications
        addInterApplicationLinks(results, mermaid);
        
        Path mermaidFile = outputDir.resolve("dependency-graph.mmd");
        Files.writeString(mermaidFile, mermaid.toString());
    }
    
    private void generateGraphvizDot(List<AnalysisResult> results, Path outputDir) throws IOException {
        StringBuilder dot = new StringBuilder();
        dot.append("digraph Dependencies {\n");
        dot.append("    rankdir=LR;\n");
        dot.append("    node [fontname=\"Arial\"];\n");
        dot.append("    edge [fontname=\"Arial\"];\n\n");
        
        // Définir les styles
        dot.append("    // Styles\n");
        dot.append("    node [shape=box, style=filled];\n\n");
        
        // Créer des sous-graphes par couche
        dot.append("    // Applications\n");
        dot.append("    subgraph cluster_apps {\n");
        dot.append("        label=\"Applications\";\n");
        dot.append("        style=filled;\n");
        dot.append("        color=lightgrey;\n");
        dot.append("        node [fillcolor=lightblue];\n");
        
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            WebLogicApplication app = result.getApplication();
            String appId = sanitizeId(app.getName());
            
            dot.append("        ").append(appId).append(" [label=\"").append(app.getName())
               .append("\\n").append(app.getType()).append("\"];\n");
        }
        
        dot.append("    }\n\n");
        
        // Créer des sous-graphes pour les dépendances
        Map<String, Set<DependencyNode>> dependencyGroups = groupDependencies(results);
        
        // Bases de données
        if (dependencyGroups.containsKey("database")) {
            dot.append("    // Databases\n");
            dot.append("    subgraph cluster_db {\n");
            dot.append("        label=\"Databases\";\n");
            dot.append("        style=filled;\n");
            dot.append("        color=lightgreen;\n");
            dot.append("        node [shape=cylinder, fillcolor=lightgreen];\n");
            
            for (DependencyNode node : dependencyGroups.get("database")) {
                dot.append("        ").append(node.id).append(" [label=\"").append(node.label).append("\"];\n");
            }
            
            dot.append("    }\n\n");
        }
        
        // EJB
        if (dependencyGroups.containsKey("ejb")) {
            dot.append("    // EJB Components\n");
            dot.append("    subgraph cluster_ejb {\n");
            dot.append("        label=\"EJB Components\";\n");
            dot.append("        style=filled;\n");
            dot.append("        color=lightyellow;\n");
            dot.append("        node [shape=component, fillcolor=lightyellow];\n");
            
            for (DependencyNode node : dependencyGroups.get("ejb")) {
                dot.append("        ").append(node.id).append(" [label=\"").append(node.label).append("\"];\n");
            }
            
            dot.append("    }\n\n");
        }
        
        // Cobol
        if (dependencyGroups.containsKey("cobol")) {
            dot.append("    // COBOL Programs\n");
            dot.append("    subgraph cluster_cobol {\n");
            dot.append("        label=\"Mainframe COBOL\";\n");
            dot.append("        style=filled;\n");
            dot.append("        color=lightcoral;\n");
            dot.append("        node [shape=box3d, fillcolor=lightcoral];\n");
            
            for (DependencyNode node : dependencyGroups.get("cobol")) {
                dot.append("        ").append(node.id).append(" [label=\"").append(node.label).append("\"];\n");
            }
            
            dot.append("    }\n\n");
        }
        
        // Ajouter les relations
        dot.append("    // Relations\n");
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            String appId = sanitizeId(app.getName());
            
            if (app.getGlobalDependencies() != null) {
                addDotRelations(app, appId, dot);
            }
        }
        
        dot.append("}\n");
        
        Path dotFile = outputDir.resolve("dependency-graph.dot");
        Files.writeString(dotFile, dot.toString());
    }
    
    private void generateD3JsonGraph(List<AnalysisResult> results, Path outputDir) throws IOException {
        Map<String, Object> graph = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> links = new ArrayList<>();
        
        Map<String, Integer> nodeIndex = new HashMap<>();
        int index = 0;
        
        // Créer les nœuds pour les applications
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            Map<String, Object> node = new HashMap<>();
            node.put("id", app.getName());
            node.put("name", app.getName());
            node.put("type", "application");
            node.put("group", 1);
            node.put("properties", Map.of(
                    "type", app.getType().toString(),
                    "frameworks", app.getFrameworks(),
                    "endpoints", result.getEndpointsCount()
            ));
            
            nodes.add(node);
            nodeIndex.put(app.getName(), index++);
        }
        
        // Créer les nœuds pour les dépendances et les liens
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            Integer sourceIndex = nodeIndex.get(app.getName());
            
            if (app.getGlobalDependencies() != null) {
                // Bases de données
                if (app.getGlobalDependencies().getDatabases() != null) {
                    for (Dependencies.DatabaseDependency db : app.getGlobalDependencies().getDatabases()) {
                        String dbId = "DB_" + db.getDataSourceName();
                        
                        if (!nodeIndex.containsKey(dbId)) {
                            Map<String, Object> dbNode = new HashMap<>();
                            dbNode.put("id", dbId);
                            dbNode.put("name", db.getDataSourceName());
                            dbNode.put("type", "database");
                            dbNode.put("group", 2);
                            dbNode.put("properties", Map.of(
                                    "databaseType", db.getDatabaseType() != null ? db.getDatabaseType() : "Unknown",
                                    "tables", db.getTables() != null ? db.getTables().size() : 0
                            ));
                            
                            nodes.add(dbNode);
                            nodeIndex.put(dbId, index++);
                        }
                        
                        Map<String, Object> link = new HashMap<>();
                        link.put("source", sourceIndex);
                        link.put("target", nodeIndex.get(dbId));
                        link.put("value", 1);
                        link.put("type", "uses_database");
                        links.add(link);
                    }
                }
                
                // EJB
                if (app.getGlobalDependencies().getEjbs() != null) {
                    for (Dependencies.EJBDependency ejb : app.getGlobalDependencies().getEjbs()) {
                        String ejbId = "EJB_" + ejb.getEjbName();
                        
                        if (!nodeIndex.containsKey(ejbId)) {
                            Map<String, Object> ejbNode = new HashMap<>();
                            ejbNode.put("id", ejbId);
                            ejbNode.put("name", ejb.getEjbName());
                            ejbNode.put("type", "ejb");
                            ejbNode.put("group", 3);
                            ejbNode.put("properties", Map.of(
                                    "isLocal", ejb.isLocal(),
                                    "isStateless", ejb.isStateless(),
                                    "version", ejb.getVersion() != null ? ejb.getVersion() : "Unknown"
                            ));
                            
                            nodes.add(ejbNode);
                            nodeIndex.put(ejbId, index++);
                        }
                        
                        Map<String, Object> link = new HashMap<>();
                        link.put("source", sourceIndex);
                        link.put("target", nodeIndex.get(ejbId));
                        link.put("value", 1);
                        link.put("type", "uses_ejb");
                        links.add(link);
                    }
                }
                
                // Cobol
                if (app.getGlobalDependencies().getCobolPrograms() != null) {
                    for (Dependencies.CobolDependency cobol : app.getGlobalDependencies().getCobolPrograms()) {
                        String cobolId = "COBOL_" + cobol.getProgramName();
                        
                        if (!nodeIndex.containsKey(cobolId)) {
                            Map<String, Object> cobolNode = new HashMap<>();
                            cobolNode.put("id", cobolId);
                            cobolNode.put("name", cobol.getProgramName());
                            cobolNode.put("type", "cobol");
                            cobolNode.put("group", 4);
                            cobolNode.put("properties", Map.of(
                                    "connectionType", cobol.getConnectionType(),
                                    "host", cobol.getHost() != null ? cobol.getHost() : "Unknown"
                            ));
                            
                            nodes.add(cobolNode);
                            nodeIndex.put(cobolId, index++);
                        }
                        
                        Map<String, Object> link = new HashMap<>();
                        link.put("source", sourceIndex);
                        link.put("target", nodeIndex.get(cobolId));
                        link.put("value", 1);
                        link.put("type", "uses_cobol");
                        links.add(link);
                    }
                }
                
                // Web Services
                if (app.getGlobalDependencies().getWebServices() != null) {
                    for (Dependencies.WebServiceDependency ws : app.getGlobalDependencies().getWebServices()) {
                        String wsId = "WS_" + ws.getServiceName();
                        
                        if (!nodeIndex.containsKey(wsId)) {
                            Map<String, Object> wsNode = new HashMap<>();
                            wsNode.put("id", wsId);
                            wsNode.put("name", ws.getServiceName());
                            wsNode.put("type", "webservice");
                            wsNode.put("group", 5);
                            wsNode.put("properties", Map.of(
                                    "serviceType", ws.getType(),
                                    "url", ws.getUrl() != null ? ws.getUrl() : "Unknown"
                            ));
                            
                            nodes.add(wsNode);
                            nodeIndex.put(wsId, index++);
                        }
                        
                        Map<String, Object> link = new HashMap<>();
                        link.put("source", sourceIndex);
                        link.put("target", nodeIndex.get(wsId));
                        link.put("value", 1);
                        link.put("type", "uses_webservice");
                        links.add(link);
                    }
                }
            }
        }
        
        graph.put("nodes", nodes);
        graph.put("links", links);
        
        Path jsonFile = outputDir.resolve("dependency-graph-d3.json");
        objectMapper.writeValue(jsonFile.toFile(), graph);
    }
    
    private void generateInteractiveHTML(List<AnalysisResult> results, Path outputDir) throws IOException {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>Legacy Application Dependencies - Interactive Graph</title>
                    <script src="https://d3js.org/d3.v7.min.js"></script>
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            margin: 0;
                            padding: 20px;
                            background-color: #f5f5f5;
                        }
                        
                        #graph-container {
                            background-color: white;
                            border: 1px solid #ddd;
                            border-radius: 8px;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        }
                        
                        .node {
                            stroke: #fff;
                            stroke-width: 2px;
                            cursor: pointer;
                        }
                        
                        .node.application { fill: #4CAF50; }
                        .node.database { fill: #2196F3; }
                        .node.ejb { fill: #FF9800; }
                        .node.cobol { fill: #F44336; }
                        .node.webservice { fill: #9C27B0; }
                        .node.jms { fill: #00BCD4; }
                        .node.file { fill: #795548; }
                        
                        .link {
                            stroke: #999;
                            stroke-opacity: 0.6;
                            stroke-width: 2px;
                        }
                        
                        .node-label {
                            font-size: 12px;
                            pointer-events: none;
                        }
                        
                        .tooltip {
                            position: absolute;
                            text-align: left;
                            padding: 10px;
                            font-size: 12px;
                            background: rgba(0, 0, 0, 0.8);
                            color: white;
                            border-radius: 4px;
                            pointer-events: none;
                            opacity: 0;
                            transition: opacity 0.3s;
                        }
                        
                        .legend {
                            position: absolute;
                            top: 20px;
                            right: 20px;
                            background: white;
                            padding: 10px;
                            border: 1px solid #ddd;
                            border-radius: 4px;
                        }
                        
                        .legend-item {
                            margin: 5px 0;
                        }
                        
                        .legend-color {
                            display: inline-block;
                            width: 20px;
                            height: 20px;
                            margin-right: 5px;
                            vertical-align: middle;
                            border: 1px solid #333;
                        }
                        
                        h1 {
                            color: #333;
                            text-align: center;
                        }
                        
                        .controls {
                            text-align: center;
                            margin: 20px 0;
                        }
                        
                        button {
                            background-color: #4CAF50;
                            color: white;
                            border: none;
                            padding: 10px 20px;
                            margin: 0 5px;
                            border-radius: 4px;
                            cursor: pointer;
                            font-size: 14px;
                        }
                        
                        button:hover {
                            background-color: #45a049;
                        }
                    </style>
                </head>
                <body>
                    <h1>Legacy Application Dependencies - Interactive Graph</h1>
                    
                    <div class="controls">
                        <button onclick="resetZoom()">Reset Zoom</button>
                        <button onclick="toggleSimulation()">Toggle Animation</button>
                        <button onclick="exportSVG()">Export SVG</button>
                    </div>
                    
                    <div id="graph-container"></div>
                    
                    <div class="legend">
                        <h3>Legend</h3>
                        <div class="legend-item">
                            <span class="legend-color" style="background-color: #4CAF50;"></span>
                            Application
                        </div>
                        <div class="legend-item">
                            <span class="legend-color" style="background-color: #2196F3;"></span>
                            Database
                        </div>
                        <div class="legend-item">
                            <span class="legend-color" style="background-color: #FF9800;"></span>
                            EJB Component
                        </div>
                        <div class="legend-item">
                            <span class="legend-color" style="background-color: #F44336;"></span>
                            COBOL Program
                        </div>
                        <div class="legend-item">
                            <span class="legend-color" style="background-color: #9C27B0;"></span>
                            Web Service
                        </div>
                    </div>
                    
                    <div class="tooltip"></div>
                    
                    <script>
                        // Charger les données
                        d3.json('dependency-graph-d3.json').then(function(graph) {
                            const width = window.innerWidth - 40;
                            const height = 600;
                            
                            // Créer le SVG
                            const svg = d3.select('#graph-container')
                                .append('svg')
                                .attr('width', width)
                                .attr('height', height);
                            
                            // Créer un groupe pour le zoom
                            const g = svg.append('g');
                            
                            // Tooltip
                            const tooltip = d3.select('.tooltip');
                            
                            // Créer la simulation
                            const simulation = d3.forceSimulation(graph.nodes)
                                .force('link', d3.forceLink(graph.links).id(d => d.id).distance(100))
                                .force('charge', d3.forceManyBody().strength(-300))
                                .force('center', d3.forceCenter(width / 2, height / 2))
                                .force('collision', d3.forceCollide().radius(30));
                            
                            // Créer les liens
                            const link = g.append('g')
                                .selectAll('line')
                                .data(graph.links)
                                .join('line')
                                .attr('class', 'link');
                            
                            // Créer les nœuds
                            const node = g.append('g')
                                .selectAll('g')
                                .data(graph.nodes)
                                .join('g')
                                .call(drag(simulation));
                            
                            // Ajouter les cercles
                            node.append('circle')
                                .attr('class', d => 'node ' + d.type)
                                .attr('r', d => d.type === 'application' ? 25 : 20)
                                .on('mouseover', function(event, d) {
                                    tooltip.transition()
                                        .duration(200)
                                        .style('opacity', .9);
                                    
                                    let content = '<strong>' + d.name + '</strong><br/>' +
                                                 'Type: ' + d.type + '<br/>';
                                    
                                    if (d.properties) {
                                        Object.entries(d.properties).forEach(([key, value]) => {
                                            content += key + ': ' + value + '<br/>';
                                        });
                                    }
                                    
                                    tooltip.html(content)
                                        .style('left', (event.pageX + 10) + 'px')
                                        .style('top', (event.pageY - 28) + 'px');
                                })
                                .on('mouseout', function(d) {
                                    tooltip.transition()
                                        .duration(500)
                                        .style('opacity', 0);
                                });
                            
                            // Ajouter les labels
                            node.append('text')
                                .attr('class', 'node-label')
                                .attr('dy', '.35em')
                                .attr('text-anchor', 'middle')
                                .text(d => d.name.length > 15 ? d.name.substring(0, 15) + '...' : d.name);
                            
                            // Mise à jour de la simulation
                            simulation.on('tick', () => {
                                link
                                    .attr('x1', d => d.source.x)
                                    .attr('y1', d => d.source.y)
                                    .attr('x2', d => d.target.x)
                                    .attr('y2', d => d.target.y);
                                
                                node
                                    .attr('transform', d => `translate(${d.x},${d.y})`);
                            });
                            
                            // Zoom
                            const zoom = d3.zoom()
                                .scaleExtent([0.1, 10])
                                .on('zoom', (event) => {
                                    g.attr('transform', event.transform);
                                });
                            
                            svg.call(zoom);
                            
                            // Fonctions de drag
                            function drag(simulation) {
                                function dragstarted(event) {
                                    if (!event.active) simulation.alphaTarget(0.3).restart();
                                    event.subject.fx = event.subject.x;
                                    event.subject.fy = event.subject.y;
                                }
                                
                                function dragged(event) {
                                    event.subject.fx = event.x;
                                    event.subject.fy = event.y;
                                }
                                
                                function dragended(event) {
                                    if (!event.active) simulation.alphaTarget(0);
                                    event.subject.fx = null;
                                    event.subject.fy = null;
                                }
                                
                                return d3.drag()
                                    .on('start', dragstarted)
                                    .on('drag', dragged)
                                    .on('end', dragended);
                            }
                            
                            // Fonctions globales
                            window.resetZoom = function() {
                                svg.transition()
                                    .duration(750)
                                    .call(zoom.transform, d3.zoomIdentity);
                            };
                            
                            window.toggleSimulation = function() {
                                if (simulation.alpha() > 0) {
                                    simulation.stop();
                                } else {
                                    simulation.alpha(1).restart();
                                }
                            };
                            
                            window.exportSVG = function() {
                                const svgData = new XMLSerializer().serializeToString(svg.node());
                                const blob = new Blob([svgData], {type: 'image/svg+xml;charset=utf-8'});
                                const url = URL.createObjectURL(blob);
                                const link = document.createElement('a');
                                link.href = url;
                                link.download = 'dependency-graph.svg';
                                document.body.appendChild(link);
                                link.click();
                                document.body.removeChild(link);
                            };
                        });
                    </script>
                </body>
                </html>
                """;
        
        Path htmlFile = outputDir.resolve("dependency-graph-interactive.html");
        Files.writeString(htmlFile, html);
    }
    
    // Méthodes pour générer des graphiques spécialisés par type de dépendance
    
    private void generateDatabaseDependencyGraph(List<AnalysisResult> results, Path outputDir) 
            throws IOException {
        StringBuilder mermaid = new StringBuilder();
        mermaid.append("graph LR\n");
        mermaid.append("    classDef app fill:#f9f,stroke:#333,stroke-width:4px\n");
        mermaid.append("    classDef db fill:#bbf,stroke:#333,stroke-width:2px\n");
        mermaid.append("    classDef table fill:#ddf,stroke:#333,stroke-width:1px\n\n");
        
        Map<String, Set<String>> dbToTables = new HashMap<>();
        Map<String, Set<String>> appToDb = new HashMap<>();
        
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            String appId = sanitizeId(app.getName());
            Set<String> appDatabases = new HashSet<>();
            
            if (app.getGlobalDependencies() != null && 
                app.getGlobalDependencies().getDatabases() != null) {
                
                for (Dependencies.DatabaseDependency db : app.getGlobalDependencies().getDatabases()) {
                    String dbId = sanitizeId("DB_" + db.getDataSourceName());
                    appDatabases.add(dbId);
                    
                    if (db.getTables() != null) {
                        dbToTables.computeIfAbsent(dbId, k -> new HashSet<>())
                                  .addAll(db.getTables());
                    }
                }
            }
            
            if (!appDatabases.isEmpty()) {
                appToDb.put(appId, appDatabases);
            }
        }
        
        // Générer les nœuds et relations
        for (Map.Entry<String, Set<String>> entry : appToDb.entrySet()) {
            String appId = entry.getKey();
            mermaid.append("    ").append(appId).append("[").append(appId).append("]:::app\n");
            
            for (String dbId : entry.getValue()) {
                mermaid.append("    ").append(dbId).append("[(").append(dbId).append(")]:::db\n");
                mermaid.append("    ").append(appId).append(" --> ").append(dbId).append("\n");
                
                // Ajouter les tables si disponibles
                Set<String> tables = dbToTables.get(dbId);
                if (tables != null && !tables.isEmpty()) {
                    for (String table : tables) {
                        String tableId = sanitizeId(dbId + "_" + table);
                        mermaid.append("    ").append(tableId).append("[").append(table).append("]:::table\n");
                        mermaid.append("    ").append(dbId).append(" --> ").append(tableId).append("\n");
                    }
                }
            }
        }
        
        Path dbGraphFile = outputDir.resolve("database-dependency-graph.mmd");
        Files.writeString(dbGraphFile, mermaid.toString());
    }
    
    private void generateEJBDependencyGraph(List<AnalysisResult> results, Path outputDir) 
            throws IOException {
        // Similaire mais pour les EJB
        // Code similaire adapté pour les EJB...
    }
    
    private void generateWebServiceDependencyGraph(List<AnalysisResult> results, Path outputDir) 
            throws IOException {
        // Similaire mais pour les Web Services
        // Code similaire adapté pour les Web Services...
    }
    
    private void generateCobolDependencyGraph(List<AnalysisResult> results, Path outputDir) 
            throws IOException {
        // Similaire mais pour les connexions Cobol
        // Code similaire adapté pour Cobol...
    }
    
    // Classes et méthodes utilitaires
    
    private static class DependencyNode {
        String id;
        String label;
        String type;
        
        DependencyNode(String id, String label, String type) {
            this.id = id;
            this.label = label;
            this.type = type;
        }
    }
    
    private Map<String, Set<DependencyNode>> groupDependencies(List<AnalysisResult> results) {
        Map<String, Set<DependencyNode>> groups = new HashMap<>();
        
        for (AnalysisResult result : results) {
            if (!result.isSuccess() || result.getApplication().getGlobalDependencies() == null) 
                continue;
            
            Dependencies deps = result.getApplication().getGlobalDependencies();
            
            // Bases de données
            if (deps.getDatabases() != null) {
                Set<DependencyNode> dbNodes = groups.computeIfAbsent("database", k -> new HashSet<>());
                for (Dependencies.DatabaseDependency db : deps.getDatabases()) {
                    dbNodes.add(new DependencyNode(
                            sanitizeId("DB_" + db.getDataSourceName()),
                            db.getDataSourceName() + "\\n" + db.getDatabaseType(),
                            "database"
                    ));
                }
            }
            
            // EJB
            if (deps.getEjbs() != null) {
                Set<DependencyNode> ejbNodes = groups.computeIfAbsent("ejb", k -> new HashSet<>());
                for (Dependencies.EJBDependency ejb : deps.getEjbs()) {
                    ejbNodes.add(new DependencyNode(
                            sanitizeId("EJB_" + ejb.getEjbName()),
                            ejb.getEjbName() + "\\n" + (ejb.isLocal() ? "Local" : "Remote"),
                            "ejb"
                    ));
                }
            }
            
            // Cobol
            if (deps.getCobolPrograms() != null) {
                Set<DependencyNode> cobolNodes = groups.computeIfAbsent("cobol", k -> new HashSet<>());
                for (Dependencies.CobolDependency cobol : deps.getCobolPrograms()) {
                    cobolNodes.add(new DependencyNode(
                            sanitizeId("COBOL_" + cobol.getProgramName()),
                            cobol.getProgramName() + "\\n" + cobol.getConnectionType(),
                            "cobol"
                    ));
                }
            }
        }
        
        return groups;
    }
    
    private void addDotRelations(WebLogicApplication app, String appId, StringBuilder dot) {
        Dependencies deps = app.getGlobalDependencies();
        
        if (deps.getDatabases() != null) {
            for (Dependencies.DatabaseDependency db : deps.getDatabases()) {
                String dbId = sanitizeId("DB_" + db.getDataSourceName());
                dot.append("    ").append(appId).append(" -> ").append(dbId).append(";\n");
            }
        }
        
        if (deps.getEjbs() != null) {
            for (Dependencies.EJBDependency ejb : deps.getEjbs()) {
                String ejbId = sanitizeId("EJB_" + ejb.getEjbName());
                dot.append("    ").append(appId).append(" -> ").append(ejbId).append(";\n");
            }
        }
        
        if (deps.getCobolPrograms() != null) {
            for (Dependencies.CobolDependency cobol : deps.getCobolPrograms()) {
                String cobolId = sanitizeId("COBOL_" + cobol.getProgramName());
                dot.append("    ").append(appId).append(" -> ").append(cobolId)
                   .append(" [style=dashed];\n");
            }
        }
    }
    
    private void addInterApplicationLinks(List<AnalysisResult> results, StringBuilder mermaid) {
        // Analyser les dépendances inter-applications
        // Basé sur les noms d'EJB et de Web Services
        
        Map<String, String> componentToApp = new HashMap<>();
        
        // Construire une map des composants vers les applications
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            String appName = app.getName().toLowerCase();
            
            // Indexer les endpoints par nom de classe
            if (app.getEndpoints() != null) {
                for (Endpoint endpoint : app.getEndpoints()) {
                    if (endpoint.getClassName() != null) {
                        componentToApp.put(endpoint.getClassName().toLowerCase(), app.getName());
                    }
                }
            }
        }
        
        // Détecter les liens inter-applications
        for (AnalysisResult result : results) {
            if (!result.isSuccess()) continue;
            
            WebLogicApplication app = result.getApplication();
            String sourceAppId = sanitizeId(app.getName());
            
            if (app.getGlobalDependencies() != null) {
                // Vérifier les EJB
                if (app.getGlobalDependencies().getEjbs() != null) {
                    for (Dependencies.EJBDependency ejb : app.getGlobalDependencies().getEjbs()) {
                        String ejbNameLower = ejb.getEjbName().toLowerCase();
                        
                        for (Map.Entry<String, String> entry : componentToApp.entrySet()) {
                            if (ejbNameLower.contains(entry.getKey()) && 
                                !entry.getValue().equals(app.getName())) {
                                String targetAppId = sanitizeId(entry.getValue());
                                mermaid.append("    ").append(sourceAppId).append(" -.->|EJB| ")
                                       .append(targetAppId).append("\n");
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
    
    private String sanitizeId(String id) {
        return id.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
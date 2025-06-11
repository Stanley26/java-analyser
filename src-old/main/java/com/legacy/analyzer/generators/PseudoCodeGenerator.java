package com.legacy.analyzer.generators;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.legacy.analyzer.model.Endpoint;
import com.legacy.analyzer.model.WebLogicApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PseudoCodeGenerator {
    
    private final JavaParser javaParser = new JavaParser();
    
    public void generatePseudoCode(WebLogicApplication application) throws IOException {
        log.info("Génération du pseudo-code pour l'application: {}", application.getName());
        
        // Générer le pseudo-code pour chaque endpoint
        if (application.getEndpoints() != null) {
            for (Endpoint endpoint : application.getEndpoints()) {
                generateEndpointPseudoCode(endpoint, application);
            }
        }
        
        // Générer aussi pour les modules
        if (application.getModules() != null) {
            for (WebLogicApplication.Module module : application.getModules()) {
                if (module.getEndpoints() != null) {
                    for (Endpoint endpoint : module.getEndpoints()) {
                        generateEndpointPseudoCode(endpoint, application);
                    }
                }
            }
        }
    }
    
    private void generateEndpointPseudoCode(Endpoint endpoint, WebLogicApplication application) {
        if (endpoint.getSourceLocation() == null || endpoint.getSourceLocation().getFilePath() == null) {
            log.debug("Pas de localisation source pour l'endpoint: {}", endpoint.getId());
            return;
        }
        
        try {
            Path sourceFile = Paths.get(endpoint.getSourceLocation().getFilePath());
            if (!Files.exists(sourceFile)) {
                // Essayer de trouver le fichier relatif au path extrait
                sourceFile = findSourceFile(application, endpoint);
                if (sourceFile == null || !Files.exists(sourceFile)) {
                    log.debug("Fichier source introuvable: {}", endpoint.getSourceLocation().getFilePath());
                    return;
                }
            }
            
            ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceFile);
            if (!parseResult.isSuccessful()) {
                log.warn("Impossible de parser le fichier source: {}", sourceFile);
                return;
            }
            
            CompilationUnit cu = parseResult.getResult().orElse(null);
            if (cu == null) return;
            
            // Trouver la méthode correspondante
            Optional<MethodDeclaration> method = findMethod(cu, endpoint);
            
            if (method.isPresent()) {
                Endpoint.PseudoCode pseudoCode = generateMethodPseudoCode(method.get());
                endpoint.setPseudoCode(pseudoCode);
            }
            
        } catch (Exception e) {
            log.error("Erreur lors de la génération du pseudo-code pour l'endpoint: {}", 
                     endpoint.getId(), e);
        }
    }
    
    private Path findSourceFile(WebLogicApplication application, Endpoint endpoint) {
        // Essayer différents chemins possibles
        String relativePath = endpoint.getSourceLocation().getFilePath();
        
        // Retirer le chemin absolu et garder seulement la partie relative
        if (relativePath.contains("WEB-INF/classes/")) {
            relativePath = relativePath.substring(relativePath.indexOf("WEB-INF/classes/") + 16);
        } else if (relativePath.contains("src/main/java/")) {
            relativePath = relativePath.substring(relativePath.indexOf("src/main/java/") + 14);
        } else if (relativePath.contains("src/")) {
            relativePath = relativePath.substring(relativePath.indexOf("src/") + 4);
        }
        
        // Essayer différents emplacements
        Path extractedPath = application.getExtractedPath();
        if (extractedPath != null) {
            Path[] possiblePaths = {
                extractedPath.resolve("WEB-INF/classes").resolve(relativePath),
                extractedPath.resolve("src/main/java").resolve(relativePath),
                extractedPath.resolve("src").resolve(relativePath),
                extractedPath.resolve(relativePath)
            };
            
            for (Path path : possiblePaths) {
                if (Files.exists(path)) {
                    return path;
                }
            }
        }
        
        return null;
    }
    
    private Optional<MethodDeclaration> findMethod(CompilationUnit cu, Endpoint endpoint) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> {
                    // Vérifier le nom de la méthode
                    if (!method.getNameAsString().equals(endpoint.getMethodName())) {
                        return false;
                    }
                    
                    // Vérifier la ligne si disponible
                    if (endpoint.getSourceLocation().getStartLine() != null) {
                        int methodLine = method.getBegin().map(pos -> pos.line).orElse(0);
                        return Math.abs(methodLine - endpoint.getSourceLocation().getStartLine()) < 5;
                    }
                    
                    return true;
                })
                .findFirst();
    }
    
    private Endpoint.PseudoCode generateMethodPseudoCode(MethodDeclaration method) {
        PseudoCodeBuilder builder = new PseudoCodeBuilder();
        
        // Générer le pseudo-code simplifié
        String simplified = generateSimplifiedPseudoCode(method);
        
        // Générer le pseudo-code détaillé avec blocks
        List<Endpoint.PseudoCodeBlock> blocks = generateDetailedPseudoCode(method);
        
        return Endpoint.PseudoCode.builder()
                .simplified(simplified)
                .detailed(builder.toString())
                .blocks(blocks)
                .build();
    }
    
    private String generateSimplifiedPseudoCode(MethodDeclaration method) {
        StringBuilder sb = new StringBuilder();
        sb.append("FONCTION ").append(method.getNameAsString()).append("(");
        
        // Paramètres
        method.getParameters().forEach(param -> {
            sb.append(param.getNameAsString()).append(", ");
        });
        if (!method.getParameters().isEmpty()) {
            sb.setLength(sb.length() - 2); // Enlever la dernière virgule
        }
        sb.append(")\n");
        
        // Corps simplifié
        if (method.getBody().isPresent()) {
            SimplifiedPseudoCodeVisitor visitor = new SimplifiedPseudoCodeVisitor();
            method.getBody().get().accept(visitor, null);
            sb.append(visitor.getPseudoCode());
        }
        
        sb.append("FIN FONCTION");
        
        return sb.toString();
    }
    
    private List<Endpoint.PseudoCodeBlock> generateDetailedPseudoCode(MethodDeclaration method) {
        List<Endpoint.PseudoCodeBlock> blocks = new ArrayList<>();
        
        if (method.getBody().isPresent()) {
            DetailedPseudoCodeVisitor visitor = new DetailedPseudoCodeVisitor();
            method.getBody().get().accept(visitor, null);
            blocks = visitor.getBlocks();
        }
        
        return blocks;
    }
    
    private class SimplifiedPseudoCodeVisitor extends VoidVisitorAdapter<Void> {
        private final StringBuilder pseudoCode = new StringBuilder();
        private int indentLevel = 1;
        
        public String getPseudoCode() {
            return pseudoCode.toString();
        }
        
        private void indent() {
            for (int i = 0; i < indentLevel; i++) {
                pseudoCode.append("    ");
            }
        }
        
        @Override
        public void visit(IfStmt n, Void arg) {
            indent();
            pseudoCode.append("SI ").append(simplifyCondition(n.getCondition())).append(" ALORS\n");
            indentLevel++;
            n.getThenStmt().accept(this, arg);
            indentLevel--;
            
            n.getElseStmt().ifPresent(elseStmt -> {
                indent();
                pseudoCode.append("SINON\n");
                indentLevel++;
                elseStmt.accept(this, arg);
                indentLevel--;
            });
            
            indent();
            pseudoCode.append("FIN SI\n");
        }
        
        @Override
        public void visit(ForStmt n, Void arg) {
            indent();
            pseudoCode.append("POUR ");
            
            if (n.getInitialization().size() > 0) {
                pseudoCode.append(simplifyExpression(n.getInitialization().get(0)));
            }
            pseudoCode.append(" JUSQU'À ");
            n.getCompare().ifPresent(cond -> pseudoCode.append(simplifyCondition(cond)));
            pseudoCode.append("\n");
            
            indentLevel++;
            n.getBody().accept(this, arg);
            indentLevel--;
            
            indent();
            pseudoCode.append("FIN POUR\n");
        }
        
        @Override
        public void visit(ForEachStmt n, Void arg) {
            indent();
            pseudoCode.append("POUR CHAQUE ")
                     .append(n.getVariable().getNameAsString())
                     .append(" DANS ")
                     .append(simplifyExpression(n.getIterable()))
                     .append("\n");
            
            indentLevel++;
            n.getBody().accept(this, arg);
            indentLevel--;
            
            indent();
            pseudoCode.append("FIN POUR CHAQUE\n");
        }
        
        @Override
        public void visit(WhileStmt n, Void arg) {
            indent();
            pseudoCode.append("TANT QUE ").append(simplifyCondition(n.getCondition())).append("\n");
            
            indentLevel++;
            n.getBody().accept(this, arg);
            indentLevel--;
            
            indent();
            pseudoCode.append("FIN TANT QUE\n");
        }
        
        @Override
        public void visit(TryStmt n, Void arg) {
            indent();
            pseudoCode.append("ESSAYER\n");
            
            indentLevel++;
            n.getTryBlock().accept(this, arg);
            indentLevel--;
            
            n.getCatchClauses().forEach(catchClause -> {
                indent();
                pseudoCode.append("ATTRAPER ")
                         .append(catchClause.getParameter().getTypeAsString())
                         .append("\n");
                
                indentLevel++;
                catchClause.getBody().accept(this, arg);
                indentLevel--;
            });
            
            n.getFinallyBlock().ifPresent(finallyBlock -> {
                indent();
                pseudoCode.append("FINALEMENT\n");
                
                indentLevel++;
                finallyBlock.accept(this, arg);
                indentLevel--;
            });
            
            indent();
            pseudoCode.append("FIN ESSAYER\n");
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            indent();
            pseudoCode.append("APPELER ");
            
            n.getScope().ifPresent(scope -> {
                pseudoCode.append(simplifyExpression(scope)).append(".");
            });
            
            pseudoCode.append(n.getNameAsString()).append("(");
            
            for (int i = 0; i < n.getArguments().size(); i++) {
                if (i > 0) pseudoCode.append(", ");
                pseudoCode.append(simplifyExpression(n.getArgument(i)));
            }
            
            pseudoCode.append(")\n");
            
            super.visit(n, arg);
        }
        
        @Override
        public void visit(ReturnStmt n, Void arg) {
            indent();
            pseudoCode.append("RETOURNER");
            n.getExpression().ifPresent(expr -> 
                pseudoCode.append(" ").append(simplifyExpression(expr))
            );
            pseudoCode.append("\n");
        }
        
        @Override
        public void visit(AssignExpr n, Void arg) {
            indent();
            pseudoCode.append(simplifyExpression(n.getTarget()))
                     .append(" = ")
                     .append(simplifyExpression(n.getValue()))
                     .append("\n");
            
            super.visit(n, arg);
        }
        
        private String simplifyCondition(Expression expr) {
            if (expr instanceof BinaryExpr) {
                BinaryExpr binary = (BinaryExpr) expr;
                String left = simplifyExpression(binary.getLeft());
                String right = simplifyExpression(binary.getRight());
                String operator = translateOperator(binary.getOperator());
                
                return left + " " + operator + " " + right;
            } else if (expr instanceof UnaryExpr) {
                UnaryExpr unary = (UnaryExpr) expr;
                if (unary.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
                    return "NON " + simplifyExpression(unary.getExpression());
                }
            } else if (expr instanceof MethodCallExpr) {
                MethodCallExpr call = (MethodCallExpr) expr;
                return simplifyMethodCall(call);
            }
            
            return simplifyExpression(expr);
        }
        
        private String simplifyExpression(Expression expr) {
            if (expr instanceof NameExpr) {
                return ((NameExpr) expr).getNameAsString();
            } else if (expr instanceof StringLiteralExpr) {
                return "\"" + ((StringLiteralExpr) expr).getValue() + "\"";
            } else if (expr instanceof IntegerLiteralExpr) {
                return expr.toString();
            } else if (expr instanceof BooleanLiteralExpr) {
                return ((BooleanLiteralExpr) expr).getValue() ? "VRAI" : "FAUX";
            } else if (expr instanceof NullLiteralExpr) {
                return "NULL";
            } else if (expr instanceof FieldAccessExpr) {
                FieldAccessExpr field = (FieldAccessExpr) expr;
                return simplifyExpression(field.getScope()) + "." + field.getNameAsString();
            } else if (expr instanceof MethodCallExpr) {
                return simplifyMethodCall((MethodCallExpr) expr);
            }
            
            return expr.toString();
        }
        
        private String simplifyExpression(Statement stmt) {
            if (stmt instanceof ExpressionStmt) {
                return simplifyExpression(((ExpressionStmt) stmt).getExpression());
            }
            return stmt.toString();
        }
        
        private String simplifyMethodCall(MethodCallExpr call) {
            StringBuilder sb = new StringBuilder();
            
            call.getScope().ifPresent(scope -> {
                sb.append(simplifyExpression(scope)).append(".");
            });
            
            sb.append(translateMethodName(call.getNameAsString()));
            
            // Simplifier les arguments pour certaines méthodes communes
            String methodName = call.getNameAsString();
            if (isCommonMethod(methodName)) {
                sb.append("()");
            } else {
                sb.append("(...)");
            }
            
            return sb.toString();
        }
        
        private String translateOperator(BinaryExpr.Operator op) {
            switch (op) {
                case EQUALS: return "ÉGAL À";
                case NOT_EQUALS: return "DIFFÉRENT DE";
                case LESS: return "INFÉRIEUR À";
                case GREATER: return "SUPÉRIEUR À";
                case LESS_EQUALS: return "INFÉRIEUR OU ÉGAL À";
                case GREATER_EQUALS: return "SUPÉRIEUR OU ÉGAL À";
                case AND: return "ET";
                case OR: return "OU";
                case PLUS: return "+";
                case MINUS: return "-";
                case MULTIPLY: return "*";
                case DIVIDE: return "/";
                default: return op.toString();
            }
        }
        
        private String translateMethodName(String methodName) {
            // Traduire les méthodes communes
            switch (methodName) {
                case "equals": return "estÉgalÀ";
                case "isEmpty": return "estVide";
                case "contains": return "contient";
                case "get": return "obtenir";
                case "set": return "définir";
                case "add": return "ajouter";
                case "remove": return "supprimer";
                case "save": return "sauvegarder";
                case "delete": return "supprimer";
                case "find": return "trouver";
                case "search": return "chercher";
                default: return methodName;
            }
        }
        
        private boolean isCommonMethod(String methodName) {
            return methodName.equals("isEmpty") || 
                   methodName.equals("size") ||
                   methodName.equals("length") ||
                   methodName.equals("trim");
        }
    }
    
    private class DetailedPseudoCodeVisitor extends VoidVisitorAdapter<Void> {
        private final List<Endpoint.PseudoCodeBlock> blocks = new ArrayList<>();
        private final Stack<Endpoint.PseudoCodeBlock> blockStack = new Stack<>();
        
        public List<Endpoint.PseudoCodeBlock> getBlocks() {
            return blocks;
        }
        
        @Override
        public void visit(IfStmt n, Void arg) {
            Endpoint.PseudoCodeBlock ifBlock = Endpoint.PseudoCodeBlock.builder()
                    .type("CONDITION")
                    .content("SI " + simplifyCondition(n.getCondition()))
                    .startLine(n.getBegin().map(pos -> pos.line).orElse(0))
                    .endLine(n.getEnd().map(pos -> pos.line).orElse(0))
                    .children(new ArrayList<>())
                    .build();
            
            addBlock(ifBlock);
            blockStack.push(ifBlock);
            
            n.getThenStmt().accept(this, arg);
            
            n.getElseStmt().ifPresent(elseStmt -> {
                Endpoint.PseudoCodeBlock elseBlock = Endpoint.PseudoCodeBlock.builder()
                        .type("CONDITION")
                        .content("SINON")
                        .children(new ArrayList<>())
                        .build();
                
                ifBlock.getChildren().add(elseBlock);
                blockStack.push(elseBlock);
                elseStmt.accept(this, arg);
                blockStack.pop();
            });
            
            blockStack.pop();
        }
        
        @Override
        public void visit(ForStmt n, Void arg) {
            String loopContent = "POUR " + 
                                (n.getInitialization().isEmpty() ? "" : 
                                 simplifyExpression(n.getInitialization().get(0))) +
                                " JUSQU'À " + 
                                n.getCompare().map(this::simplifyCondition).orElse("");
            
            Endpoint.PseudoCodeBlock loopBlock = Endpoint.PseudoCodeBlock.builder()
                    .type("LOOP")
                    .content(loopContent)
                    .startLine(n.getBegin().map(pos -> pos.line).orElse(0))
                    .endLine(n.getEnd().map(pos -> pos.line).orElse(0))
                    .children(new ArrayList<>())
                    .build();
            
            addBlock(loopBlock);
            blockStack.push(loopBlock);
            n.getBody().accept(this, arg);
            blockStack.pop();
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            String callContent = "APPELER ";
            
            n.getScope().ifPresent(scope -> {
                callContent += simplifyExpression(scope) + ".";
            });
            
            callContent += n.getNameAsString();
            
            Endpoint.PseudoCodeBlock callBlock = Endpoint.PseudoCodeBlock.builder()
                    .type("CALL")
                    .content(callContent)
                    .startLine(n.getBegin().map(pos -> pos.line).orElse(0))
                    .endLine(n.getEnd().map(pos -> pos.line).orElse(0))
                    .build();
            
            addBlock(callBlock);
            
            super.visit(n, arg);
        }
        
        private void addBlock(Endpoint.PseudoCodeBlock block) {
            if (blockStack.isEmpty()) {
                blocks.add(block);
            } else {
                blockStack.peek().getChildren().add(block);
            }
        }
        
        private String simplifyCondition(Expression expr) {
            // Réutiliser la logique de SimplifiedPseudoCodeVisitor
            return new SimplifiedPseudoCodeVisitor().simplifyCondition(expr);
        }
        
        private String simplifyExpression(Expression expr) {
            return new SimplifiedPseudoCodeVisitor().simplifyExpression(expr);
        }
        
        private String simplifyExpression(Statement stmt) {
            return new SimplifiedPseudoCodeVisitor().simplifyExpression(stmt);
        }
    }
    
    private static class PseudoCodeBuilder {
        private final StringBuilder code = new StringBuilder();
        private int indentLevel = 0;
        
        public void indent() {
            for (int i = 0; i < indentLevel; i++) {
                code.append("    ");
            }
        }
        
        public void increaseIndent() {
            indentLevel++;
        }
        
        public void decreaseIndent() {
            if (indentLevel > 0) {
                indentLevel--;
            }
        }
        
        public void appendLine(String line) {
            indent();
            code.append(line).append("\n");
        }
        
        @Override
        public String toString() {
            return code.toString();
        }
    }
}
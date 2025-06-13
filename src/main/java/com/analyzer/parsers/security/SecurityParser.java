package com.analyzer.parsers.security;

import com.analyzer.model.technical.ExternalCall;
import com.analyzer.model.technical.SecurityRule;
import com.analyzer.parsers.common.DependencyParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un parseur de dépendances spécialisé dans la détection des règles de sécurité
 * définies par les annotations de Spring Security.
 */
public class SecurityParser implements DependencyParser {

    // Regex pour extraire les rôles d'une expression comme "hasRole('ROLE_ADMIN')"
    private static final Pattern ROLE_PATTERN = Pattern.compile("hasRole\\s*\\(\\s*'([^']*)'\\s*\\)");

    @Override
    public List<ExternalCall> findDependencies(MethodDeclaration method, TypeDeclaration<?> enclosingClass) {
        // Ce parseur ne retourne pas d'appels "externes", mais il est plus simple de le garder
        // dans la même famille de parseurs. On pourrait aussi créer une autre interface.
        return new ArrayList<>();
    }

    /**
     * Méthode spécifique pour trouver les règles de sécurité.
     * @param method La méthode à analyser.
     * @return Une liste de SecurityRule trouvées.
     */
    public List<SecurityRule> findSecurityRules(MethodDeclaration method) {
        List<SecurityRule> rules = new ArrayList<>();

        // Gérer @PreAuthorize et @PostAuthorize
        method.getAnnotationByName("PreAuthorize").ifPresent(annotation -> rules.add(createRuleFromAnnotation(annotation)));
        method.getAnnotationByName("PostAuthorize").ifPresent(annotation -> rules.add(createRuleFromAnnotation(annotation)));

        // Gérer @Secured
        method.getAnnotationByName("Secured").ifPresent(annotation -> rules.add(createRuleFromAnnotation(annotation)));

        return rules;
    }

    private SecurityRule createRuleFromAnnotation(AnnotationExpr annotation) {
        SecurityRule rule = new SecurityRule();
        String expression = annotation.toString();
        
        // Enlève le nom de l'annotation pour ne garder que l'expression.
        // ex: @PreAuthorize("hasRole('ADMIN')") -> "hasRole('ADMIN')"
        rule.expression = expression.substring(annotation.getNameAsString().length()).replace("(", "").replace(")", "").replace("\"", "").trim();

        // Tenter d'extraire les rôles
        Matcher matcher = ROLE_PATTERN.matcher(rule.expression);
        while (matcher.find()) {
            rule.requiredRoles.add(matcher.group(1));
        }
        return rule;
    }
}

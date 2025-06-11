package com.analyzer.parsers.common;

import com.analyzer.model.technical.EjbCall;
import com.analyzer.model.technical.ExternalCall;
import com.analyzer.model.technical.SourceLocation;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.util.ArrayList;
import java.util.List;

public class EjbParser implements DependencyParser {

    @Override
    public List<ExternalCall> findDependencies(MethodDeclaration method, TypeDeclaration<?> enclosingClass) {
        List<ExternalCall> dependencies = new ArrayList<>();

        // Trouve tous les appels de méthode dans le corps de la méthode analysée
        method.findAll(MethodCallExpr.class).forEach(methodCall -> {
            // Nous cherchons spécifiquement la méthode "lookup"
            if (methodCall.getNameAsString().equals("lookup")) {
                // Vérifie si l'appel a au moins un argument
                if (!methodCall.getArguments().isEmpty()) {
                    methodCall.getArgument(0).ifStringLiteralExpr(jndiString -> {
                        EjbCall ejbCall = new EjbCall();
                        ejbCall.jndiName = jndiString.asString();

                        SourceLocation location = new SourceLocation();
                        enclosingClass.getFullyQualifiedName().ifPresent(name -> {
                            location.file = name.replace('.', '/') + ".java";
                        });
                        methodCall.getBegin().ifPresent(p -> location.lineNumber = p.line);
                        ejbCall.sourceLocation = location;

                        dependencies.add(ejbCall);
                    });
                }
            }
        });

        return dependencies;
    }
}

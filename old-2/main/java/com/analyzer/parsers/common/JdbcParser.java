package com.analyzer.parsers.common;

import com.analyzer.model.technical.DatabaseCall;
import com.analyzer.model.technical.ExternalCall;
import com.analyzer.model.technical.SourceLocation;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JdbcParser implements DependencyParser {

    private static final Pattern SQL_KEYWORD_PATTERN =
        Pattern.compile("\\b(SELECT|INSERT|UPDATE|DELETE|CALL)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public List<ExternalCall> findDependencies(MethodDeclaration method, TypeDeclaration<?> enclosingClass) {
        List<ExternalCall> dependencies = new ArrayList<>();

        method.findAll(StringLiteralExpr.class).forEach(literal -> {
            String text = literal.getValue();
            Matcher matcher = SQL_KEYWORD_PATTERN.matcher(text);

            if (matcher.find()) {
                DatabaseCall dbCall = new DatabaseCall();
                dbCall.query = text;

                SourceLocation location = new SourceLocation();
                // On peut maintenant trouver le fichier source depuis la classe
                enclosingClass.getFullyQualifiedName().ifPresent(name -> {
                    location.file = name.replace('.', '/') + ".java";
                });
                literal.getBegin().ifPresent(p -> location.lineNumber = p.line);
                dbCall.sourceLocation = location;
                
                dependencies.add(dbCall);
            }
        });
        return dependencies;
    }
}

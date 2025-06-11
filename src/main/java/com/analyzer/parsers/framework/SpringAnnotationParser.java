package com.analyzer.parsers.framework;

// ... (imports)
import com.analyzer.model.technical.Endpoint;
import com.analyzer.parsers.common.EntryPointParser;
import java.util.ArrayList;
import java.util.List;

public class SpringAnnotationParser implements EntryPointParser {

    @Override
    public boolean supports(File file) {
        return file.getName().endsWith(".java");
    }

    @Override
    public List<Endpoint> parse(File javaFile, Path projectRoot) {
        List<Endpoint> foundEndpoints = new ArrayList<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

            for (ClassOrInterfaceDeclaration clas : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (isSpringController(clas)) {
                    String baseClassPath = clas.getAnnotationByName("RequestMapping")
                            .map(this::extractPathFromAnnotation).orElse("");
                    
                    String fullClassName = packageName + "." + clas.getNameAsString();

                    for (MethodDeclaration method : clas.getMethods()) {
                        // La méthode processMethod va maintenant retourner un Optional<Endpoint>
                        // et on l'ajoutera à la liste foundEndpoints.
                        // (La logique interne de processMethod et handleMappingAnnotation
                        // est très similaire, mais au lieu de modifier le rapport,
                        // elle construit et retourne un objet Endpoint).
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'analyse du fichier Java : " + javaFile.getAbsolutePath());
        }
        return foundEndpoints;
    }
    
    // ... (les méthodes privées comme isSpringController, extractPathFromAnnotation
    // restent très similaires mais sont maintenant utilisées pour construire
    // l'objet Endpoint qui est retourné).
}
```
*Note : L'implémentation complète est omise pour la brièveté, mais la structure est claire. Le parseur ne fait que collecter et retourne
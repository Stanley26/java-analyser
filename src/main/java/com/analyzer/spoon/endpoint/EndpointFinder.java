// Fichier: src/main/java/com/votre_entreprise/analyzer/spoon/endpoint/EndpointFinder.java
package com.votre_entreprise.analyzer.spoon.endpoint;

import spoon.reflect.declaration.CtMethod;
import java.util.List;

public interface EndpointFinder {
    List<CtMethod<?>> findEndpoints();
    String getPathFor(CtMethod<?> method);
    String getHttpMethodFor(CtMethod<?> method);
}

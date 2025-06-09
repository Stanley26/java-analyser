#!/bin/bash

# Script de lancement pour Legacy Analyzer
# Usage: ./run.sh [options]

# Détection du répertoire du script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Vérifier que Java est installé
if ! command -v java &> /dev/null; then
    echo "ERREUR: Java n'est pas installé ou n'est pas dans le PATH"
    echo "Veuillez installer Java 17 ou supérieur"
    exit 1
fi

# Vérifier la version de Java
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "ERREUR: Java 17 ou supérieur est requis (version actuelle: $JAVA_VERSION)"
    exit 1
fi

# Options JVM
JVM_OPTS="-Xmx4g -Xms1g"
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"
JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=200"
JVM_OPTS="$JVM_OPTS -Dfile.encoding=UTF-8"
JVM_OPTS="$JVM_OPTS -Djava.awt.headless=true"

# Créer le répertoire de logs s'il n'existe pas
mkdir -p logs

# Vérifier si le JAR existe
JAR_FILE="build/libs/legacy-analyzer-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "Le fichier JAR n'existe pas. Construction en cours..."
    ./gradlew bootJar
    if [ $? -ne 0 ]; then
        echo "ERREUR: La construction a échoué"
        exit 1
    fi
fi

# Lancer l'application
echo "Démarrage de Legacy Analyzer..."
echo "Options JVM: $JVM_OPTS"
echo ""

java $JVM_OPTS -jar "$JAR_FILE" "$@"
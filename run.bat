@echo off
setlocal

rem Script de lancement pour Legacy Analyzer
rem Usage: run.bat [options]

rem Aller dans le répertoire du script
cd /d "%~dp0"

rem Vérifier que Java est installé
java -version >nul 2>&1
if errorlevel 1 (
    echo ERREUR: Java n'est pas installe ou n'est pas dans le PATH
    echo Veuillez installer Java 17 ou superieur
    exit /b 1
)

rem Options JVM
set JVM_OPTS=-Xmx4g -Xms1g
set JVM_OPTS=%JVM_OPTS% -XX:+UseG1GC
set JVM_OPTS=%JVM_OPTS% -XX:MaxGCPauseMillis=200
set JVM_OPTS=%JVM_OPTS% -Dfile.encoding=UTF-8
set JVM_OPTS=%JVM_OPTS% -Djava.awt.headless=true

rem Créer le répertoire de logs s'il n'existe pas
if not exist logs mkdir logs

rem Vérifier si le JAR existe
set JAR_FILE=build\libs\legacy-analyzer-1.0.0.jar
if not exist "%JAR_FILE%" (
    echo Le fichier JAR n'existe pas. Construction en cours...
    call gradlew.bat bootJar
    if errorlevel 1 (
        echo ERREUR: La construction a echoue
        exit /b 1
    )
)

rem Lancer l'application
echo Demarrage de Legacy Analyzer...
echo Options JVM: %JVM_OPTS%
echo.

java %JVM_OPTS% -jar "%JAR_FILE%" %*

endlocal
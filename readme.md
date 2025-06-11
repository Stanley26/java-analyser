# Legacy-Analyzer

Legacy-Analyzer est un outil d'analyse statique en ligne de commande conçu pour cartographier des applications Java legacy complexes. Son objectif principal est de fournir une documentation technique et métier détaillée et fiable pour sécuriser et accélérer les projets de réécriture ou de modernisation.

Il scanne le code source de multiples applications, analyse leur configuration, leurs dépendances et leurs points d'entrée, et génère des rapports structurés au format JSON.

### Fonctionnalités Clés

* **Analyse Multi-Projets :** Scanne un répertoire contenant plusieurs projets en une seule passe.
* **Détection de Frameworks :**
    * **Spring MVC :** Détecte les `@RestController` et les annotations de mapping (`@GetMapping`, etc.).
    * **Struts 1.x :** Analyse les `struts-config.xml` pour trouver les actions et les URL.
    * **Java Servlets :** Analyse les `web.xml` pour trouver les servlets et leurs URL patterns.
* **Analyse de Dépendances en Profondeur :**
    * **JDBC :** Trouve les requêtes SQL écrites en dur dans le code Java.
    * **EJB :** Détecte les appels au mainframe ou à d'autres systèmes via les lookups JNDI.
* **Gestion de la Configuration :** Lit les fichiers `.properties` et peut les fusionner avec une configuration d'override (ex: celle de production) pour une analyse plus juste.
* **Corrélation Métier :** Peut utiliser un fichier CSV fourni par l'utilisateur pour lier les endpoints techniques à des fonctions d'affaires, et générer un rapport métier consolidé.

### Prérequis

* JDK 11 ou supérieur.
* Gradle (l'outil utilise un wrapper `gradlew`, donc aucune installation manuelle n'est requise).

### Comment Construire l'Application

Depuis la racine du projet `legacy-analyzer`, exécutez la commande suivante. Cela va télécharger les dépendances, compiler le code et créer un fichier JAR exécutable dans le dossier `build/libs`.

```bash
./gradlew build
```

### Comment Exécuter l'Analyseur

Une fois le projet construit, vous pouvez lancer l'analyse via le JAR généré.

```bash
java -jar build/libs/legacy-analyzer-1.0-SNAPSHOT.jar [OPTIONS]
```

**Options disponibles :**

| Option                       | Raccourci | Description                                                                                               | Obligatoire |
| ---------------------------- | --------- | --------------------------------------------------------------------------------------------------------- | ----------- |
| `--projects-path=<dossier>`  | `-p`      | Chemin vers le dossier racine contenant tous les projets à analyser.                                      | **Oui** |
| `--output-directory=<dossier>` | `-out`    | Dossier où les rapports JSON seront sauvegardés. Par défaut : `./reports`.                                | Non         |
| `--override-path=<dossier>`  | `-o`      | Chemin vers le projet contenant les fichiers `.properties` d'override (ex: la configuration de production). | Non         |
| `--business-map=<fichier>`   | `-b`      | Chemin vers le fichier CSV de mapping des fonctions d'affaires.                                           | Non         |

**Exemple de commande complète :**

```bash
java -jar build/libs/legacy-analyzer-1.0-SNAPSHOT.jar \
    -p="/home/user/workspace/legacy-applications" \
    -o="/home/user/configurations/prod-overrides" \
    -b="/home/user/documents/fonctions-metier.csv" \
    -out="resultats-analyse"
```

### Structure des Rapports

L'outil génère deux types de rapports :

1.  **Rapports Techniques (`rapport-technique-NOM_PROJET.json`) :** Un fichier par projet, détaillant toute la structure interne de l'application (endpoints, dépendances, configuration). C'est le plan pour les équipes de développement.
2.  **Rapport Métier (`rapport-metier.json`) :** Un unique fichier consolidé (si `--business-map` est utilisé) qui regroupe les endpoints par fonction d'affaires. C'est l'outil de planification pour les chefs de produit et les architectes.


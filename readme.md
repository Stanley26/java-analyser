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
java -jar build/libs/analyzer-1.0.0.jar "C:/chemin/vers/mon/portfolio"
```

Sortie
L'outil va générer des fichiers rapport_analyse_[nom_du_projet].json dans le répertoire depuis lequel vous avez lancé la commande.


Structure du Rapport (.json)
Chaque rapport contient une liste d'objets, où chaque objet représente un endpoint analysé avec la structure suivante :

{
  "endpointPath": "/api/users/{id}",
  "httpMethod": "GET",
  "framework": "Spring",
  "entryPointSignature": "com.mycompany.controller.UserController.getUserById(long)",
  "dependencies": [
    {
      "type": "Service",
      "className": "com.mycompany.service.UserService",
      "methodCalled": "findUserById(long)",
      "callDepth": 1
    }
  ],
  "businessRules": []
}


# Legacy Analyzer

Application d'analyse automatique d'applications Java legacy déployées sur WebLogic.

## 🎯 Objectifs

Legacy Analyzer est conçu pour analyser automatiquement un écosystème complet d'applications Java legacy et extraire :

- **Tous les endpoints HTTP** avec leurs URLs complètes
- **Les règles métier** et la logique applicative
- **Les dépendances** : bases de données, EJB, programmes Cobol, web services, JMS, fichiers
- **Le pseudo-code** de chaque endpoint pour faciliter la compréhension
- **Des rapports détaillés** en JSON et Excel

## 🚀 Fonctionnalités

### Frameworks supportés
- ✅ Servlets (2.5, 3.0, 3.1, 4.0)
- ✅ Struts (1.x, 2.x)
- ✅ Spring MVC (2.x, 3.x, 4.x, 5.x)
- ✅ JAX-RS (Jersey, RestEasy, CXF)
- ⚠️ JSF (support basique)

### Types de dépendances détectées
- **Bases de données** : JDBC, Hibernate, JPA, MyBatis
- **EJB** : 2.x et 3.x, lookups JNDI
- **Cobol** : connexions socket, JNI, transferts de fichiers, MQ
- **Web Services** : SOAP (JAX-WS, CXF, Axis) et REST
- **JMS** : Queues, Topics, Message-Driven Beans
- **Fichiers** : accès locaux et partagés

### Formats de sortie
- **JSON** : Données structurées pour traitement ultérieur
- **Excel** : Rapports détaillés avec graphiques et statistiques

## 📋 Prérequis

- Java 17 ou supérieur
- 4 GB de RAM minimum (8 GB recommandé)
- Espace disque suffisant pour l'extraction des archives

## 🛠️ Installation

### 1. Cloner le repository
```bash
git clone https://github.com/votre-org/legacy-analyzer.git
cd legacy-analyzer
```

### 2. Construire l'application
```bash
./gradlew bootJar
```

Sous Windows :
```cmd
gradlew.bat bootJar
```

## 📖 Utilisation

### Commande de base
```bash
./run.sh --source=/path/to/weblogic/deployments --output=/path/to/results
```

Sous Windows :
```cmd
run.bat --source=C:\weblogic\deployments --output=C:\analysis\results
```

### Options disponibles

| Option | Description | Valeur par défaut |
|--------|-------------|-------------------|
| `--source` | Répertoire contenant les applications | Obligatoire |
| `--output` | Répertoire de sortie des résultats | `./analysis-output` |
| `--config` | Fichier de configuration YAML | Configuration par défaut |
| `--app-name` | Analyser une application spécifique | Toutes les applications |
| `--parallel` | Activer l'analyse parallèle | `true` |
| `--deep` | Analyse approfondie | `false` |

### Exemples d'utilisation

#### Analyse complète avec configuration personnalisée
```bash
./run.sh --config=my-config.yml
```

#### Analyse d'une application spécifique
```bash
./run.sh --source=/weblogic/deployments --app-name=MyApp.ear --deep
```

#### Génération de rapports uniquement
```bash
./run.sh report --input=/path/to/previous/analysis --format=excel
```

## ⚙️ Configuration

### Fichier de configuration (analyzer-config.yml)

```yaml
analyzer:
  source:
    root-directory: "/path/to/weblogic/deployments"
    include-patterns:
      - "*.ear"
      - "*.war"
    exclude-patterns:
      - "*-test.ear"
      - "backup/*"
  
  analysis:
    frameworks:
      - name: struts
        enabled: true
      - name: spring
        enabled: true
    
    database:
      extract-queries: true
      parse-sql: true
    
    integrations:
      ejb:
        analyze-remote-calls: true
      cobol:
        detect-socket-calls: true
  
  output:
    directory: "./results"
    formats:
      json:
        pretty-print: true
      excel:
        include-charts: true
  
  performance:
    parallel-analysis: true
    max-threads: 8
    memory-limit: "4G"
```

## 📊 Structure des résultats

```
analysis-output/
├── analysis-index.json          # Index de l'analyse
├── global/                      # Résultats globaux
│   ├── ecosystem-overview.json
│   ├── dependencies-graph.json
│   └── statistics.json
├── applications/                # Résultats par application
│   ├── app1/
│   │   ├── application-info.json
│   │   ├── endpoints.json
│   │   ├── dependencies.json
│   │   ├── database/
│   │   ├── integrations/
│   │   └── pseudocode/
│   └── app2/
└── reports/                     # Rapports Excel
    ├── global-analysis-report.xlsx
    └── per-application/
        ├── app1-detailed-report.xlsx
        └── app2-detailed-report.xlsx
```

## 📈 Rapports Excel

### Rapport global
- **Vue d'ensemble** : Statistiques générales de l'écosystème
- **Liste des applications** : Inventaire complet avec métriques
- **Analyse des endpoints** : Tous les endpoints détectés
- **Matrice des dépendances** : Vue croisée applications/dépendances
- **Technologies utilisées** : Inventaire des frameworks et outils
- **Statistiques détaillées** : Métriques avancées

### Rapport par application
- **Résumé** : Informations générales et statistiques
- **Endpoints** : Liste détaillée avec paramètres et règles métier
- **Dépendances** : Toutes les dépendances externes
- **Pseudo-code** : Échantillons de code simplifié

## 🔍 Détection des URLs

L'analyseur utilise plusieurs stratégies pour détecter les URLs :

1. **URLs statiques** (100% de détection) : Annotations et configurations XML
2. **URLs semi-dynamiques** (80-90%) : Constantes et concaténations simples
3. **URLs dynamiques** (30-50%) : Construction complexe marquée comme "DYNAMIC"

## ⚡ Performance

### Recommandations
- Utiliser l'analyse parallèle pour les gros volumes
- Ajuster `max-threads` selon votre CPU
- Augmenter la mémoire JVM si nécessaire (`-Xmx8g`)

### Temps d'analyse estimés
- Application simple (WAR) : 1-2 minutes
- Application moyenne (EAR) : 5-10 minutes
- Gros EAR multi-modules : 15-30 minutes

## 🐛 Résolution de problèmes

### OutOfMemoryError
Augmenter la mémoire dans `run.sh` :
```bash
JVM_OPTS="-Xmx8g -Xms2g"
```

### Fichiers non trouvés
Vérifier que le répertoire source contient bien des fichiers `.ear`, `.war` ou `.jar`.

### Analyse incomplète
- Activer le mode `--deep` pour une analyse plus approfondie
- Vérifier les logs dans `./logs/legacy-analyzer.log`

## 📝 Logs

Les logs sont disponibles dans le répertoire `./logs/` :
- `legacy-analyzer.log` : Log principal
- `legacy-analyzer-error.log` : Erreurs uniquement
- `analysis-details.log` : Détails techniques de l'analyse

## 🤝 Contribution

Pour contribuer au projet :

1. Fork le repository
2. Créer une branche feature (`git checkout -b feature/AmazingFeature`)
3. Commit les changements (`git commit -m 'Add AmazingFeature'`)
4. Push la branche (`git push origin feature/AmazingFeature`)
5. Ouvrir une Pull Request

## 📄 Licence

Ce projet est sous licence MIT. Voir le fichier `LICENSE` pour plus de détails.

## 👥 Support

Pour toute question ou problème :
- Ouvrir une issue sur GitHub
- Contacter l'équipe de développement

## 🔄 Versions

### v1.0.0 (Version actuelle)
- Support complet Servlets, Struts, Spring MVC, JAX-RS
- Détection des dépendances BD, EJB, Cobol, WS, JMS
- Génération de pseudo-code
- Rapports Excel avec graphiques
- Analyse parallèle

### Roadmap
- v1.1.0 : Support complet JSF
- v1.2.0 : Interface web de visualisation
- v1.3.0 : Export vers base de données
- v2.0.0 : Recommandations de migration automatiques
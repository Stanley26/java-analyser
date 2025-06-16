#!/bin/bash

# Exemples d'utilisation de Legacy Analyzer
# Ce fichier contient des exemples de commandes pour différents cas d'usage

echo "=== Exemples d'utilisation de Legacy Analyzer ==="
echo ""

# 1. Analyse basique
echo "1. Analyse basique de toutes les applications:"
echo "   ./run.sh --source=/weblogic/deployments --output=./results"
echo ""

# 2. Analyse avec configuration personnalisée
echo "2. Analyse avec fichier de configuration:"
echo "   ./run.sh --config=analyzer-config.yml"
echo ""

# 3. Analyse d'une application spécifique
echo "3. Analyse d'une seule application:"
echo "   ./run.sh --source=/weblogic/deployments --app-name=MyApp.ear --output=./results/MyApp"
echo ""

# 4. Analyse approfondie avec plus de détails
echo "4. Analyse approfondie:"
echo "   ./run.sh --source=/weblogic/deployments --deep --output=./deep-analysis"
echo ""

# 5. Analyse séquentielle (pas de parallélisation)
echo "5. Analyse séquentielle (pour debug):"
echo "   ./run.sh --source=/weblogic/deployments --parallel=false --output=./results"
echo ""

# 6. Génération de rapports uniquement
echo "6. Générer des rapports depuis une analyse existante:"
echo "   ./run.sh report --input=./previous-analysis --format=excel"
echo ""

# 7. Validation de configuration
echo "7. Valider un fichier de configuration:"
echo "   ./run.sh validate --config=my-config.yml"
echo ""

# 8. Analyse avec mémoire augmentée
echo "8. Analyse avec plus de mémoire (pour grosses applications):"
echo "   JVM_OPTS='-Xmx8g -Xms2g' ./run.sh --source=/weblogic/deployments"
echo ""

# 9. Analyse avec logs détaillés
echo "9. Analyse avec logs en mode DEBUG:"
echo "   ./run.sh --source=/weblogic/deployments --output=./results --log-level=DEBUG"
echo ""

# 10. Analyse complète d'un environnement de production
echo "10. Exemple complet pour environnement de production:"
cat << 'EOF'
    ./run.sh \
        --config=prod-config.yml \
        --source=/opt/weblogic/domains/prod/deployments \
        --output=/data/analysis/$(date +%Y%m%d) \
        --parallel=true \
        --deep
EOF
echo ""

# Fonction utilitaire pour afficher un exemple
show_example() {
    local example_num=$1
    local description=$2
    local command=$3
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Exemple $example_num: $description"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Commande:"
    echo "  $command"
    echo ""
}

# Cas d'usage avancés
echo ""
echo "=== Cas d'usage avancés ==="
echo ""

show_example "A" "Analyser uniquement les applications Struts" \
"./run.sh --source=/weblogic/deployments --framework=struts --output=./struts-apps"

show_example "B" "Exclure certaines applications" \
"./run.sh --source=/weblogic/deployments --exclude='*test*,*backup*' --output=./results"

show_example "C" "Analyse avec rapport Excel uniquement" \
"./run.sh --source=/weblogic/deployments --output=./results --format=excel"

show_example "D" "Analyse continue (watch mode)" \
"while true; do ./run.sh --source=/weblogic/deployments --output=./results/\$(date +%H%M%S); sleep 3600; done"

# Conseils
echo ""
echo "=== Conseils d'utilisation ==="
echo ""
echo "• Pour les grosses applications (>1GB), augmenter la mémoire JVM"
echo "• Utiliser --deep pour une analyse complète incluant le pseudo-code"
echo "• Les logs détaillés sont dans ./logs/legacy-analyzer.log"
echo "• Les rapports Excel sont dans ./results/reports/"
echo "• Pour debug, utiliser --parallel=false et --log-level=DEBUG"
echo ""

# Structure de sortie typique
echo "=== Structure de sortie typique ==="
echo ""
echo "results/"
echo "├── analysis-index.json"
echo "├── global/"
echo "│   ├── ecosystem-overview.json"
echo "│   └── dependencies-graph.json"
echo "├── applications/"
echo "│   ├── MyApp1/"
echo "│   │   ├── endpoints.json"
echo "│   │   └── dependencies.json"
echo "│   └── MyApp2/"
echo "│       └── ..."
echo "└── reports/"
echo "    ├── global-analysis-report.xlsx"
echo "    └── per-application/"
echo "        ├── MyApp1-report.xlsx"
echo "        └── MyApp2-report.xlsx"
echo ""
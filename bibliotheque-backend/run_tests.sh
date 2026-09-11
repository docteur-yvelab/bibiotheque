#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
# run_tests.sh — Lance tous les tests et génère un rapport HTML
# ═══════════════════════════════════════════════════════════════════
# Usage :
#   ./run_tests.sh                  # Tous les tests
#   ./run_tests.sh unit             # Tests unitaires uniquement
#   ./run_tests.sh integration      # Tests d'intégration uniquement
#   ./run_tests.sh security         # Tests de sécurité uniquement
# ═══════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Couleurs ─────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# ── Configuration ────────────────────────────────────────────────
REPORT_DIR="target/test-reports"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
HTML_REPORT="${REPORT_DIR}/rapport-tests-${TIMESTAMP}.html"
SUREFIRE_DIR="target/surefire-reports"
TEST_TYPE="${1:-all}"

# ── Fonctions utilitaires ────────────────────────────────────────

print_banner() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}  ${BOLD}🧪 RAPPORT DE TESTS — Bibliothèque API${NC}                     ${CYAN}║${NC}"
    echo -e "${CYAN}║${NC}  ${BLUE}$(date '+%d/%m/%Y à %H:%M:%S')${NC}                                            ${CYAN}║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_step() {
    echo -e "${BLUE}▸${NC} ${BOLD}$1${NC}"
}

print_success() {
    echo -e "  ${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "  ${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "  ${YELLOW}⚠️  $1${NC}"
}

# ── Banner ───────────────────────────────────────────────────────
print_banner

# ── Créer le répertoire de rapports ──────────────────────────────
mkdir -p "${REPORT_DIR}"

# ── Lancer les tests ─────────────────────────────────────────────
print_step "Lancement des tests..."

case "${TEST_TYPE}" in
    unit)
        print_warning "Mode : Tests unitaires uniquement"
        TEST_PATTERN="ReservationServiceTest"
        ;;
    integration)
        print_warning "Mode : Tests d'intégration uniquement"
        TEST_PATTERN="ReservationControllerIntegrationTest"
        ;;
    security)
        print_warning "Mode : Tests de sécurité (RS-01 à RS-05)"
        TEST_PATTERN="ReservationControllerIntegrationTest#rs0*+ReservationServiceTest\$RS0*"
        ;;
    all)
        print_warning "Mode : TOUS les tests"
        TEST_PATTERN=""
        ;;
    *)
        echo -e "${RED}Usage: $0 [unit|integration|security|all]${NC}"
        exit 1
        ;;
esac

# Lancer Maven
echo ""
if [ -n "${TEST_PATTERN}" ]; then
    ./mvnw test -Dtest="${TEST_PATTERN}" \
        -Dsurefire.useFile=true \
        -Dsurefire.printSummary=true \
        2>&1 | tee "${REPORT_DIR}/console-output.log"
else
    ./mvnw test \
        -Dsurefire.useFile=true \
        -Dsurefire.printSummary=true \
        2>&1 | tee "${REPORT_DIR}/console-output.log"
fi

MAVEN_EXIT=$?

echo ""

# ── Extraire les résultats ───────────────────────────────────────
print_step "Analyse des résultats..."

# Compter les résultats depuis les fichiers Surefire XML
TOTAL_TESTS=0
TOTAL_FAILURES=0
TOTAL_ERRORS=0
TOTAL_SKIPPED=0
TOTAL_TIME=0
TESTS_PASSED=0

if [ -d "${SUREFIRE_DIR}" ]; then
    for xml_file in "${SUREFIRE_DIR}"/TEST-*.xml; do
        if [ -f "${xml_file}" ]; then
            tests=$(grep -oP 'tests="\K[0-9]+' "${xml_file}" 2>/dev/null || echo "0")
            failures=$(grep -oP 'failures="\K[0-9]+' "${xml_file}" 2>/dev/null || echo "0")
            errors=$(grep -oP 'errors="\K[0-9]+' "${xml_file}" 2>/dev/null || echo "0")
            skipped=$(grep -oP 'skipped="\K[0-9]+' "${xml_file}" 2>/dev/null || echo "0")
            time=$(grep -oP 'time="\K[0-9.]+' "${xml_file}" 2>/dev/null || echo "0")

            TOTAL_TESTS=$((TOTAL_TESTS + tests))
            TOTAL_FAILURES=$((TOTAL_FAILURES + failures))
            TOTAL_ERRORS=$((TOTAL_ERRORS + errors))
            TOTAL_SKIPPED=$((TOTAL_SKIPPED + skipped))
            TOTAL_TIME=$(echo "${TOTAL_TIME} + ${time}" | bc 2>/dev/null || echo "0")
        fi
    done
    TESTS_PASSED=$((TOTAL_TESTS - TOTAL_FAILURES - TOTAL_ERRORS - TOTAL_SKIPPED))
fi

# Arrondir le temps
TOTAL_TIME_ROUNDED=$(printf "%.2f" "${TOTAL_TIME}" 2>/dev/null || echo "0")

print_success "Tests trouvés : ${TOTAL_TESTS}"
print_success "Tests réussis : ${TESTS_PASSED}"
if [ "${TOTAL_FAILURES}" -gt 0 ]; then
    print_error "Échecs : ${TOTAL_FAILURES}"
fi
if [ "${TOTAL_ERRORS}" -gt 0 ]; then
    print_error "Erreurs : ${TOTAL_ERRORS}"
fi
if [ "${TOTAL_SKIPPED}" -gt 0 ]; then
    print_warning "Ignorés : ${TOTAL_SKIPPED}"
fi
echo -e "  ${BLUE}⏱  Durée totale : ${TOTAL_TIME_ROUNDED}s${NC}"

# ── Générer le rapport HTML ──────────────────────────────────────
print_step "Génération du rapport HTML..."

# Déterminer le statut global
if [ "${TOTAL_FAILURES}" -eq 0 ] && [ "${TOTAL_ERRORS}" -eq 0 ]; then
    GLOBAL_STATUS="PASS"
    STATUS_COLOR="#22c55e"
    STATUS_ICON="✅"
else
    GLOBAL_STATUS="FAIL"
    STATUS_COLOR="#ef4444"
    STATUS_ICON="❌"
fi

# Lister les détails des tests par classe
TEST_DETAILS=""
for xml_file in "${SUREFIRE_DIR}"/TEST-*.xml; do
    if [ -f "${xml_file}" ]; then
        classname=$(grep -oP 'name="\K[^"]+' "${xml_file}" 2>/dev/null | head -1)
        tests=$(grep -oP 'tests="\K[0-9]+' "${xml_file}" 2>/dev/null || echo "0")
        failures=$(grep -oP 'failures="\K[0-9]+' "${xml_file}" 2>/dev/null || echo "0")
        errors=$(grep -oP 'errors="\K[0-9]+' "${xml_file}" 2>/dev/null || echo "0")
        skipped=$(grep -oP 'skipped="\K[0-9]+' "${xml_file}" 2>/dev/null || echo "0")
        time=$(grep -oP 'time="\K[0-9.]+' "${xml_file}" 2>/dev/null || echo "0")
        passed=$((tests - failures - errors - skipped))

        # Extraire les noms des tests qui ont échoué
        failed_tests=""
        if [ "${failures}" -gt 0 ] || [ "${errors}" -gt 0 ]; then
            failed_tests=$(grep -oP '<testcase[^>]*name="\K[^"]+' "${xml_file}" 2>/dev/null | head -5)
        fi

        # Couleur de la ligne
        if [ "${failures}" -gt 0 ] || [ "${errors}" -gt 0 ]; then
            ROW_COLOR="#fef2f2"
            BADGE_COLOR="#ef4444"
            BADGE_TEXT="FAIL"
        else
            ROW_COLOR="#f0fdf4"
            BADGE_COLOR="#22c55e"
            BADGE_TEXT="PASS"
        fi

        # Construire les détails des échecs
        FAILED_DETAILS=""
        if [ -n "${failed_tests}" ]; then
            FAILED_DETAILS="<ul style='margin:4px 0 0 0;padding-left:16px;color:#b91c1c;font-size:0.85em;'>"
            for test_name in ${failed_tests}; do
                FAILED_DETAILS+="<li>${test_name}</li>"
            done
            FAILED_DETAILS+="</ul>"
        fi

        # Extraire le nom court de la classe (sans le package)
        SHORT_NAME=$(echo "${classname}" | sed 's/.*\.//')

        TEST_DETAILS+="
        <tr style='background:${ROW_COLOR};'>
            <td style='padding:10px 12px;border-bottom:1px solid #e5e7eb;'>
                <strong>${SHORT_NAME}</strong>
                <br><span style='color:#6b7280;font-size:0.8em;'>${classname}</span>
            </td>
            <td style='padding:10px 12px;border-bottom:1px solid #e5e7eb;text-align:center;'>${tests}</td>
            <td style='padding:10px 12px;border-bottom:1px solid #e5e7eb;text-align:center;color:#22c55e;font-weight:bold;'>${passed}</td>
            <td style='padding:10px 12px;border-bottom:1px solid #e5e7eb;text-align:center;${failures:+color:#ef4444;font-weight:bold;}'>${failures}</td>
            <td style='padding:10px 12px;border-bottom:1px solid #e5e7eb;text-align:center;${errors:+color:#ef4444;font-weight:bold;}'>${errors}</td>
            <td style='padding:10px 12px;border-bottom:1px solid #e5e7eb;text-align:center;'>${skipped}</td>
            <td style='padding:10px 12px;border-bottom:1px solid #e5e7eb;text-align:center;'>${time}s</td>
            <td style='padding:10px 12px;border-bottom:1px solid #e5e7eb;text-align:center;'>
                <span style='background:${BADGE_COLOR};color:white;padding:2px 8px;border-radius:12px;font-size:0.8em;font-weight:bold;'>${BADGE_TEXT}</span>
                ${FAILED_DETAILS}
            </td>
        </tr>"
    fi
done

# Générer le HTML
cat > "${HTML_REPORT}" << HTMLEOF
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Rapport de Tests — Bibliothèque API</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f8fafc; color: #1e293b; }
        .container { max-width: 1100px; margin: 0 auto; padding: 24px; }
        .header { background: linear-gradient(135deg, #1e293b 0%, #334155 100%); color: white; padding: 32px; border-radius: 16px; margin-bottom: 24px; }
        .header h1 { font-size: 1.8em; margin-bottom: 8px; }
        .header .meta { color: #94a3b8; font-size: 0.9em; }
        .status-badge { display: inline-block; padding: 6px 16px; border-radius: 20px; font-weight: bold; font-size: 1.1em; margin-top: 12px; }
        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 16px; margin-bottom: 24px; }
        .stat-card { background: white; border-radius: 12px; padding: 20px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .stat-card .number { font-size: 2em; font-weight: bold; }
        .stat-card .label { color: #64748b; font-size: 0.85em; margin-top: 4px; }
        .card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 24px; }
        .card h2 { font-size: 1.3em; margin-bottom: 16px; color: #1e293b; border-bottom: 2px solid #e2e8f0; padding-bottom: 8px; }
        table { width: 100%; border-collapse: collapse; }
        th { background: #f1f5f9; padding: 10px 12px; text-align: left; font-size: 0.85em; color: #475569; text-transform: uppercase; letter-spacing: 0.05em; border-bottom: 2px solid #e2e8f0; }
        .rules-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 12px; }
        .rule-item { background: #f0fdf4; border-left: 4px solid #22c55e; padding: 12px 16px; border-radius: 0 8px 8px 0; }
        .rule-item .rule-id { font-weight: bold; color: #166534; }
        .rule-item .rule-desc { color: #475569; font-size: 0.9em; margin-top: 4px; }
        .footer { text-align: center; color: #94a3b8; font-size: 0.8em; margin-top: 32px; padding: 16px; }
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <div class="header">
            <h1>🧪 Rapport de Tests</h1>
            <div class="meta">Bibliothèque API — feature/reservation-securite-thiakou-stive</div>
            <div class="meta">Généré le $(date '+%d/%m/%Y à %H:%M:%S')</div>
            <div class="status-badge" style="background:${STATUS_COLOR};color:white;">
                ${STATUS_ICON} ${GLOBAL_STATUS}
            </div>
        </div>

        <!-- Statistiques -->
        <div class="stats-grid">
            <div class="stat-card">
                <div class="number" style="color:#1e293b;">${TOTAL_TESTS}</div>
                <div class="label">Tests totaux</div>
            </div>
            <div class="stat-card">
                <div class="number" style="color:#22c55e;">${TESTS_PASSED}</div>
                <div class="label">Réussis</div>
            </div>
            <div class="stat-card">
                <div class="number" style="color:${TOTAL_FAILURES:+#ef4444}">${TOTAL_FAILURES:-0}</div>
                <div class="label">Échecs</div>
            </div>
            <div class="stat-card">
                <div class="number" style="color:${TOTAL_ERRORS:+#ef4444}">${TOTAL_ERRORS:-0}</div>
                <div class="label">Erreurs</div>
            </div>
            <div class="stat-card">
                <div class="number" style="color:#64748b;">${TOTAL_SKIPPED:-0}</div>
                <div class="label">Ignorés</div>
            </div>
            <div class="stat-card">
                <div class="number" style="color:#3b82f6;">${TOTAL_TIME_ROUNDED}s</div>
                <div class="label">Durée totale</div>
            </div>
        </div>

        <!-- Règles de sécurité -->
        <div class="card">
            <h2>🔒 Règles de sécurité vérifiées</h2>
            <div class="rules-grid">
                <div class="rule-item">
                    <div class="rule-id">RS-01</div>
                    <div class="rule-desc">Sans token JWT → 401 Unauthorized</div>
                </div>
                <div class="rule-item">
                    <div class="rule-id">RS-02</div>
                    <div class="rule-desc">ADHERENT → 403 sur DELETE</div>
                </div>
                <div class="rule-item">
                    <div class="rule-id">RS-03</div>
                    <div class="rule-desc">Accès propriétaire seul → 403</div>
                </div>
                <div class="rule-item">
                    <div class="rule-id">RS-04</div>
                    <div class="rule-desc">Identité du token JWT (pas du body)</div>
                </div>
                <div class="rule-item">
                    <div class="rule-id">RS-05</div>
                    <div class="rule-desc">Filtrage automatique par propriétaire</div>
                </div>
                <div class="rule-item">
                    <div class="rule-id">RG-01</div>
                    <div class="rule-desc">Réservation livre disponible → 409</div>
                </div>
                <div class="rule-item">
                    <div class="rule-id">RG-02</div>
                    <div class="rule-desc">1 seule réservation active par livre/adhérent</div>
                </div>
                <div class="rule-item">
                    <div class="rule-id">RG-03</div>
                    <div class="rule-desc">Max 3 réservations simultanées</div>
                </div>
            </div>
        </div>

        <!-- Détails par classe de test -->
        <div class="card">
            <h2>📊 Détails par classe de test</h2>
            <table>
                <thead>
                    <tr>
                        <th>Classe</th>
                        <th style="text-align:center;">Total</th>
                        <th style="text-align:center;">✅ OK</th>
                        <th style="text-align:center;">❌ Fail</th>
                        <th style="text-align:center;">⚠️ Err</th>
                        <th style="text-align:center;">⏭ Skip</th>
                        <th style="text-align:center;">⏱ Time</th>
                        <th style="text-align:center;">Statut</th>
                    </tr>
                </thead>
                <tbody>
                    ${TEST_DETAILS}
                </tbody>
            </table>
        </div>

        <!-- Footer -->
        <div class="footer">
            Rapport généré automatiquement par run_tests.sh<br>
            Branche : feature/reservation-securite-thiakou-stive
        </div>
    </div>
</body>
</html>
HTMLEOF

print_success "Rapport HTML généré : ${HTML_REPORT}"

# ── Ouvrir le rapport (optionnel) ────────────────────────────────
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  📄 Rapport : ${HTML_REPORT}${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"

# Ouvrir dans le navigateur si disponible
if command -v xdg-open &> /dev/null; then
    xdg-open "${HTML_REPORT}" 2>/dev/null &
elif command -v open &> /dev/null; then
    open "${HTML_REPORT}" 2>/dev/null &
fi

echo ""
if [ "${MAVEN_EXIT}" -eq 0 ]; then
    echo -e "${GREEN}${BOLD}  🎉 TOUS LES TESTS SONT PASSÉS !${NC}"
else
    echo -e "${RED}${BOLD}  ⛔ DES TESTS ONT ÉCHOUÉ — Voir le rapport HTML pour les détails${NC}"
fi
echo ""

exit ${MAVEN_EXIT}

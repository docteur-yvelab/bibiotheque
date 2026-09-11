#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
# setup_data.sh — Initialisation complète de la base de données
# ═══════════════════════════════════════════════════════════════════
# Ce script initialise TOUTES les données nécessaires pour les séances :
# - Livres (L1 à L5 avec stocks variés)
# - Utilisateurs (admin + 3 adhérents)
# - Rôles (BIBLIOTHECAIRE + ADHERENT)
# - Emprunts (A3 a emprunté L2-L5)
# - Réservations (pour tester les règles RG et RS)
#
# Usage :
#   ./setup_data.sh              # Initialise la base
#   ./setup_data.sh --reset      # Réinitialise tout (supprime les anciennes données)
# ═══════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Couleurs ─────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# ── Configuration ────────────────────────────────────────────────
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-bibliotheque}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-123456}"

RESET_MODE=false
if [ "${1:-}" = "--reset" ]; then
    RESET_MODE=true
fi

# ── Fonctions ────────────────────────────────────────────────────

print_banner() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}  ${BOLD}📚 Initialisation de la Base de Données Bibliothèque${NC}        ${CYAN}║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_step() {
    echo -e "${BLUE}▸${NC} ${BOLD}$1${NC}"
}

print_success() {
    echo -e "  ${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "  ${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "  ${RED}❌ $1${NC}"
}

# ── Banner ───────────────────────────────────────────────────────
print_banner

# ── Vérifier la connexion à la base ──────────────────────────────
print_step "Vérification de la connexion à PostgreSQL..."

if ! PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -c "SELECT 1;" > /dev/null 2>&1; then
    print_error "Impossible de se connecter à PostgreSQL"
    echo -e "  ${YELLOW}Vérifiez que PostgreSQL est lancé et que les identifiants sont corrects${NC}"
    echo -e "  ${YELLOW}DB_HOST=${DB_HOST} DB_PORT=${DB_PORT} DB_NAME=${DB_NAME} DB_USER=${DB_USER}${NC}"
    exit 1
fi
print_success "Connecté à PostgreSQL (${DB_HOST}:${DB_PORT}/${DB_NAME})"

# ── Mode reset ───────────────────────────────────────────────────
if [ "${RESET_MODE}" = true ]; then
    print_warning "Mode RESET : suppression des anciennes données..."
    PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" <<SQL
        DELETE FROM user_role;
        DELETE FROM borrow;
        DELETE FROM reservation;
        DELETE FROM users WHERE user_id >= 1;
        DELETE FROM books WHERE book_id >= 1;
        DELETE FROM role;
        ALTER SEQUENCE users_user_id_seq RESTART WITH 1;
        ALTER SEQUENCE books_book_id_seq RESTART WITH 1;
        ALTER SEQUENCE role_role_id_seq RESTART WITH 1;
SQL
    print_success "Données supprimées"
fi

# ── 1. Rôles ─────────────────────────────────────────────────────
print_step "Création des rôles..."

PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" <<SQL
    INSERT INTO role (role_id, role_name) VALUES
    (1, 'BIBLIOTHECAIRE'),
    (2, 'ADHERENT')
    ON CONFLICT (role_id) DO UPDATE SET role_name = EXCLUDED.role_name;
SQL
print_success "Rôles : BIBLIOTHECAIRE (id=1), ADHERENT (id=2)"

# ── 2. Utilisateurs ──────────────────────────────────────────────
print_step "Création des utilisateurs..."

# Le hash BCrypt de "123456"
HASH='$2a$10$Q18M5eiqPXGAb4dkuujYyeaAgHu46TlVlL0zL/W8w5lwm6kQYyLK2'

PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" <<SQL
    -- Admin (BIBLIOTHECAIRE)
    INSERT INTO users (user_id, username, password, name) VALUES
    (1, 'admin', '${HASH}', 'Bibliothécaire Principal')
    ON CONFLICT (user_id) DO UPDATE SET
        username = EXCLUDED.username,
        password = EXCLUDED.password,
        name = EXCLUDED.name;

    -- Adhérents
    INSERT INTO users (user_id, username, password, name) VALUES
    (2, 'A1', '${HASH}', 'Adherent 1'),
    (3, 'A2', '${HASH}', 'Adherent 2'),
    (4, 'A3', '${HASH}', 'Adherent 3')
    ON CONFLICT (user_id) DO UPDATE SET
        username = EXCLUDED.username,
        password = EXCLUDED.password,
        name = EXCLUDED.name;
SQL

# Attribution des rôles
PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" <<SQL
    INSERT INTO user_role (user_id, role_id) VALUES
    (1, 1),  -- admin → BIBLIOTHECAIRE
    (2, 2),  -- A1 → ADHERENT
    (3, 2),  -- A2 → ADHERENT
    (4, 2)   -- A3 → ADHERENT
    ON CONFLICT DO NOTHING;
SQL

print_success "Utilisateurs créés :"
echo -e "    ${BOLD}admin${NC} (BIBLIOTHECAIRE) — userId=1"
echo -e "    ${BOLD}A1${NC}    (ADHERENT)      — userId=2"
echo -e "    ${BOLD}A2${NC}    (ADHERENT)      — userId=3"
echo -e "    ${BOLD}A3${NC}    (ADHERENT)      — userId=4"

# ── 3. Livres ────────────────────────────────────────────────────
print_step "Création des livres..."

PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" <<SQL
    INSERT INTO books (book_id, book_name, book_author, book_genre, no_of_copies) VALUES
    (1, 'Le Petit Prince',     'Antoine de Saint-Exupéry', 'Roman',    1),
    (2, 'L\'Étranger',         'Albert Camus',             'Roman',    0),
    (3, 'Les Misérables',      'Victor Hugo',              'Roman',    0),
    (4, 'Madame Bovary',       'Gustave Flaubert',         'Roman',    0),
    (5, 'Germinal',            'Émile Zola',               'Roman',    0),
    (6, 'Python pour débutants','Jean Dupont',             'Informatique', 3),
    (7, 'Spring Boot Guide',   'John Smith',               'Informatique', 2),
    (8, 'SQL en 24h',          'Marie Martin',             'Informatique', 0)
    ON CONFLICT (book_id) DO UPDATE SET
        book_name = EXCLUDED.book_name,
        book_author = EXCLUDED.book_author,
        book_genre = EXCLUDED.book_genre,
        no_of_copies = EXCLUDED.no_of_copies;
SQL

print_success "Livres créés :"
echo -e "    ${BOLD}L1${NC} Le Petit Prince      — 1 copie (disponible)"
echo -e "    ${BOLD}L2${NC} L'Étranger            — 0 copies (emprunté)"
echo -e "    ${BOLD}L3${NC} Les Misérables        — 0 copies (emprunté)"
echo -e "    ${BOLD}L4${NC} Madame Bovary          — 0 copies (emprunté)"
echo -e "    ${BOLD}L5${NC} Germinal               — 0 copies (emprunté)"
echo -e "    ${BOLD}L6${NC} Python pour débutants  — 3 copies (disponible)"
echo -e "    ${BOLD}L7${NC} Spring Boot Guide      — 2 copies (disponible)"
echo -e "    ${BOLD}L8${NC} SQL en 24h             — 0 copies (emprunté)"

# ── 4. Emprunts ──────────────────────────────────────────────────
print_step "Création des emprunts (A3 a emprunté L2-L5 et L8)..."

PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" <<SQL
    INSERT INTO borrow (user_id, book_id, issue_date, due_date) VALUES
    (4, 2, NOW(), NOW() + INTERVAL '7 days'),
    (4, 3, NOW(), NOW() + INTERVAL '7 days'),
    (4, 4, NOW(), NOW() + INTERVAL '7 days'),
    (4, 5, NOW(), NOW() + INTERVAL '7 days'),
    (4, 8, NOW(), NOW() + INTERVAL '7 days')
    ON CONFLICT DO NOTHING;
SQL
print_success "Emprunts créés pour A3 (L2, L3, L4, L5, L8)"

# ── 5. Réservations de test ──────────────────────────────────────
print_step "Création des réservations de test..."

PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" <<SQL
    -- Réservation de A1 pour L2 (EN_ATTENTE)
    INSERT INTO reservation (livre_id, adherent_id, date_reservation, date_expiration, statut) VALUES
    (2, 2, NOW(), NOW() + INTERVAL '7 days', 'EN_ATTENTE')
    ON CONFLICT DO NOTHING;

    -- Réservation de A2 pour L3 (EN_ATTENTE)
    INSERT INTO reservation (livre_id, adherent_id, date_reservation, date_expiration, statut) VALUES
    (3, 3, NOW(), NOW() + INTERVAL '7 days', 'EN_ATTENTE')
    ON CONFLICT DO NOTHING;

    -- Réservation de A1 pour L4 (DISPONIBLE)
    INSERT INTO reservation (livre_id, adherent_id, date_reservation, date_expiration, statut) VALUES
    (4, 2, NOW() - INTERVAL '3 days', NOW() + INTERVAL '4 days', 'DISPONIBLE')
    ON CONFLICT DO NOTHING;

    -- Réservation de A2 pour L5 (ANNULEE — pour tester RG-06)
    INSERT INTO reservation (livre_id, adherent_id, date_reservation, date_expiration, statut) VALUES
    (5, 3, NOW() - INTERVAL '2 days', NOW() + INTERVAL '5 days', 'ANNULEE')
    ON CONFLICT DO NOTHING;
SQL
print_success "Réservations de test créées"

# ── Résumé ───────────────────────────────────────────────────────
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  📊 RÉSUMÉ DE L'INITIALISATION${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "  ${BOLD}Utilisateurs :${NC}"
echo -e "    🔑 ${BOLD}admin${NC}     — BIBLIOTHECAIRE — user: ${GREEN}admin${NC}     pass: ${GREEN}123456${NC}"
echo -e "    👤 ${BOLD}A1${NC}         — ADHERENT      — user: ${GREEN}A1${NC}         pass: ${GREEN}123456${NC}"
echo -e "    👤 ${BOLD}A2${NC}         — ADHERENT      — user: ${GREEN}A2${NC}         pass: ${GREEN}123456${NC}"
echo -e "    👤 ${BOLD}A3${NC}         — ADHERENT      — user: ${GREEN}A3${NC}         pass: ${GREEN}123456${NC}"
echo ""
echo -e "  ${BOLD}Livres :${NC}"
echo -e "    📕 L1 Le Petit Prince       — ${GREEN}1 copie${NC}  (réservable si emprunté)"
echo -e "    📗 L2 L'Étranger            — ${RED}0 copies${NC} (réservable maintenant)"
echo -e "    📘 L3 Les Misérables        — ${RED}0 copies${NC} (réservable maintenant)"
echo -e "    📙 L4 Madame Bovary          — ${RED}0 copies${NC} (réservable maintenant)"
echo -e "    📕 L5 Germinal               — ${RED}0 copies${NC} (réservable maintenant)"
echo -e "    📗 L6 Python pour débutants  — ${GREEN}3 copies${NC} (disponible)"
echo -e "    📘 L7 Spring Boot Guide      — ${GREEN}2 copies${NC} (disponible)"
echo -e "    📙 L8 SQL en 24h             — ${RED}0 copies${NC} (emprunté par A3)"
echo ""
echo -e "  ${BOLD}Réservations :${NC}"
echo -e "    📋 A1 → L2 (EN_ATTENTE)"
echo -e "    📋 A2 → L3 (EN_ATTENTE)"
echo -e "    📋 A1 → L4 (DISPONIBLE)"
echo -e "    📋 A2 → L5 (ANNULEE)"
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  🚀 POUR DÉMARRER LE SERVEUR :${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "  ${GREEN}./mvnw spring-boot:run${NC}"
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  🔐 POUR S'AUTHENTIFIER :${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "  ${GREEN}curl -X POST http://localhost:8080/authenticate \\${NC}"
echo -e "  ${GREEN}  -H \"Content-Type: application/json\" \\${NC}"
echo -e "  ${GREEN}  -d '{\"username\":\"admin\",\"password\":\"123456\"}'${NC}"
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${GREEN}${BOLD}  ✅ INITIALISATION TERMINÉE AVEC SUCCÈS !${NC}"
echo ""

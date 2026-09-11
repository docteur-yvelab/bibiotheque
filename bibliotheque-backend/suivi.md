# 📋 SUIVI ET VÉRIFICATION — Sécurité API Réservations

**Branche** : `feature/reservation-securite-thiakou-stive`
**Date** : 11 septembre 2026
**Résultat** : ✅ 54/54 tests passent — BUILD SUCCESS
**Dernière MàJ** : Correction des bugs critiques (rôles, BorrowController, filtre adherentId)

---

## TABLE DES MATIÈRES

1. [Comment lancer les tests](#1-comment-lancer-les-tests)
2. [Vérification de la sécurité](#2-vérification-de-la-sécurité)
3. [Vérification des tests](#3-vérification-des-tests)
4. [Bugs corrigés](#4-bugs-corrigés)
5. [Guide de test manuel (curl)](#5-guide-de-test-manuel)
6. [Fichiers créés/modifiés](#6-fichiers-créésmodifiés)

---

## 1. COMMENT LANCER LES TESTS

### 1.1 — Prérequis

```bash
java -version          # Java 21+
./mvnw --version       # Maven (via le wrapper)
# PostgreSQL en cours d'exécution (pour les tests d'intégration)
```

### 1.2 — Lancer TOUS les tests

```bash
./mvnw test
```

**Résultat attendu :**
```
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 1.3 — Lancer uniquement les tests unitaires

```bash
./mvnw test -Dtest="ReservationServiceTest"
```

**Résultat :** 28 tests, 0 échec, AUCUNE base de données nécessaire.

### 1.4 — Lancer uniquement les tests d'intégration

```bash
./mvnw test -Dtest="ReservationControllerIntegrationTest"
```

**Résultat :** 25 tests, 0 échec, nécessite PostgreSQL.

### 1.5 — Lancer avec le script automatisé

```bash
./run_tests.sh              # Tous les tests
./run_tests.sh unit         # Unitaires uniquement
./run_tests.sh integration  # Intégration uniquement
./run_tests.sh security     # Sécurité RS-01 à RS-05
```

---

## 2. VÉRIFICATION DE LA SÉCURITÉ

### 2.1 — Système de rôles

| Exigence | Statut | Vérification |
|----------|--------|--------------|
| Rôle ADHERENT existe | ✅ | `role` table : role_id=2, role_name='ADHERENT' |
| Rôle BIBLIOTHECAIRE existe | ✅ | `role` table : role_id=1, role_name='BIBLIOTHECAIRE' |
| Attribution correcte | ✅ | `init_data.sql` : A1/A2/A3 → ADHERENT, admin → BIBLIOTHECAIRE |
| Rôles utilisés dans tous les controllers | ✅ | `hasRole('BIBLIOTHECAIRE')` partout (corrigé) |

### 2.2 — Matrice d'autorisation

| Endpoint | Anonyme | ADHERENT | BIBLIOTHECAIRE | Statut |
|----------|---------|----------|----------------|--------|
| `POST /api/reservations` | ❌ 401 | ✅ Pour lui-même | ✅ Pour n'importe qui | ✅ |
| `GET /api/reservations` | ❌ 401 | ✅ Ses réservations | ✅ Toutes (+ filtre adherentId) | ✅ |
| `GET /api/reservations/{id}` | ❌ 401 | ✅ Si sienne | ✅ Toutes | ✅ |
| `PATCH /api/reservations/{id}/annuler` | ❌ 401 | ✅ Si sienne | ✅ Toutes | ✅ |
| `DELETE /api/reservations/{id}` | ❌ 401 | ❌ 403 | ✅ Toutes | ✅ |
| `POST /admin/books` | ❌ 401 | ❌ 403 | ✅ | ✅ |
| `POST /borrow` | ❌ 401 | ❌ 403 | ✅ | ✅ |

### 2.3 — Règles de sécurité (RS-01 à RS-05)

#### RS-01 : Sans token JWT → 401

| Vérification | Statut | Détail |
|-------------|--------|--------|
| Tous les endpoints protégés | ✅ | `authenticated()` dans SecurityFilterChain |
| Absence de token → 401 | ✅ | `JwtAuthenticationEntryPoint` |
| Token invalide → 401 | ✅ | `JwtRequestFilter` |
| Token expiré → 401 | ✅ | `ExpiredJwtException` |
| Aucun 403 pour non authentifié | ✅ | Sans token → toujours 401 |

**Tests :** `rs01_get_sansToken`, `rs01_post_sansToken`, `rs01_getById_sansToken`, `rs01_patch_sansToken`, `rs01_delete_sansToken`, `rs01_bearerVide`, `rs01_headerSansBearer`

#### RS-02 : ADHERENT → 403 sur DELETE

| Vérification | Statut | Détail |
|-------------|--------|--------|
| DELETE par ADHERENT → 403 | ✅ | `hasRole("BIBLIOTHECAIRE")` |
| DELETE par BIBLIOTHECAIRE → 204 | ✅ | Autorisé |
| ADHERENT sur /admin → 403 | ✅ | `@PreAuthorize` corrigé |
| ADHERENT sur /borrow → 403 | ✅ | `@PreAuthorize` ajouté |

**Tests :** `rs02_adherentSupprime403`, `rs02_bibliothecaireSupprimeOk`, `rs02_deleteInexistante404`

#### RS-03 : Accès propriétaire seul

| Vérification | Statut | Détail |
|-------------|--------|--------|
| GET réservation d'un autre → 403 | ✅ | Vérification `adherentId.equals(userId)` |
| PATCH réservation d'un autre → 403 | ✅ | Même vérification |
| GET sa propre → 200 | ✅ | |
| PATCH sa propre → 200 | ✅ | |
| BIBLIOTHECAIRE → 200 | ✅ | `isBiblio` bypass |

**Tests :** 5 unitaires + 4 intégration

#### RS-04 : Identité du token, pas du body

| Vérification | Statut | Détail |
|-------------|--------|--------|
| adherentId du body ignoré pour ADHERENT | ✅ | `adherentId = userId` |
| ID extrait du SecurityContext | ✅ | `currentUserService.getAuthenticatedUserId()` |
| BIBLIOTHECAIRE peut utiliser adherentId du body | ✅ | |
| BIBLIOTHECAIRE sans adherentId → 400 | ✅ | |

**Tests :** 2 unitaires + 3 intégration

#### RS-05 : Filtrage automatique

| Vérification | Statut | Détail |
|-------------|--------|--------|
| ADHERENT ne voit que ses réservations | ✅ | `findByAdherentId(userId)` |
| BIBLIOTHECAIRE voit toutes les réservations | ✅ | `findAll()` |
| BIBLIOTHECAIRE peut filtrer par adherentId | ✅ | `findByAdherentId(filterAdherentId)` |
| Filtrage par statut fonctionne | ✅ | |

**Tests :** 3 unitaires + 3 intégration

### 2.4 — Distinction 401 vs 403

| Scénario | Code attendu | Statut |
|----------|-------------|--------|
| Sans token | 401 | ✅ |
| Token invalide | 401 | ✅ |
| Token expiré | 401 | ✅ |
| ADHERENT → DELETE | 403 | ✅ |
| ADHERENT → réservation d'un autre | 403 | ✅ |
| ADHERENT → /admin | 403 | ✅ |
| ADHERENT → /borrow | 403 | ✅ |
| ADHERENT → ses propres opérations | 200/201 | ✅ |
| BIBLIOTHECAIRE → tout | 200/201/204 | ✅ |

### 2.5 — Configuration Spring Security

| Composant | Fichier | Statut |
|-----------|---------|--------|
| SecurityFilterChain | `WebSecurityConfiguration.java` | ✅ |
| JwtRequestFilter | `JwtRequestFilter.java` | ✅ |
| JwtAuthenticationEntryPoint | `JwtAuthenticationEntryPoint.java` | ✅ |
| Endpoints publics | `/authenticate`, Swagger | ✅ |
| @PreAuthorize | BooksController, AdminController, BorrowController | ✅ |
| Exception handler (403) | `GlobalExceptionHandler.java` | ✅ |

---

## 3. VÉRIFICATION DES TESTS

### 3.1 — Tests unitaires (ReservationServiceTest)

| Exigence | Statut | Détail |
|----------|--------|--------|
| JUnit 5 (@ExtendWith(MockitoExtension.class)) | ✅ | |
| Mockito (@Mock, @InjectMocks) | ✅ | |
| Repository mocké (pas de vraie DB) | ✅ | |
| Test passe sans base de données | ✅ | |
| @MockitoSettings(strictness = Strictness.LENIENT) | ✅ | |

**Cas RG-03 :**

| Cas | Méthode | Statut |
|-----|---------|--------|
| 2 réservations actives → peut en créer une 3ème | `rg03_deuxReservationsActives_doitReussir` | ✅ |
| 3 réservations actives → refus | `rg03_troisReservationsActives_doitEchouer` | ✅ |

### 3.2 — Tests d'intégration (ReservationControllerIntegrationTest)

| Exigence | Statut | Détail |
|----------|--------|--------|
| @SpringBootTest | ✅ | |
| @AutoConfigureMockMvc | ✅ | |
| MockMvc | ✅ | |
| S'exécute avec `mvn test` | ✅ | |
| @TestInstance(PER_CLASS) | ✅ | |
| Nettoyage (@BeforeEach deleteAll) | ✅ | |

### 3.3 — Exigences générales

| Exigence | Statut | Détail |
|----------|--------|--------|
| Tests s'exécutent avec `mvn test` | ✅ | |
| Tous les tests passent en vert | ✅ | 54/54 |
| Noms de méthodes descriptifs | ✅ | |
| Tests unitaires dans `src/test/.../service/` | ✅ | |
| Tests d'intégration dans `src/test/.../controller/` | ✅ | |
| @DisplayName pour lecture claire | ✅ | |

---

## 4. BUGS CORRIGÉS

| # | Bug | Gravité | Fichier | Avant | Après |
|---|-----|---------|---------|-------|-------|
| 1 | Rôle inexistant | 🔴 | BooksController | `hasRole('Admin')` | `hasRole('BIBLIOTHECAIRE')` |
| 2 | Rôle inexistant | 🔴 | AdminController | `hasRole('Admin')` | `hasRole('BIBLIOTHECAIRE')` |
| 3 | Self-assignment rôle null | 🔴 | AdminController | `role.setRoleName(role.getRoleName())` | `role.setRoleName("ADHERENT")` |
| 4 | Aucune sécurité | 🔴 | BorrowController | Pas d'annotation | `@PreAuthorize("hasRole('BIBLIOTHECAIRE')")` |
| 5 | GET sans filtre adherentId | 🟡 | ReservationController | Uniquement filtre `statut` | + paramètre `adherentId` |
| 6 | CORS dupliqué et hardcodé | 🟡 | Tous controllers | `@CrossOrigin("localhost:4200")` | Supprimé (CorsConfiguration globale) |

---

## 5. GUIDE DE TEST MANUEL (curl)

### 5.1 — Authentification

```bash
# Admin (BIBLIOTHECAIRE)
curl -X POST http://localhost:8080/authenticate \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# Adhérent A1
curl -X POST http://localhost:8080/authenticate \
  -H "Content-Type: application/json" \
  -d '{"username":"A1","password":"123456"}'
```

### 5.2 — RS-01 : Sans token → 401

```bash
curl -v http://localhost:8080/api/reservations
# → HTTP 401
```

### 5.3 — RS-02 : ADHERENT → DELETE 403

```bash
curl -v -X DELETE http://localhost:8080/api/reservations/1 \
  -H "Authorization: Bearer TOKEN_ADHERENT"
# → HTTP 403
```

### 5.4 — RS-03 : Réservation d'un autre → 403

```bash
curl -v http://localhost:8080/api/reservations/ID_RESERVATION_A2 \
  -H "Authorization: Bearer TOKEN_ADHERENT_A1"
# → HTTP 403
```

### 5.5 — RS-04 : Body ignoré pour ADHERENT

```bash
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer TOKEN_ADHERENT_A1" \
  -H "Content-Type: application/json" \
  -d '{"livreId":5,"adherentId":3}'
# → Créé pour adherentId=2 (token), PAS 3 (body)
```

### 5.6 — RS-05 : BIBLIOTHECAIRE filtre par adherentId

```bash
curl "http://localhost:8080/api/reservations?adherentId=3" \
  -H "Authorization: Bearer TOKEN_ADMIN"
# → Retourne uniquement les réservations de A2 (userId=3)
```

### 5.7 — RG-01 : Livre disponible → 409

```bash
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer TOKEN_ADHERENT" \
  -H "Content-Type: application/json" \
  -d '{"livreId":1}'
# → 409 "RG-01 : Le livre 'L1' est disponible"
```

---

## 6. FICHIERS CRÉÉS/MODIFIÉS

### Fichiers créés

| Fichier | Description |
|---------|-------------|
| `docker-compose.yml` | PostgreSQL + Backend + JWT_SECRET |
| `.env` | JWT_SECRET et DB_PASSWORD |
| `securite.md` | Rapport de sécurité complet |
| `questions.md` | 50+ Q/R sur l'implémentation |
| `suivi.md` | Ce fichier |
| `run_tests.sh` | Script de test avec rapport HTML |
| `CurrentUserService.java` | Utilitaire SecurityContext |
| `ReservationControllerIntegrationTest.java` | 25 tests d'intégration |

### Fichiers modifiés

| Fichier | Description |
|---------|-------------|
| `WebSecurityConfiguration.java` | Ordre des matchers corrigé |
| `ReservationController.java` | Extraction token + filtre adherentId + CORS nettoyé |
| `ReservationService.java` | Paramètres userId/isBiblio + RS-03/RS-04/RS-05 + filtre adherentId |
| `BooksController.java` | `hasRole('BIBLIOTHECAIRE')` + CORS nettoyé |
| `AdminController.java` | `hasRole('BIBLIOTHECAIRE')` + fix self-assignment + CORS nettoyé |
| `BorrowController.java` | `@PreAuthorize` ajouté + CORS nettoyé |
| `JwtUtil.java` | JWT_SECRET en variable d'environnement |
| `JwtService.java` | Rôles ADHERENT/BIBLIOTHECAIRE |
| `ReservationResponse.java` | Constructeur no-arg + @JsonFormat |
| `GlobalExceptionHandler.java` | Handler AccessDeniedException (403) |
| `BibliothequeApplication.java` | Vérification mot de passe admin |
| `init_data.sql` | Rôles BIBLIOTHECAIRE/ADHERENT |
| `ReservationServiceTest.java` | 28 tests unitaires réécrits |

---

## 7. RÉSULTAT FINAL

```
╔═══════════════════════════════════════════════════════════════╗
║                    RÉSULTAT DE VÉRIFICATION                   ║
╠═══════════════════════════════════════════════════════════════╣
║  Tests unitaires (Service)        : 28/28  ✅                 ║
║  Tests d'intégration (Controller) : 25/25  ✅                 ║
║  Tests de contexte                :  1/1   ✅                 ║
║  ─────────────────────────────────────────────────────────    ║
║  TOTAL                            : 54/54  ✅  BUILD SUCCESS  ║
╠═══════════════════════════════════════════════════════════════╣
║  RS-01 : Sans token → 401         : ✅ Implémenté             ║
║  RS-02 : ADHERENT → 403 DELETE    : ✅ Implémenté             ║
║  RS-03 : Accès propriétaire seul  : ✅ Implémenté             ║
║  RS-04 : Identité du token        : ✅ Implémenté             ║
║  RS-05 : Filtrage automatique     : ✅ Implémenté             ║
╠═══════════════════════════════════════════════════════════════╣
║  Rôle ADHERENT                    : ✅ Créé                    ║
║  Rôle BIBLIOTHECAIRE              : ✅ Créé                    ║
║  Rôle dans BooksController        : ✅ Corrigé                 ║
║  Rôle dans AdminController        : ✅ Corrigé                 ║
║  BorrowController sécurisé        : ✅ Corrigé                 ║
║  GET filtre adherentId            : ✅ Corrigé                 ║
║  Clé JWT sécurisée                : ✅ Variable d'env         ║
║  docker-compose.yml               : ✅ Créé                    ║
║  Documentation                    : ✅ securite+questions+suivi║
╚═══════════════════════════════════════════════════════════════╝
```

**Toutes les exigences sont ✅ Implémenté et les 6 bugs critiques sont corrigés.**

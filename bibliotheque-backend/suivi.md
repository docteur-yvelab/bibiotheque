# 📋 SUIVI ET VÉRIFICATION — Sécurité API Réservations

**Branche** : `feature/reservation-securite-thiakou-stive`
**Date** : 11 septembre 2026
**Résultat** : ✅ 54/54 tests passent — BUILD SUCCESS

---

## TABLE DES MATIÈRES

1. [Comment lancer les tests](#1-comment-lancer-les-tests)
2. [Vérification de la sécurité (PARTIE 1)](#2-vérification-de-la-sécurité)
3. [Vérification des tests (PARTIE 2)](#3-vérification-des-tests)
4. [Rapport d'analyse complet](#4-rapport-danalyse-complet)
5. [Guide de test manuel (postman/curl)](#5-guide-de-test-manuel)
6. [Fichiers créés/modifiés](#6-fichiers-créésmodifiés)

---

## 1. COMMENT LANCER LES TESTS

### 1.1 — Prérequis

```bash
# Java 21+
java -version

# Maven (via le wrapper, pas besoin d'installer Maven)
./mvnw --version

# PostgreSQL en cours d'exécution (pour les tests d'intégration)
# Optionnel : H2 ou une base de test dédiée
```

### 1.2 — Lancer TOUS les tests

```bash
./mvnw test
```

**Résultat attendu :**
```
Tests run: 28, Failures: 0, Errors: 0 (unitaires)
Tests run: 25, Failures: 0, Errors: 0 (intégration)
Tests run:  1, Failures: 0, Errors: 0 (contexte)
────────────────────────────────────────────────────
TOTAL: 54 tests — BUILD SUCCESS
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

### 1.5 — Lancer un test spécifique

```bash
# Exemple : test RG-03 (max 3 réservations)
./mvnw test -Dtest="ReservationServiceTest\$RG03_Max3Reservations"

# Exemple : test RS-01 (sans token → 401)
./mvnw test -Dtest="ReservationControllerIntegrationTest#rs01_get_sansToken"
```

### 1.6 — Lancer avec un rapport détaillé

```bash
./mvnw test -Dtest="ReservationServiceTest" 2>&1 | tee rapport_tests.txt
```

---

## 2. VÉRIFICATION DE LA SÉCURITÉ

### 2.1 — Système de rôles

| Exigence | Statut | Vérification |
|----------|--------|--------------|
| Rôle ADHERENT existe | ✅ | `role` table : role_id=2, role_name='ADHERENT' |
| Rôle BIBLIOTHECAIRE existe | ✅ | `role` table : role_id=1, role_name='BIBLIOTHECAIRE' |
| Attribution correcte | ✅ | `init_data.sql` : A1/A2/A3 → ADHERENT, admin → BIBLIOTHECAIRE |

**Fichiers concernés :**
- `init_data.sql` (lignes 12-17)
- `entity/Role.java`
- `entity/Users.java` (relation ManyToMany)
- `service/JwtService.java` (getAuthority)

### 2.2 — Matrice d'autorisation des endpoints

| Endpoint | Anonyme | ADHERENT | BIBLIOTHECAIRE | Statut |
|----------|---------|----------|----------------|--------|
| `POST /api/reservations` | ❌ 401 | ✅ Pour lui-même | ✅ Pour n'importe qui | ✅ |
| `GET /api/reservations` | ❌ 401 | ✅ Ses réservations | ✅ Toutes | ✅ |
| `GET /api/reservations/{id}` | ❌ 401 | ✅ Si sienne | ✅ Toutes | ✅ |
| `PATCH /api/reservations/{id}/annuler` | ❌ 401 | ✅ Si sienne | ✅ Toutes | ✅ |
| `DELETE /api/reservations/{id}` | ❌ 401 | ❌ 403 | ✅ Toutes | ✅ |

**Fichiers concernés :**
- `configuration/WebSecurityConfiguration.java` (SecurityFilterChain)
- `controller/ReservationController.java` (logique métier)
- `service/ReservationService.java` (contrôle d'accès)
- `service/CurrentUserService.java` (extraction du token)

### 2.3 — Règles de sécurité (RS-01 à RS-05)

#### RS-01 : Sans token JWT → 401

| Vérification | Statut | Détail |
|-------------|--------|--------|
| Tous les endpoints /api/reservations/** sont protégés | ✅ | `authenticated()` dans SecurityFilterChain |
| Absence de token retourne 401 | ✅ | `JwtAuthenticationEntryPoint` → 401 |
| Token invalide retourne 401 | ✅ | `JwtRequestFilter`捕获 l'erreur → 401 |
| Token expiré retourne 401 | ✅ | `ExpiredJwtException`捕获 → 401 |
| Aucun 403 pour un utilisateur non authentifié | ✅ | Sans token → toujours 401, jamais 403 |

**Tests couvrant RS-01 :**
- Unitaires : aucun (testé en intégration)
- Intégration : `rs01_get_sansToken`, `rs01_post_sansToken`, `rs01_getById_sansToken`, `rs01_patch_sansToken`, `rs01_delete_sansToken`, `rs01_bearerVide`, `rs01_headerSansBearer`

#### RS-02 : ADHERENT → 403 sur DELETE

| Vérification | Statut | Détail |
|-------------|--------|--------|
| DELETE par ADHERENT retourne 403 | ✅ | `hasRole("BIBLIOTHECAIRE")` sur DELETE |
| DELETE par BIBLIOTHECAIRE retourne 204 | ✅ | Autorisé par SecurityFilterChain |
| Aucun autre endpoint ne retourne 403 à tort | ✅ | GET/PATCH/PASSED ne sont pas filtrés par rôle |

**Tests couvrant RS-02 :**
- Intégration : `rs02_adherentSupprime403`, `rs02_bibliothecaireSupprimeOk`, `rs02_deleteInexistante404`

#### RS-03 : Accès propriétaire seul

| Vérification | Statut | Détail |
|-------------|--------|--------|
| GET réservation d'un autre → 403 | ✅ | `reservation.getAdherentId().equals(userId)` |
| PATCH réservation d'un autre → 403 | ✅ | Même vérification |
| GET sa propre réservation → 200 | ✅ | Vérification passe |
| PATCH sa propre réservation → 200 | ✅ | Vérification passe |
| BIBLIOTHECAIRE accède à tout → 200 | ✅ | `isBiblio` bypass la vérification |

**Tests couvrant RS-03 :**
- Unitaires : `RS03_AccesProprietaireSeul` (5 tests)
- Intégration : `rs03_consulterReservationAutrui403`, `rs03_consulterSaPropreReservation200`, `rs03_annulerReservationAutrui403`, `rs03_annulerSaPropreReservation200`

#### RS-04 : Identité du token, pas du body

| Vérification | Statut | Détail |
|-------------|--------|--------|
| adherentId du body ignoré pour ADHERENT | ✅ | `adherentId = userId` dans le service |
| ID extrait du SecurityContext | ✅ | `currentUserService.getAuthenticatedUserId()` |
| BIBLIOTHECAIRE peut utiliser adherentId du body | ✅ | `if (isBiblio) adherentId = request.getAdherentId()` |
| BIBLIOTHECAIRE sans adherentId → 400 | ✅ | `IllegalArgumentException` → 400 |

**Tests couvrant RS-04 :**
- Unitaires : `RS04_IdentiteDuToken` (2 tests)
- Intégration : `rs04_adherentIgnoreBody`, `rs04_biblioCreePourAutrui`, `rs04_biblioSansAdherentId400`

#### RS-05 : Filtrage automatique

| Vérification | Statut | Détail |
|-------------|--------|--------|
| ADHERENT ne voit que ses réservations | ✅ | `findByAdherentId(userId)` |
| BIBLIOTHECAIRE voit toutes les réservations | ✅ | `findAll()` |
| Filtrage par statut fonctionne | ✅ | `findByAdherentIdAndStatut(userId, statut)` |

**Tests couvrant RS-05 :**
- Unitaires : `RS05_FiltrageAuto` (3 tests)
- Intégration : `rs05_adherentNeVoitQueLesSiennes`, `rs05_biblioVoitTout`, `rs05_filtreStatutAdherent`

### 2.4 — Distinction 401 vs 403

| Scénario | Code attendu | Statut |
|----------|-------------|--------|
| Sans token | 401 | ✅ |
| Token invalide | 401 | ✅ |
| Token expiré | 401 | ✅ |
| ADHERENT → DELETE | 403 | ✅ |
| ADHERENT → réservation d'un autre | 403 | ✅ |
| ADHERENT → ses propres opérations | 200/201 | ✅ |
| BIBLIOTHECAIRE → tout | 200/201/204 | ✅ |

### 2.5 — Configuration Spring Security

| Composant | Fichier | Statut |
|-----------|---------|--------|
| SecurityFilterChain | `WebSecurityConfiguration.java` | ✅ |
| JwtRequestFilter | `JwtRequestFilter.java` | ✅ |
| JwtAuthenticationEntryPoint | `JwtAuthenticationEntryPoint.java` | ✅ |
| Endpoints publics | `/authenticate`, Swagger | ✅ |
| Endpoints privés | `/api/reservations/**`, `/admin/**`, `/borrow/**` | ✅ |
| Exception handler (403) | `GlobalExceptionHandler.java` | ✅ |

---

## 3. VÉRIFICATION DES TESTS

### 3.1 — Tests unitaires (ReservationServiceTest)

| Exigence | Statut | Détail |
|----------|--------|--------|
| JUnit 5 (@ExtendWith(MockitoExtension.class)) | ✅ | |
| Mockito (@Mock, @InjectMocks) | ✅ | |
| Repository mocké (pas de vraie DB) | ✅ | |
| Test passe sans base de données | ✅ | `./mvnw test -Dtest="ReservationServiceTest"` |
| @MockitoSettings(strictness = Strictness.LENIENT) | ✅ | |

**Cas de test RG-03 :**

| Cas | Méthode | Statut |
|-----|---------|--------|
| 2 réservations actives → peut en créer une 3ème | `rg03_deuxReservationsActives_doitReussir` | ✅ |
| 3 réservations actives → ne peut pas en créer une 4ème | `rg03_troisReservationsActives_doitEchouer` | ✅ |

### 3.2 — Tests d'intégration (ReservationControllerIntegrationTest)

| Exigence | Statut | Détail |
|----------|--------|--------|
| @SpringBootTest | ✅ | |
| @AutoConfigureMockMvc | ✅ | |
| TestRestTemplate ou MockMvc | ✅ | MockMvc |
| S'exécute avec `mvn test` | ✅ | |
| @TestInstance(PER_CLASS) | ✅ | Pour le partage d'état |

**Cas de test RS-01 :**

| Cas | Méthode | Statut |
|-----|---------|--------|
| GET sans token → 401 | `rs01_get_sansToken` | ✅ |
| POST sans token → 401 | `rs01_post_sansToken` | ✅ |
| GET/{id} sans token → 401 | `rs01_getById_sansToken` | ✅ |
| PATCH sans token → 401 | `rs01_patch_sansToken` | ✅ |
| DELETE sans token → 401 | `rs01_delete_sansToken` | ✅ |
| Bearer vide → 401 | `rs01_bearerVide` | ✅ |
| Header sans Bearer → 401 | `rs01_headerSansBearer` | ✅ |

**Cas de test RS-03 :**

| Cas | Méthode | Statut |
|-----|---------|--------|
| GET réservation d'un autre → 403 | `rs03_consulterReservationAutrui403` | ✅ |
| GET sa propre réservation → 200 | `rs03_consulterSaPropreReservation200` | ✅ |
| ANNULER réservation d'un autre → 403 | `rs03_annulerReservationAutrui403` | ✅ |
| ANNULER sa propre réservation → 200 | `rs03_annulerSaPropreReservation200` | ✅ |

### 3.3 — Exigences générales

| Exigence | Statut | Détail |
|----------|--------|--------|
| Tests s'exécutent avec `mvn test` | ✅ | |
| Tous les tests passent en vert | ✅ | 54/54 |
| Noms de méthodes descriptifs | ✅ | `rg03_troisReservationsActives_doitEchouer` |
| Tests unitaires dans `src/test/.../service/` | ✅ | |
| Tests d'intégration dans `src/test/.../controller/` | ✅ | |
| @DisplayName pour lecture claire | ✅ | |
| Nettoyage des données (@BeforeEach) | ✅ | `reservationRepository.deleteAll()` |

---

## 4. RAPPORT D'ANALYSE COMPLET

| Exigence | Statut | Observation | Fichier |
|----------|--------|-------------|---------|
| Rôle ADHERENT | ✅ | Créé dans init_data.sql | `init_data.sql` |
| Rôle BIBLIOTHECAIRE | ✅ | Créé dans init_data.sql | `init_data.sql` |
| RS-01 : Sans token → 401 | ✅ | SecurityFilterChain + EntryPoint | `WebSecurityConfiguration.java` |
| RS-02 : ADHERENT → 403 DELETE | ✅ | hasRole("BIBLIOTHECAIRE") | `WebSecurityConfiguration.java` |
| RS-03 : Accès propriétaire | ✅ | Vérification userId dans le service | `ReservationService.java` |
| RS-04 : Identité du token | ✅ | SecurityContext + CurrentUserService | `ReservationController.java` |
| RS-05 : Filtrage automatique | ✅ | findByAdherentId(userId) | `ReservationService.java` |
| 401 vs 403 | ✅ | EntryPoint + AccessDeniedHandler | `GlobalExceptionHandler.java` |
| Clé JWT sécurisée | ✅ | Variable d'environnement JWT_SECRET | `JwtUtil.java` |
| Tests unitaires RG-03 | ✅ | 2 tests avec Mockito | `ReservationServiceTest.java` |
| Tests d'intégration | ✅ | 25 tests avec MockMvc | `ReservationControllerIntegrationTest.java` |
| docker-compose.yml | ✅ | PostgreSQL + backend + JWT_SECRET | `docker-compose.yml` |
| Documentation | ✅ | securite.md + questions.md + suivi.md | Racine du projet |

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

**Réponse :**
```json
{
  "user": {"userId": 1, "username": "admin", "name": "Bibliothécaire Principal"},
  "jwtToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

### 5.2 — Test RS-01 : Sans token → 401

```bash
curl -v http://localhost:8080/api/reservations
# → HTTP 401 Unauthorized
```

### 5.3 — Test RS-01 : Token invalide → 401

```bash
curl -v http://localhost:8080/api/reservations \
  -H "Authorization: Bearer tokenInvalide123"
# → HTTP 401 Unauthorized
```

### 5.4 — Test RS-02 : ADHERENT → DELETE 403

```bash
# Remplacer TOKEN_ADHERENT par le token de A1
curl -v -X DELETE http://localhost:8080/api/reservations/1 \
  -H "Authorization: Bearer TOKEN_ADHERENT"
# → HTTP 403 Forbidden
```

### 5.5 — Test RS-02 : BIBLIOTHECAIRE → DELETE 204

```bash
# Remplacer TOKEN_ADMIN par le token de admin
curl -v -X DELETE http://localhost:8080/api/reservations/1 \
  -H "Authorization: Bearer TOKEN_ADMIN"
# → HTTP 204 No Content
```

### 5.6 — Test RS-03 : ADHERENT accède à la réservation d'un autre → 403

```bash
# Créer une réservation pour A2 (via admin)
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"livreId":2,"adherentId":3}'

# A1 tente de consulter cette réservation
curl -v http://localhost:8080/api/reservations/1 \
  -H "Authorization: Bearer TOKEN_ADHERENT_A1"
# → HTTP 403 Forbidden
```

### 5.7 — Test RS-04 : ADHERENT envoie un autre adherentId → ignoré

```bash
# A1 (userId=2) envoie adherentId=3 dans le body
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer TOKEN_ADHERENT_A1" \
  -H "Content-Type: application/json" \
  -d '{"livreId":5,"adherentId":3}'

# → La réservation est créée pour adherentId=2 (A1 du token), PAS 3
```

### 5.8 — Test RS-05 : ADHERENT ne voit que ses réservations

```bash
curl http://localhost:8080/api/reservations \
  -H "Authorization: Bearer TOKEN_ADHERENT_A1"

# → Retourne uniquement les réservations où adherentId=2
```

### 5.9 — Test RS-05 : BIBLIOTHECAIRE voit tout

```bash
curl http://localhost:8080/api/reservations \
  -H "Authorization: Bearer TOKEN_ADMIN"

# → Retourne TOUTES les réservations
```

### 5.10 — Test RG-01 : Réservation d'un livre disponible → 409

```bash
# L1 a 1 copie → réservation impossible
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer TOKEN_ADHERENT" \
  -H "Content-Type: application/json" \
  -d '{"livreId":1}'

# → HTTP 409 Conflict
# → {"message":"RG-01 : Le livre 'L1' est disponible, réservation impossible."}
```

### 5.11 — Test RG-03 : Max 3 réservations → 409

```bash
# Après avoir créé 3 réservations actives
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer TOKEN_ADHERENT" \
  -H "Content-Type: application/json" \
  -d '{"livreId":5}'

# → HTTP 409 Conflict
# → {"message":"RG-03 : L'adhérent ne peut pas dépasser 3 réservations actives simultanées."}
```

---

## 6. FICHIERS CRÉÉS/MODIFIÉS

### Fichiers créés

| Fichier | Taille | Description |
|---------|--------|-------------|
| `docker-compose.yml` | 1.2 KB | PostgreSQL + Backend Spring Boot |
| `.env` | 200 B | JWT_SECRET et DB_PASSWORD |
| `securite.md` | 8 KB | Rapport de sécurité complet |
| `questions.md` | 12 KB | 50+ Q/R sur l'implémentation |
| `suivi.md` | Ce fichier | Guide de test et vérification |
| `CurrentUserService.java` | 1.5 KB | Utilitaire SecurityContext |
| `ReservationControllerIntegrationTest.java` | 10 KB | 25 tests d'intégration |

### Fichiers modifiés

| Fichier | Lignes changées | Description |
|---------|----------------|-------------|
| `WebSecurityConfiguration.java` | +21/-10 | Ordre des matchers corrigé |
| `ReservationController.java` | +93/-40 | Extraction du token + contrôle d'accès |
| `ReservationService.java` | +118/-50 | Paramètres userId/isBiblio + RS-03/RS-04/RS-05 |
| `JwtUtil.java` | +11/-3 | JWT_SECRET en variable d'environnement |
| `JwtService.java` | +13/-5 | Rôles ADHERENT/BIBLIOTHECAIRE |
| `ReservationResponse.java` | +6/-1 | Constructeur no-arg + @JsonFormat |
| `GlobalExceptionHandler.java` | +14/-2 | Handler AccessDeniedException (403) |
| `BibliothequeApplication.java` | +9/-3 | Vérification mot de passe admin |
| `init_data.sql` | +32/-15 | Rôles BIBLIOTHECAIRE/ADHERENT |
| `ReservationServiceTest.java` | +728/-323 | 28 tests unitaires réécrits |

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
║  Clé JWT sécurisée                : ✅ Variable d'env         ║
║  docker-compose.yml               : ✅ Créé                    ║
║  Documentation                    : ✅ securite.md + questions ║
╚═══════════════════════════════════════════════════════════════╝
```

**Toutes les exigences sont ✅ Implémenté et correct.**

# 🔒 RAPPORT DE SÉCURITÉ — API Réservations Bibliothèque

**Branche** : `feature/reservation-securite-thiakou-stive`
**Date** : 11 septembre 2026
**Technologies** : Spring Boot 3.2.5 · Spring Security 6.2 · JWT (JJWT 0.12.5) · Java 21
**Tests** : ✅ 54/54 — BUILD SUCCESS

---

## 1. ARCHITECTURE DE SÉCURITÉ

### 1.1 Fichiers de configuration

| Fichier | Rôle |
|---------|------|
| `WebSecurityConfiguration.java` | Chaîne de filtres Spring Security (SecurityFilterChain) |
| `JwtRequestFilter.java` | Filtre OncePerRequestFilter — extraction et validation du token JWT |
| `JwtAuthenticationEntryPoint.java` | Point d'entrée — retourne 401 si non authentifié |
| `JwtUtil.java` | Génération, validation et parsing des tokens JWT |
| `JwtService.java` | Service d'authentification + UserDetailsService |
| `CurrentUserService.java` | Utilitaire pour extraire l'utilisateur courant depuis SecurityContext |
| `CorsConfiguration.java` | Configuration CORS globale (unique source) |

### 1.2 Chaîne de filtres

```
Requête HTTP
    │
    ▼
CorsFilter (CorsConfiguration.java)
    │
    ▼
JwtRequestFilter
    │  ├─ Extrait le header "Authorization: Bearer <token>"
    │  ├─ Parse et valide le token (JwtUtil)
    │  ├─ Charge l'utilisateur (JwtService → UserDetailsService)
    │  └─ Place l'Authentication dans SecurityContextHolder
    │
    ▼
AuthorizationFilter
    │  ├─ Vérifie les rôles (hasRole dans SecurityFilterChain + @PreAuthorize)
    │  └─ Appelle JwtAuthenticationEntryPoint si échec
    │
    ▼
Contrôleur
```

### 1.3 Gestion des rôles

| Rôle | Description | Autorisations |
|------|-------------|---------------|
| `ADHERENT` | Membre de la bibliothèque | Réservations (ses propres données uniquement) |
| `BIBLIOTHECAIRE` | Personnel de la bibliothèque | CRUD complet + suppression + gestion users/livres/emprunts |

**Mapping base de données → Spring Security :**
```
Role.roleName = "ADHERENT"       → Authority = "ROLE_ADHERENT"
Role.roleName = "BIBLIOTHECAIRE" → Authority = "ROLE_BIBLIOTHECAIRE"
```

---

## 2. MATRICE D'AUTORISATION DES ENDPOINTS

### 2.1 Endpoints Réservation (`/api/reservations`)

| Méthode | Endpoint | Anonyme | ADHERENT | BIBLIOTHECAIRE |
|---------|----------|---------|----------|----------------|
| `POST` | `/api/reservations` | ❌ 401 | ✅ Pour lui-même | ✅ Pour n'importe qui |
| `GET` | `/api/reservations` | ❌ 401 | ✅ Ses réservations | ✅ Toutes (filtrable par adherentId) |
| `GET` | `/api/reservations/{id}` | ❌ 401 | ✅ Si elle lui appartient | ✅ Toutes |
| `PATCH` | `/api/reservations/{id}/annuler` | ❌ 401 | ✅ Si elle lui appartient | ✅ Toutes |
| `DELETE` | `/api/reservations/{id}` | ❌ 401 | ❌ 403 Forbidden | ✅ Toutes |

### 2.2 Endpoints Livres (`/admin/books`)

| Méthode | Endpoint | Anonyme | ADHERENT | BIBLIOTHECAIRE |
|---------|----------|---------|----------|----------------|
| `GET` | `/admin/books` | ❌ 401 | ❌ 403 | ✅ |
| `GET` | `/admin/books/{id}` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |
| `POST` | `/admin/books` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |
| `PUT` | `/admin/books/{id}` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |
| `DELETE` | `/admin/books/{id}` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |

### 2.3 Endpoints Utilisateurs (`/admin/users`)

| Méthode | Endpoint | Anonyme | ADHERENT | BIBLIOTHECAIRE |
|---------|----------|---------|----------|----------------|
| `POST` | `/admin/users` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |
| `GET` | `/admin/users` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |
| `GET` | `/admin/users/{id}` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |
| `PUT` | `/admin/users/{id}` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |

### 2.4 Endpoints Emprunts (`/borrow`)

| Méthode | Endpoint | Anonyme | ADHERENT | BIBLIOTHECAIRE |
|---------|----------|---------|----------|----------------|
| `POST` | `/borrow` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |
| `GET` | `/borrow` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |
| `PUT` | `/borrow` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |
| `GET` | `/borrow/user/{id}` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |
| `GET` | `/borrow/book/{id}` | ❌ 401 | ❌ 403 | ✅ (`@PreAuthorize`) |

### 2.5 Endpoints Publics

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `POST` | `/authenticate` | Authentification → retourne le token JWT |
| `GET` | `/swagger-ui/**` | Documentation Swagger |
| `GET` | `/v3/api-docs/**` | OpenAPI JSON |

---

## 3. RÈGLES DE SÉCURITÉ IMPLÉMENTÉES

### RS-01 : Sans token JWT → 401 Unauthorized

**Implémentation :**
- `WebSecurityConfiguration.java` : tous les endpoints privés sont `authenticated()`
- `JwtAuthenticationEntryPoint.java` : retourne `401 Unauthorized` si non authentifié
- `JwtRequestFilter.java` : si le token est absent/invalide/expiré, l'utilisateur reste anonyme

**Code :**
```java
// WebSecurityConfiguration.java
.requestMatchers("/api/reservations/**").authenticated()
.exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
```

### RS-02 : ADHERENT → 403 Forbidden sur actions réservées au BIBLIOTHECAIRE

**Implémentation :**
- `WebSecurityConfiguration.java` : `DELETE /api/reservations/**` réservé à `hasRole("BIBLIOTHECAIRE")`
- `BooksController.java` : tous les endpoints CRUD livres avec `@PreAuthorize("hasRole('BIBLIOTHECAIRE')")`
- `AdminController.java` : tous les endpoints CRUD users avec `@PreAuthorize("hasRole('BIBLIOTHECAIRE')")`
- `BorrowController.java` : tous les endpoints emprunts avec `@PreAuthorize("hasRole('BIBLIOTHECAIRE')")`
- `GlobalExceptionHandler.java` : handler pour `AccessDeniedException` → 403

**Code :**
```java
// WebSecurityConfiguration.java
.requestMatchers(HttpMethod.DELETE, "/api/reservations/**").hasRole("BIBLIOTHECAIRE")
.requestMatchers("/admin/**").hasRole("BIBLIOTHECAIRE")

// BooksController.java, AdminController.java, BorrowController.java
@PreAuthorize("hasRole('BIBLIOTHECAIRE')")
```

### RS-03 : ADHERENT ne peut pas accéder aux réservations d'un autre

**Implémentation :**
- `ReservationService.java` : vérifie `reservation.getAdherentId().equals(userId)` avant tout accès
- `CurrentUserService.java` : extrait `userId` depuis `SecurityContextHolder`

**Code :**
```java
// ReservationService.java
if (!isBiblio && !reservation.getAdherentId().equals(userId)) {
    throw new AccessDeniedException("RS-03 : Accès refusé — cette réservation ne vous appartient pas.");
}
```

### RS-04 : Identité extraite du token JWT, PAS du corps de la requête

**Implémentation :**
- `ReservationController.java` : récupère `userId = currentUserService.getAuthenticatedUserId()`
- `ReservationService.java` : pour un ADHERENT, `adherentId = userId` (le corps est ignoré)

**Code :**
```java
// ReservationController.java
Integer userId = currentUserService.getAuthenticatedUserId();
boolean isBiblio = currentUserService.isBibliothecaire();
ReservationResponse response = reservationService.creerReservation(request, userId, isBiblio);

// ReservationService.java
if (!isBiblio) {
    adherentId = userId; // RS-04 : on ignore request.getAdherentId()
}
```

### RS-05 : GET /api/reservations filtré automatiquement pour l'ADHERENT

**Implémentation :**
- `ReservationService.listerReservations()` : pour un ADHERENT, appelle `findByAdherentId(userId)`
- `ReservationController.java` : accepte un paramètre `adherentId` optionnel pour le BIBLIOTHECAIRE

**Code :**
```java
// ReservationController.java
@GetMapping
public ResponseEntity<List<ReservationResponse>> listerReservations(
        @RequestParam(required = false) ReservationStatus statut,
        @RequestParam(required = false) Integer adherentId) {

// ReservationService.java
if (isBiblio) {
    // BIBLIOTHECAIRE : voit tout, filtrable par statut et/ou adherentId
    if (statut != null && filterAdherentId != null) {
        reservations = repository.findByAdherentIdAndStatut(filterAdherentId, statut);
    } else if (statut != null) {
        reservations = repository.findByStatut(statut);
    } else if (filterAdherentId != null) {
        reservations = repository.findByAdherentId(filterAdherentId);
    } else {
        reservations = repository.findAll();
    }
} else {
    // RS-05 : ADHERENT ne voit que ses réservations
    reservations = statut != null
        ? repository.findByAdherentIdAndStatut(userId, statut)
        : repository.findByAdherentId(userId);
}
```

---

## 4. TOKEN JWT

### 4.1 Génération

```
POST /authenticate
Body: { "username": "A1", "password": "123456" }
Response: {
    "user": { "userId": 2, "username": "A1", "name": "Adherent 1" },
    "jwtToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

### 4.2 Structure du token

| Claim | Valeur |
|-------|--------|
| `sub` | Username de l'utilisateur |
| `iat` | Date de création |
| `exp` | Création + 5 heures (18000 secondes) |
| `Signature` | HMAC-SHA512 avec la clé secrète |

### 4.3 Clé secrète

- **Avant** : hardcodée dans `JwtUtil.java`
- **Maintenant** : variable d'environnement `JWT_SECRET` avec fallback développement
- **Configuration** : `docker-compose.yml` ou fichier `.env`

### 4.4 Validation

```java
// JwtRequestFilter.java
if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
    jwtToken = requestTokenHeader.substring(7);
    username = jwtUtil.getUsernameFromToken(jwtToken);
    if (jwtUtil.validateToken(jwtToken, userDetails)) {
        // Token valide → authentification enregistrée
    }
}
```

---

## 5. DISTINCTION 401 vs 403

| Code | Signification | Cas d'usage |
|------|---------------|-------------|
| **401 Unauthorized** | Token absent, invalide ou expiré | Pas de header `Authorization` / Token expiré / Mauvaise signature |
| **403 Forbidden** | Utilisateur authentifié mais sans droits | ADHERENT qui tente DELETE / ADHERENT accédant à une réservation d'un autre / ADHERENT sur /admin ou /borrow |

**Implémentation :**
- 401 → `JwtAuthenticationEntryPoint` (géré par Spring Security)
- 403 → `GlobalExceptionHandler.handleAccessDeniedException()` (géré par notre exception handler)

---

## 6. DONNÉES DE TEST (init_data.sql)

| Entité | Valeurs |
|--------|---------|
| Admin | userId=1, username=`admin`, password=`123456`, rôle=BIBLIOTHECAIRE |
| Adhérent A1 | userId=2, username=`A1`, password=`123456`, rôle=ADHERENT |
| Adhérent A2 | userId=3, username=`A2`, password=`123456`, rôle=ADHERENT |
| Adhérent A3 | userId=4, username=`A3`, password=`123456`, rôle=ADHERENT |
| Livres | L1 (1 copie), L2-L5 (0 copies, empruntés par A3) |

---

## 7. RÉSULTATS DES TESTS

```
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Catégorie | Tests | Statut |
|-----------|-------|--------|
| RG-01 : Livre indisponible | 2 | ✅ |
| RG-02 : Une seule réservation/livre | 2 | ✅ |
| RG-03 : Max 3 réservations | 2 | ✅ |
| RG-04 : Date expiration +7j | 1 | ✅ |
| RG-05/RG-06 : Annulation | 5 | ✅ |
| RS-01 : Sans token → 401 | 7 (intégration) | ✅ |
| RS-02 : DELETE → 403 | 3 (intégration) | ✅ |
| RS-03 : Accès propriétaire seul | 5 + 4 | ✅ |
| RS-04 : Identité du token | 2 + 3 | ✅ |
| RS-05 : Filtrage automatique | 3 + 3 | ✅ |
| Cas limites | 6 + 5 | ✅ |

---

## 8. BUGS CORRIGÉS

| # | Bug | Gravité | Fichier | Correction |
|---|-----|---------|---------|------------|
| 1 | `hasRole('Admin')` au lieu de `hasRole('BIBLIOTHECAIRE')` | 🔴 | BooksController, AdminController | Remplacement du nom de rôle |
| 2 | `role.setRoleName(role.getRoleName())` → null | 🔴 | AdminController | `role.setRoleName("ADHERENT")` |
| 3 | BorrowController sans aucune sécurité | 🔴 | BorrowController | `@PreAuthorize("hasRole('BIBLIOTHECAIRE')")` sur tous les endpoints write |
| 4 | GET /api/reservations sans filtre adherentId | 🟡 | ReservationController, ReservationService | Ajout du paramètre `adherentId` |
| 5 | `@CrossOrigin("localhost:4200")` dupliqué et hardcodé | 🟡 | Tous les controllers | Suppression (CorsConfiguration globale) |

---

## 9. VULNÉRABILITÉS RÉSIDUELLES

| Vulnérabilité | Risque | Statut |
|---------------|--------|--------|
| `System.out.println` dans JwtRequestFilter | 🟡 Moyen | ⚠️ À remplacer par SLF4J |
| Pas de refresh token | 🟡 Moyen | ⚠️ Prévu comme amélioration |
| Pas de rate limiting sur /authenticate | 🟡 Moyen | ⚠️ Prévu comme amélioration |
| Reservation utilise des IDs au lieu de @ManyToOne | 🟢 Basse | ⚠️ Écart architectural |

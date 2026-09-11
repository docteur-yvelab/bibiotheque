# 🔒 RAPPORT DE SÉCURITÉ — API Réservations Bibliothèque

**Branche** : `feature/reservation-securite-thiakou-stive`
**Date** : 11 septembre 2026
**Technologies** : Spring Boot 3.2.5 · Spring Security 6.2 · JWT (JJWT 0.12.5) · Java 21

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
| `CorsConfiguration.java` | Configuration CORS globale |

### 1.2 Chaîne de filtres

```
Requête HTTP
    │
    ▼
CorsFilter
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
    │  ├─ Vérifie les rôles (@PreAuthorize, hasRole)
    │  └─ Appelle JwtAuthenticationEntryPoint si échec
    │
    ▼
Contrôleur
```

### 1.3 Gestion des rôles

| Rôle | Description | Autorisations |
|------|-------------|---------------|
| `ADHERENT` | Membre de la bibliothèque | CRUD réservations (ses propres données uniquement) |
| `BIBLIOTHECAIRE` | Personnel de la bibliothèque | CRUD complet + suppression + gestion users/livres |

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
| `GET` | `/api/reservations` | ❌ 401 | ✅ Ses réservations | ✅ Toutes |
| `GET` | `/api/reservations/{id}` | ❌ 401 | ✅ Si elle lui appartient | ✅ Toutes |
| `PATCH` | `/api/reservations/{id}/annuler` | ❌ 401 | ✅ Si elle lui appartient | ✅ Toutes |
| `DELETE` | `/api/reservations/{id}` | ❌ 401 | ❌ 403 Forbidden | ✅ Toutes |

### 2.2 Endpoints Livres (`/admin`)

| Méthode | Endpoint | Anonyme | ADHERENT | BIBLIOTHECAIRE |
|---------|----------|---------|----------|----------------|
| `GET` | `/admin/books` | ❌ 403 | ❌ 403 | ✅ |
| `GET` | `/admin/books/{id}` | ❌ 403 | ❌ 403 | ✅ |
| `POST` | `/admin/books` | ❌ 403 | ❌ 403 | ✅ |
| `PUT` | `/admin/books/{id}` | ❌ 403 | ❌ 403 | ✅ |
| `DELETE` | `/admin/books/{id}` | ❌ 403 | ❌ 403 | ✅ |

### 2.3 Endpoints Emprunts (`/borrow`)

| Méthode | Endpoint | Anonyme | ADHERENT | BIBLIOTHECAIRE |
|---------|----------|---------|----------|----------------|
| `POST` | `/borrow` | ❌ 401 | ✅ | ✅ |
| `GET` | `/borrow` | ❌ 401 | ✅ | ✅ |
| `PUT` | `/borrow` | ❌ 401 | ✅ | ✅ |

### 2.4 Endpoints Publics

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `POST` | `/authenticate` | Authentification → retourne le token JWT |
| `GET` | `/swagger-ui/**` | Documentation Swagger |
| `GET` | `/v3/api-docs/**` | OpenAPI JSON |

---

## 3. RÈGLES DE SÉCURITÉ IMPLÉMENTÉES

### RS-01 : Sans token JWT → 401 Unauthorized

**Implémentation :**
- `WebSecurityConfiguration.java` : tous les endpoints réservation sont `authenticated()`
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
- `WebSecurityConfiguration.java` : `DELETE /api/reservations/**` est réservé à `hasRole("BIBLIOTHECAIRE")`
- `GlobalExceptionHandler.java` : handler pour `AccessDeniedException` → 403

**Code :**
```java
// WebSecurityConfiguration.java
.requestMatchers(HttpMethod.DELETE, "/api/reservations/**").hasRole("BIBLIOTHECAIRE")
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

**Code :**
```java
if (isBiblio) {
    reservations = statut != null ? repository.findByStatut(statut) : repository.findAll();
} else {
    // RS-05 : l'ADHERENT ne voit que ses réservations
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
- **Recommandation** : configurer `JWT_SECRET` dans `.env` ou `docker-compose.yml`

### 4.4 Validation

```java
// JwtRequestFilter.java
if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
    jwtToken = requestTokenHeader.substring(7);
    username = jwtUtil.getUsernameFromToken(jwtToken);
    // ...
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
| **403 Forbidden** | Utilisateur authentifié mais sans droits | ADHERENT qui tente DELETE / ADHERENT accédant à une réservation d'un autre |

**Implémentation :**
- 401 → `JwtAuthenticationEntryPoint` (géré par Spring Security)
- 403 → `GlobalExceptionHandler.handleAccessDeniedException()` (géré par notre exception handler)

---

## 6. FLUX D'AUTHENTIFICATION COMPLET

```
1. Client → POST /authenticate { "username": "A1", "password": "123456" }
2. JwtController → JwtService.createJwtToken()
3. JwtService → authenticate() via AuthenticationManager
4. JwtService → loadUserByUsername() → UserDetailsService
5. JwtService → JwtUtil.generateToken(userDetails) → token JWT
6. Client reçoit { "user": {...}, "jwtToken": "eyJ..." }

--- Requêtes suivantes ---

7. Client → GET /api/reservations
   Header: Authorization: Bearer eyJ...
8. JwtRequestFilter → parse le token
9. JwtUtil → validateToken() → username + expiry check
10. SecurityContextHolder → Authentication enregistrée
11. AuthorizationFilter → vérifie les rôles
12. ReservationController → CurrentUserService.getAuthenticatedUserId()
13. ReservationService → filtre selon isBiblio
```

---

## 7. DONNÉES DE TEST (init_data.sql)

| Entité | Valeurs |
|--------|---------|
| Livres | L1 (1 copie), L2-L5 (0 copies) |
| Adhérents | A1 (userId=2), A2 (userId=3), A3 (userId=4) |
| Rôle | User → mapping vers `ADHERENT` ou `BIBLIOTHECAIRE` |
| Emprunts | A3 a emprunté L2, L3, L4, L5 |

---

## 8. RÉSULTATS DES TESTS

```
Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Catégorie | Tests | Statut |
|-----------|-------|--------|
| RG-01 : Livre indisponible | 2 | ✅ |
| RG-02 : Une seule réservation/livre | 2 | ✅ |
| RG-03 : Max 3 réservations | 2 | ✅ |
| RG-04 : Date expiration +7j | 1 | ✅ |
| RG-05/RG-06 : Annulation | 5 | ✅ |
| RS-03 : Accès propriétaire seul | 5 | ✅ |
| RS-04 : Identité du token | 2 | ✅ |
| RS-05 : Filtrage automatique | 3 | ✅ |
| Cas limites | 6 | ✅ |

---

## 9. VULNÉRABILITÉS IDENTIFIÉES ET CORRIGÉES

| Vulnérabilité | Risque avant | Statut après |
|---------------|-------------|--------------|
| Clé JWT hardcodée | 🔴 Élevé | ✅ Variable d'environnement |
| Endpoints réservation publics | 🔴 Élevé | ✅ `authenticated()` partout |
| ADHERENT voit toutes les réservations | 🔴 Élevé | ✅ Filtrage par `userId` |
| `adherentId` exploitable depuis le body | 🔴 Élevé | ✅ Ignoré pour ADHERENT |
| Pas de distinction 401/403 | 🟡 Moyen | ✅ 401 (token) vs 403 (rôle) |
| Mot de passe admin réinitialisé à chaque start | 🟡 Moyen | ✅ Vérification BCrypt existant |
| `System.out.println` dans le filtre JWT | 🟡 Moyen | ⚠️ À corriger (logging structuré) |

---

## 10. RECOMMANDATIONS

| # | Priorité | Recommandation |
|---|----------|----------------|
| 1 | 🔴 Haute | Définir `JWT_SECRET` dans `docker-compose.yml` avec valeur strong |
| 2 | 🟡 Moyenne | Remplacer les `System.out.println` par SLF4J Logger |
| 3 | 🟡 Moyenne | Implémenter un refresh token pour renouveler les sessions |
| 4 | 🟡 Moyenne | Ajouter un rate limiter sur `/authenticate` |
| 5 | 🟢 Basse | Ajouter des annotations `@Validated` sur les DTOs |
| 6 | 🟢 Basse | Considérer un `@RequestParam adherentId` optionnel pour le DELETE admin |

# ❓ QUESTIONS / RÉPONSES — Sécurité API Réservations

Toutes les questions qui peuvent être posées sur cette implémentation, avec les réponses détaillées.

---

## 1. ARCHITECTURE GÉNÉRALE

### Q1.1 : Quel est le stack technique utilisé ?

**R :** Spring Boot 3.2.5, Spring Security 6.2, JWT (JJWT 0.12.5), Java 21, PostgreSQL, Angular (frontend).

### Q1.2 : Comment l'authentification fonctionne-t-elle ?

**R :** Le client envoie `POST /authenticate` avec `username` + `password`. Le serveur valide les credentials via `AuthenticationManager`, génère un token JWT signé (HMAC-SHA512), et le retourne au client. Pour chaque requête suivante, le client envoie le token dans le header `Authorization: Bearer <token>`.

### Q1.3 : Quelle est la durée de vie du token JWT ?

**R :** 5 heures (18000 secondes). Passé ce délai, le token expire et le client doit se reconnecter via `/authenticate`.

### Q1.4 : Où est stockée la clé secrète JWT ?

**R :** Dans la variable d'environnement `JWT_SECRET`. En développement, un fallback est défini dans `JwtUtil.java`. En production, elle est configurée dans `docker-compose.yml` ou les variables d'environnement du serveur.

### Q1.5 : Pourquoi n'utilise-t-on pas un refresh token ?

**R :** C'est une amélioration prévue. Actuellement, le client doit se reconnecter après 5h. Un refresh token permettrait de renouveler le token sans re-authentification.

---

## 2. RÔLES ET AUTORISATIONS

### Q2.1 : Quels rôles existent dans le système ?

**R :** Deux rôles :
- **ADHERENT** : Membre de la bibliothèque. Accès limité à ses propres réservations.
- **BIBLIOTHECAIRE** : Personnel de la bibliothèque. Accès complet à toutes les opérations.

### Q2.2 : Comment les rôles sont-ils attribués ?

**R :** Via la table `role` (role_id → role_name) liée à `users` par la table de jointure `user_role`. Le `JwtService.getAuthority()` convertit les rôles de la base en autorités Spring Security (`ROLE_ADHERENT`, `ROLE_BIBLIOTHECAIRE`).

### Q2.3 : Un ADHERENT peut-il créer une réservation pour un autre adhérent ?

**R :** Non. L'identité de l'adhérent est extraite du token JWT (`CurrentUserService.getAuthenticatedUserId()`), pas du corps de la requête. Si un ADHERENT envoie `adherentId: 3` dans le body, le service l'ignore et utilise `userId` du token.

### Q2.4 : Un BIBLIOTHECAIRE peut-il créer une réservation pour n'importe qui ?

**R :** Oui. Le paramètre `isBiblio` est vérifié dans le service. Si `true`, le `adherentId` du corps de la requête est utilisé. Le BIBLIOTHECAIRE doit cependant fournir un `adherentId` valide (sinon 400 Bad Request).

### Q2.5 : Un ADHERENT peut-il supprimer une réservation ?

**R :** Non. Le `DELETE /api/reservations/{id}` est configuré avec `hasRole("BIBLIOTHECAIRE")` dans `WebSecurityConfiguration`. Un ADHERENT reçoit `403 Forbidden`.

---

## 3. RÈGLES DE SÉCURITÉ (RS-01 à RS-05)

### Q3.1 : Que se passe-t-il si on accède à `/api/reservations` sans token ?

**R :** Le endpoint est marqué `authenticated()` dans la configuration Spring Security. Sans token valide, `JwtAuthenticationEntryPoint` retourne `401 Unauthorized`.

### Q3.2 : Comment la distinction 401 vs 403 est-elle gérée ?

**R :**
- **401** : Token absent, invalide ou expiré → `JwtAuthenticationEntryPoint`
- **403** : Utilisateaur authentifié mais sans droits → `GlobalExceptionHandler.handleAccessDeniedException()`

### Q3.3 : Comment le filtrage automatique (RS-05) fonctionne-t-il ?

**R :** Dans `ReservationService.listerReservations()` :
```java
if (isBiblio) {
    reservations = repository.findAll(); // BIBLIOTHECAIRE voit tout
} else {
    reservations = repository.findByAdherentId(userId); // ADHERENT ne voit que le sien
}
```

### Q3.4 : Comment RS-04 (identité du token) est-il implémenté ?

**R :** Le controller récupère `userId = currentUserService.getAuthenticatedUserId()` depuis `SecurityContextHolder`. Le service utilise ce `userId` pour un ADHERENT, ignorant `request.getAdherentId()` du body.

### Q3.5 : Un ADHERENT peut-il annuler la réservation d'un autre ?

**R :** Non. Le service vérifie `reservation.getAdherentId().equals(userId)` avant toute opération. Sinon, il lance `AccessDeniedException` → 403.

---

## 4. RÈGLES DE GESTION (RG-01 à RG-06)

### Q4.1 : RG-01 — Peut-on réserver un livre disponible ?

**R :** Non. Si `noOfCopies > 0`, la réservation est rejetée avec `ConflictException` (409) et le message "RG-01 : Le livre est disponible, réservation impossible".

### Q4.2 : RG-02 — Combien de réservations actives par livre/adhérent ?

**R :** Une seule. Le service vérifie les réservations `EN_ATTENTE` ou `DISPONIBLE` pour le même livre et le même adhérent. Si une existe déjà → 409 "RG-02".

### Q4.3 : RG-03 — Quel est le maximum de réservations simultanées ?

**R :** 3. Un adhérent ne peut pas dépasser 3 réservations actives (statut `EN_ATTENTE` ou `DISPONIBLE`). Au-delà → 409 "RG-03".

### Q4.4 : RG-04 — Quelle est la durée d'expiration d'une réservation ?

**R :** 7 jours. `dateExpiration = dateReservation + 7 jours`, calculé côté serveur.

### Q4.5 : RG-05/RG-06 — Quand peut-on annuler une réservation ?

**R :** Seulement si le statut est `EN_ATTENTE` ou `DISPONIBLE`. Les statuts `ANNULEE`, `EXPIREE` et `HONOREE` sont figés → 409 "RG-05/RG-06".

---

## 5. ENDPOINTS ET MATRICE D'AUTORISATION

### Q5.1 : Quels endpoints sont publics ?

**R :**
- `POST /authenticate` — Authentification
- `/swagger-ui/**`, `/v3/api-docs/**` — Documentation API
- `OPTIONS /**` — Requêtes CORS préliminaires

### Q5.2 : Quels endpoints sont réservés au BIBLIOTHECAIRE ?

**R :**
- `DELETE /api/reservations/**` — Suppression
- `/admin/**` — Gestion des livres et utilisateurs

### Q5.3 : Peut-on créer une réservation via Swagger ?

**R :** Oui, mais uniquement avec un token JWT valide. Swagger est configuré avec `bearerAuth` dans `SwaggerConfiguration.java`. Il faut d'abord appeler `/authenticate`, puis entrer le token dans l'interface Swagger.

### Q5.4 : Quel est le contenu de la réponse d'authentification ?

**R :**
```json
{
    "user": {
        "userId": 2,
        "username": "A1",
        "name": "Adherent 1",
        "role": [{"roleId": 2, "roleName": "ADHERENT"}]
    },
    "jwtToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

---

## 6. DONNÉES DE TEST

### Q6.1 : Quels utilisateurs sont créés par init_data.sql ?

**R :**

| ID | Username | Nom | Rôle |
|----|----------|-----|------|
| 1 | admin | Bibliothécaire Principal | BIBLIOTHECAIRE |
| 2 | A1 | Adherent 1 | ADHERENT |
| 3 | A2 | Adherent 2 | ADHERENT |
| 4 | A3 | Adherent 3 | ADHERENT |

### Q6.2 : Quel est le mot de passe par défaut ?

**R :** `123456` pour tous les utilisateurs (hashé en BCrypt dans la base).

### Q6.3 : Quels livres sont disponibles ?

**R :**

| ID | Nom | Copies | Statut |
|----|-----|--------|--------|
| 1 | L1 | 1 | Disponible |
| 2 | L2 | 0 | Emprunté par A3 |
| 3 | L3 | 0 | Emprunté par A3 |
| 4 | L4 | 0 | Emprunté par A3 |
| 5 | L5 | 0 | Emprunté par A3 |

### Q6.4 : Comment créer un admin avec le rôle BIBLIOTHECAIRE ?

**R :** Le script `init_data.sql` insère l'utilisateur `admin` (userId=1) avec le rôle `BIBLIOTHECAIRE` (roleId=1). Si la base existe déjà avec l'ancien rôle `Admin`, le script met à jour le nom du rôle via `ON CONFLICT DO UPDATE SET role_name = EXCLUDED.role_name`.

---

## 7. SÉCURITÉ ET VULNÉRABILITÉS

### Q7.1 : La clé JWT est-elle sécurisée ?

**R :** La clé est maintenant dans une variable d'environnement (`JWT_SECRET`), plus hardcodée dans le code. En production, elle doit être une clé longue (64+ caractères) stockée dans un gestionnaire de secrets.

### Q7.2 : Le CSRF est-il protégé ?

**R :** Non, CSRF est désactivé (`csrf.disable()`) car l'API est stateless (tokens JWT). Le CSRF est pertinent pour les sessions cookies, pas pour les tokens Bearer.

### Q7.3 : Les mots de passe sont-ils hashés ?

**R :** Oui, avec BCrypt (`BCryptPasswordEncoder`). Le hash `$2a$10$...` est vérifié à chaque authentification.

### Q7.4 : Y a-t-il un risque d'injection SQL ?

**R :** Non. L'API utilise Spring Data JPA avec des requêtes nommées, pas de concaténation de chaînes. Les paramètres sont bindés de manière sécurisée.

### Q7.5 : Les tokens expirés sont-ils gérés ?

**R :** Oui. `JwtRequestFilter`捕获 `ExpiredJwtException` et laisse l'utilisateur anonyme. `JwtAuthenticationEntryPoint` retourne ensuite 401.

---

## 8. TESTS

### Q8.1 : Combien de tests existe-t-il ?

**R :** 54 tests au total :
- 28 tests unitaires (`ReservationServiceTest`) — testent la logique métier avec Mockito
- 25 tests d'intégration (`ReservationControllerIntegrationTest`) — testent les endpoints REST avec MockMvc
- 1 test de contexte (`BibliothequeApplicationTests`)

### Q8.2 : Les tests d'intégration nécessitent-ils une base de données ?

**R :** Oui. Ils utilisent `@SpringBootTest` avec `@AutoConfigureMockMvc` et nécessitent une base PostgreSQL en cours d'exécution avec les données `init_data.sql`.

### Q8.3 : Les tests unitaires nécessitent-ils une base de données ?

**R :** Non. Ils utilisent Mockito (`@ExtendWith(MockitoExtension.class)`) pour mocker les repositories. Aucune connexion base de données n'est nécessaire.

### Q8.4 : Quelles règles sont testées ?

**R :**

| Règle | Tests unitaires | Tests d'intégration |
|-------|----------------|---------------------|
| RG-01 | 2 | 1 |
| RG-02 | 2 | 0 |
| RG-03 | 2 | 0 |
| RG-04 | 1 | 0 |
| RG-05/RG-06 | 5 | 0 |
| RS-01 | 0 | 7 |
| RS-02 | 0 | 3 |
| RS-03 | 5 | 4 |
| RS-04 | 2 | 3 |
| RS-05 | 3 | 3 |
| Cas limites | 6 | 5 |

---

## 9. DOCKER ET DÉPLOIEMENT

### Q9.1 : Comment lancer le projet avec Docker ?

**R :**
```bash
# Depuis la racine du projet
docker-compose up -d

# Ou avec un JWT_SECRET personnalisé
JWT_SECRET=maCleSecreteTrèsLongue docker-compose up -d
```

### Q9.2 : Quels services sont dans docker-compose.yml ?

**R :**
- `db` : PostgreSQL 16-alpine avec initialisation via `init_data.sql`
- `backend` : Application Spring Boot construite depuis le `Dockerfile`

### Q9.3 : Comment changer le mot de passe PostgreSQL ?

**R :** Modifier la variable `DB_PASSWORD` dans le fichier `.env` (ou passer la variable d'environnement). La valeur par défaut est `123456`.

### Q9.4 : Le frontend est-il dans docker-compose ?

**R :** Non, le `docker-compose.yml` actuel ne contient que le backend et la base de données. Le frontend Angular doit être lancé séparément (`ng serve`) ou ajouté au compose.

---

## 10. BUGS CORRIGÉS

### Q10.1 : Quel bug a été trouvé dans l'ordre des matchers Spring Security ?

**R :** Le matcher `.requestMatchers("/api/reservations/**").authenticated()` était placé AVANT `.requestMatchers(HttpMethod.DELETE, "/api/reservations/**").hasRole("BIBLIOTHECAIRE")`. Spring Security évalue les matchers dans l'ordre : le premier qui correspond est appliqué. Résultat : le DELETE était accessible à tous les utilisateurs authentifiés, y compris les ADHERENTS.

**Fix :** Placer le matcher DELETE spécifique AVANT le matcher général.

### Q10.2 : Pourquoi les tests d'intégration échouaient avec 409 Conflict ?

**R :** Les réservations créées lors de tests précédents n'étaient pas nettoyées. La règle RG-02 (une seule réservation active par livre/adhérent) rejetait les créations en double.

**Fix :** Ajout de `@BeforeEach cleanReservations()` qui supprime toutes les réservations avant chaque test.

### Q10.3 : Pourquoi le BIBLIOTHECAIRE était traité comme ADHERENT ?

**R :** Les rôles dans la base de données utilisaient les anciens noms (`Admin`/`User`) au lieu de `BIBLIOTHECAIRE`/`ADHERENT`. Le `hasRole("BIBLIOTHECAIRE")` ne trouvait pas le rôle correspondant.

**Fix :** Ajout de `UPDATE role SET role_name = 'BIBLIOTHECAIRE' WHERE role_id = 1` dans le `@BeforeAll` des tests, et mise à jour de `init_data.sql` avec `ON CONFLICT DO UPDATE`.

---

## 11. EXTENSIONS POSSIBLES

### Q11.1 : Comment ajouter un refresh token ?

**R :** Générer un second token avec une durée de vie plus longue (ex: 30 jours). Le client l'utilise pour obtenir un nouveau token d'accès sans se reconnecter. Implémenter un endpoint `POST /refresh` qui valide le refresh token et retourne un nouveau JWT.

### Q11.2 : Comment ajouter du rate limiting ?

**R :** Utiliser un filter Spring (ex: Bucket4j) ou une library comme `spring-boot-starter-quota`. Limiter les appels à `/authenticate` à 5 tentatives par minute par IP.

### Q11.3 : Comment auditer les actions de réservation ?

**R :** Ajouter une table `audit_log` avec `userId`, `action`, `reservationId`, `timestamp`. Logger chaque opération dans le service via un `AuditService`.

### Q11.4 : Comment activer HTTPS ?

**R :** Configurer un reverse proxy (Nginx) avec un certificat SSL/Let's Encrypt devant le backend. Ou configurer Spring Boot avec `server.ssl.*` properties.

### Q11.5 : Comment ajouter de la pagination ?

**R :** Remplacer `findAll()` par `findAll(Pageable)` dans les repositories. Utiliser `Page<T>` dans les return types des services et controllers.

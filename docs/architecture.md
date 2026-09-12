# Architecture du projet — Gestion de Bibliothèque

> Documenté dans le cadre de la Séance 1 (environnement & cycle Git).

## Vue d'ensemble

| Composant | Technologie | Port |
|---|---|---|
| Backend | Spring Boot 3.2.5, Java 21, Spring Security 6, jjwt 0.12 | 8080 |
| Base de données | PostgreSQL (`jdbc:postgresql://localhost:5432/bibliotheque`) | 5432 |
| Frontend | Angular 14 (TypeScript 4.7) | 4200 |

## Arborescence backend (`bibliotheque-backend/`)

Organisation par couches sous `src/main/java/com/ibizabroker/bibliotheque/` :

```
configuration/   WebSecurityConfiguration (SecurityFilterChain, @EnableMethodSecurity),
                 JwtRequestFilter (validation du Bearer token à chaque requête),
                 JwtAuthenticationEntryPoint (401 JSON), JsonAccessDeniedHandler (403 JSON),
                 SwaggerConfiguration (springdoc), CorsConfiguration, TestDataInitializer
controller/      JwtController (POST /authenticate), AdminController (/admin/users),
                 BooksController (/admin/books), BorrowController (/borrow — permitAll),
                 ReservationController (/api/reservations — sécurisé, Séance 2/4)
service/         JwtService (UserDetailsService + génération de tokens),
                 ReservationService (règles métier RG-01 à RG-06, sécurité RS-03/04/05)
dao/             UsersRepository, RoleRepository, BooksRepository, BorrowRepository,
                 ReservationRepository (Spring Data JPA)
entity/          Users, Role, Books, Borrow, Reservation + DTOs
                 (ReservationRequest / ReservationResponse), ReservationStatus (enum)
exceptions/      NotFoundException, ConflictException, ForbiddenException,
                 GlobalExceptionHandler (@RestControllerAdvice -> 400/403/404/409/500 JSON)
util/            JwtUtil (signature/vérification HS384), SecurityUtils
                 (identité de l'utilisateur connecté depuis le SecurityContext — RS-04)
```

## Arborescence frontend (`bibliotheque-frontend/`)

Organisation par modules sous `src/app/` :

```
_auth/           auth.interceptor.ts (ajoute le header Authorization à chaque requête)
_model/          interfaces TypeScript partagées
_service/        books.service.ts, users.service.ts, borrow.service.ts,
                 reservation.service.ts (Séance 3)
header/, home/, login/, logout/, registration/
books-list/, book-details/, create-book/, update-book/
users-list/, user-details/, update-user/
borrow-book/, return-book/
forbidden/       page 403
```

## Flux d'authentification

1. `POST /authenticate` (username + password) → `JwtService` → token JWT signé.
2. Le front stocke le token ; `auth.interceptor.ts` ajoute `Authorization: Bearer <token>`.
3. `JwtRequestFilter` valide le token et peuple le `SecurityContextHolder`.
4. Les rôles (`ROLE_<roleName>`) sont portés par les authorities du principal.
5. 401 (token absent/invalide) vs 403 (rôle insuffisant ou réservation d'autrui) — jamais inversés.

## Base de données

Tables principales : `users`, `role`, `user_role` (ManyToMany), `books`, `borrow`, `reservation`.

Comptes de test (seed idempotent `TestDataInitializer`, détails dans `bibliotheque-backend/TESTING.md`) :
`adherent1/adherent1`, `adherent2/adherent2`, `biblio1/biblio1`.

## Cycle Git

- Branches de travail par séance : `feature/reservation-<prenom>-<nom>`,
  `feature/reservation-ui-<prenom>-<nom>`, `feature/reservation-securite-<prenom>-<nom>`.
- Commits par séance, Pull Requests ouvertes pour revue humaine (jamais fusionnées par l'agent).
- Historique consultable via `git log --oneline --all` ; branches distantes sur `origin`.

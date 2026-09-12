# Architecture du projet — Gestion de Bibliothèque

> Séance 1 — Vérification et documentation de l'environnement.
> Ce document est la référence pour comprendre et relancer le projet à froid.

---

## 1. Vue d'ensemble

| Composant | Technologie | Port | Emplacement |
|---|---|---|---|
| Backend | Spring Boot 3.2.5, Java 21 (tourne sous JDK 24), Spring Security 6, jjwt 0.12, Hibernate 6.4 | 8080 | `bibliotheque-backend/` |
| Base de données | PostgreSQL 16 | 5432 | installation **native** (voir §2) |
| Frontend | Angular 14 (TypeScript 4.7), Bootstrap 5 | 4200 | `bibliotheque-frontend/` |

Le dépôt est un **monorepo** : backend et frontend vivent côte à côte et se lancent séparément.

---

## 2. PostgreSQL : état réel et écart avec la consigne

**Constat à l'audit (à connaître pour la soutenance)** : la consigne de la Séance 1 demandait
PostgreSQL **dans un conteneur Docker**. Sur cette machine, PostgreSQL tourne en **installation
native** (service système, `pg_isready` répond sur `localhost:5432`). Docker est installé et
fonctionnel, mais aucun conteneur PostgreSQL n'existe pour ce projet (seuls des conteneurs
d'autres projets : `m2stock-*`, tous arrêtés).

**Décision** : ne pas forcer une migration — la base native fonctionne et contient des données.
Pour basculer proprement vers Docker plus tard, la commande équivalente à la configuration
actuelle de `application.properties` est :

```bash
docker run -d --name bibliotheque-postgres \
  -e POSTGRES_DB=bibliotheque \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=123456 \
  -p 5432:5432 \
  postgres:16
```

Et la version `docker-compose.yml` équivalente :

```yaml
services:
  db:
    image: postgres:16
    container_name: bibliotheque-postgres
    environment:
      POSTGRES_DB: bibliotheque
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: 123456
    ports:
      - "5432:5432"
    volumes:
      - bibliotheque-pgdata:/var/lib/postgresql/data
volumes:
  bibliotheque-pgdata:
```

**Correspondance avec `application.properties`** :

| Propriété | Valeur | Variable Docker équivalente |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/bibliotheque` | `POSTGRES_DB=bibliotheque` + `-p 5432:5432` |
| `spring.datasource.username` | `postgres` | `POSTGRES_USER=postgres` |
| `spring.datasource.password` | `123456` | `POSTGRES_PASSWORD=123456` |

⚠️ **Avant de basculer** : exporter les données (`pg_dump -U postgres bibliotheque > dump.sql`),
puis les réimporter dans le conteneur (`psql -U postgres bibliotheque < dump.sql`).

---

## 3. Base de données : tables et entités

Liste obtenue via `\dt` sur la base `bibliotheque` (6 tables, schéma `public`) :

| Table | Entité Java | Rôle |
|---|---|---|
| `books` | `Books` | Catalogue : titre, auteur, genre, nombre d'exemplaires |
| `users` | `Users` | Comptes : username, nom, mot de passe (BCrypt) |
| `role` | `Role` | Rôles simples (`roleName` : `Admin`, puis `ADHERENT`/`BIBLIOTHECAIRE` en S4) |
| `user_role` | (table de jointure) | Association ManyToMany `Users` ↔ `Role` |
| `borrow` | `Borrow` | Emprunts : dates d'emprunt/retour/échéance |
| `reservation` | `Reservation` | Réservations (créées à la Séance 2) |

> Les noms de tables/colonnes suivent la stratégie de nommage Hibernate 6 (snake_case).

---

## 4. Arborescence backend (`bibliotheque-backend/`)

```
src/main/java/com/ibizabroker/bibliotheque/
├── configuration/     # Security, filtres, CORS, Swagger
│   ├── WebSecurityConfiguration.java
│   ├── JwtRequestFilter.java
│   ├── JwtAuthenticationEntryPoint.java
│   ├── JsonAccessDeniedHandler.java
│   └── CorsConfiguration.java
├── controller/        # Endpoints REST (couche fine)
│   ├── JwtController.java
│   ├── AdminController.java
│   ├── BooksController.java
│   └── BorrowController.java
├── service/           # Logique métier
│   └── JwtService.java
├── dao/               # Spring Data JPA
│   ├── UsersRepository.java
│   ├── RoleRepository.java
│   ├── BooksRepository.java
│   └── BorrowRepository.java
├── entity/            # Entités JPA + DTOs
│   ├── Users, Role, Books, Borrow
│   └── JsonDataSerializer, JwtRequest, JwtResponse
├── exceptions/        # Exceptions métier + handler global
│   ├── NotFoundException.java
│   └── GlobalExceptionHandler.java
└── util/
    └── JwtUtil.java
```

### Rôle de chaque couche (le flux d'une requête)

1. **Controller** — reçoit la requête HTTP, délègue, met en forme la réponse.
   Aucune règle métier. C'est la porte d'entrée (`@RestController`).
2. **Service** — porte la logique métier et les règles de gestion. C'est ici qu'on
   décide ; le contrôleur, lui, ne fait que transmettre.
3. **Repository (dao)** — interface Spring Data JPA. Dériver de `JpaRepository`
   suffit ; les requêtes dérivées (`findByUsername`...) génèrent le SQL.
4. **Entity** — miroir d'une table. Chaque instance = une ligne.
5. **Exception handling** — `GlobalExceptionHandler` (`@RestControllerAdvice`)
   transforme les exceptions métier en réponses JSON HTTP (404, 409, 400...).
6. **Configuration** — sécurité (chaîne de filtres), CORS, Swagger.

### Comment le frontend appelle le backend

```
Composant Angular  →  Service Angular (books.service.ts)  →  HttpClient
        →  AuthInterceptor (ajoute "Authorization: Bearer <jwt>")
        →  HTTP  localhost:8080
        →  JwtRequestFilter (valide le token, peuple le SecurityContext)
        →  SecurityFilterChain (autorisations)
        →  @RestController  →  Service  →  Repository  →  Hibernate
        →  SQL PostgreSQL  →  JSON retourne par le même chemin
```

---

## 5. Arborescence frontend (`bibliotheque-frontend/`)

```
src/app/
├── _auth/             # auth.interceptor.ts (JWT), auth.guard.ts (routes)
├── _model/            # interfaces TypeScript (books, users...)
├── _service/          # books.service.ts, users.service.ts, borrow.service.ts, user-auth.service.ts
├── header/            # barre de navigation + session
├── home/ login/ logout/ registration/ forbidden/
├── books-list/ book-details/ create-book/ update-book/
├── users-list/ user-details/ update-user/
└── borrow-book/ return-book/
```

- `_auth/` : l'**intercepteur** ajoute le token à chaque requête sortante ;
  le **guard** protège les routes selon les rôles stockés au login.
- `_service/` : **seule couche autorisée à appeler `HttpClient`** — les composants
  appellent toujours un service, jamais le HTTP directement.
- Composants : un dossier = un écran (template + style + classe).

---

## 6. Logs de démarrage : lecture ligne par ligne

### Backend — `mvn spring-boot:run`

| Ligne de log | Signification |
|---|---|
| `Starting BibliothequeApplication using Java 24.0.1 with PID ...` | La JVM démarre, classe principale trouvée |
| `No active profile set, falling back to 1 default profile: "default"` | Aucun profil activé → `application.properties` par défaut. Anodin tant qu'il n'y a qu'un environnement |
| `Bootstrapping Spring Data JPA repositories ... Found N JPA repository interfaces` | Spring Data scanne et enregistre les repositories |
| `Tomcat initialized with port 8080` / `Starting service [Tomcat]` | Le serveur web embarqué s'initialise |
| `HHH000204: Processing PersistenceUnitInfo [name: default]` puis `Hibernate ORM core version 6.4.4.Final` | Hibernate démarre, lit les entités |
| `HikariPool-1 - Starting...` puis `- Start completed` | Le pool de connexions JDBC s'ouvre vers PostgreSQL — **si cette ligne échoue, la base est down ou mal configurée** |
| `Initialized JPA EntityManagerFactory` | Toutes les entités sont validées et mappées |
| `Will secure any request with [...]` | La chaîne de filtres Spring Security s'enregistre : on doit y voir `JwtRequestFilter` (notre filtre) entre `LogoutFilter` et les filtres d'autorisation |
| `Tomcat started on port 8080` | Le serveur accepte les requêtes |
| `Started BibliothequeApplication in X seconds` | Démarrage terminé |

> Séance 1 : `mvn spring-boot:run` démarre sans erreur, connexion PostgreSQL effective
> (pool Hikari ouvert). Vérifié.

### Frontend — `ng serve`

| Ligne | Signification |
|---|---|
| `Browser application bundle generation complete` | Compilation TypeScript/templates OK |
| `Local: http://localhost:4200/` | Le dev-server sert l'application et proxyfine rien : les appels API partent directement vers `http://localhost:8080` (URL en dur dans les services) |
| warnings `budget` | Taille du bundle initial ; cosmétique tant qu'inférieur à la limite |

### Points d'attention (anomalies visibles, non bloquantes)

| Anomalie | Explication | Impact |
|---|---|---|
| `WARNING: sun.misc.Unsafe::objectFieldOffset ... guava.jar` | Maven lui-même (plugin interne), pas notre code | Aucun |
| `WARNING: A restricted method in java.lang.System has been called ... tomcat-embed-core` | Tomcat charge sa lib native sous JDK 24 (systeme de warning JDK 24) | Aucun aujourd'hui ; à surveiller sur les futures JVM |
| `spring.jpa.open-in-view is enabled by default ...` | Hibernate reste ouvert pendant le rendu de la vue — pattern déconseillé | Bonne pratique : ajouter `spring.jpa.open-in-view=false` (à traiter en S2+) |
| `HHH90000025: ... [Books] ... does not need to be accessed concurrently` | Warnings de cache Hibernate 6 sur les tables sans génération d'ID adaptée | Aucun fonctionnel |
| `Using generated security password` **absent** | Normal : on fournit notre propre `UserDetailsService` (JwtService) | — |

---

## 7. API — Swagger et vérifications HTTP

- **Swagger UI** : http://localhost:8080/swagger-ui/index.html (activé en Séance 2 ;
  à la Séance 1 l'API se teste avec curl/Postman).
- Endpoints existants après Séance 1 :

| Endpoint | Auth | Rôle requis | Succès |
|---|---|---|---|
| `POST /authenticate` | public | — | 200 `{user, jwtToken}` |
| `GET /admin/books` | public (lecture catalogue) | — | 200 liste |
| `GET /admin/books/{id}` | public (lecture) | — | 200 |
| `POST /admin/books` | token | `Admin` | 200 (livre créé) |
| `PUT /admin/books/{id}` | token | `Admin` | 200 |
| `DELETE /admin/books/{id}` | token | `Admin` | 200 `{deleted:true}` |
| `GET /admin/users` | token | `Admin` | 200 liste |
| `POST /borrow` | public | — | 200 texte |
| `GET /borrow` | public | — | 200 liste |

Résultats observés lors de la vérification (curl, cf. `docs/postman-collection.json`) :
`POST /authenticate` → **200** avec jwtToken · `GET /admin/books` → **200** ·
`POST /admin/books` sans token → **403** · `POST /admin/books` avec token Admin → **200**.

---

## 8. Relancer l'environnement à froid (checklist soutenance)

```bash
# 1. PostgreSQL (natif) — vérifier qu'il répond
pg_isready -h localhost -p 5432

# 2. Backend
cd bibliotheque-backend
mvn spring-boot:run
# attendre "Started BibliothequeApplication"

# 3. Frontend (autre terminal)
cd bibliotheque-frontend
ng serve
# ouvrir http://localhost:4200
```

Ordre important : base → backend → frontend. Si le backend démarre avant la base,
le pool Hikari échoue et l'application plante au démarrage.

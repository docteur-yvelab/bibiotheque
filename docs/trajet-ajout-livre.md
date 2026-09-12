# Trajet d'une donnée : `POST /admin/books` de bout en bout

> Séance 1 — exercice central. Objectif : pouvoir raconter ce trajet **sans notes**
> devant le formateur. On suit le livre « Clean Code » depuis le clic sur *Submit*
> jusqu'à la ligne en base.

## Vue d'ensemble (la carte mentale)

```
[Browser] create-book.component.ts
      │  (1) l'utilisateur clique Submit
      ▼
books.service.ts ──► HttpClient.post(url, body)
      │  (2) la requête HTTP est construite
      ▼
auth.interceptor.ts
      │  (3) le header Authorization: Bearer <jwt> est ajouté
      ▼
HTTP POST http://localhost:8080/admin/books
      ▼
JwtRequestFilter.doFilterInternal
      │  (4) le token est vérifié, le SecurityContext peuplé
      ▼
WebSecurityConfiguration.securityFilterChain
      │  (5) autorisations : la route + le rôle sont-ils ok ?
      ▼
BooksController.createBook(@PostMapping "/admin/books")
      │  (6) le contrôleur reçoit le JSON désérialisé en Books
      ▼
BooksRepository.save(book)   [Spring Data JPA]
      │  (7) Hibernate génère et exécute le SQL
      ▼
PostgreSQL — table books : une nouvelle ligne
      │  (8) l'ID généré est renvoyé, puis le JSON de réponse repart
      ▼
[Browser] redirection vers la liste des livres
```

---

## Étape 1 — Le composant Angular : `create-book.component.ts`

```ts
book: Books = new Books();          // l'objet relié au formulaire par [(ngModel)]

saveBook() {
  this.booksService.createBook(this.book).subscribe(data => {
    this.goToBooksList();           // succès -> on va vers /books
  },
  error => console.log(error));     // échec -> (à améliorer : voir Séance 3)
}

onSubmit() { this.saveBook(); }     // déclenché par (ngSubmit) du <form>
```

Dans le template (`create-book.component.html`), chaque champ est relié à l'objet
par **two-way binding** : `[(ngModel)]="book.bookName"`. À la soumission, `this.book`
contient donc `{bookName: "Clean Code", bookAuthor: "Robert C. Martin", ...}`.

**Point clé** : le composant ne connaît ni HTTP, ni l'URL, ni le token. Il connaît
uniquement `BooksService`. C'est le premier principe d'architecture du projet :
*les composants ne font pas de réseau*.

---

## Étape 2 — Le service : `books.service.ts`

```ts
private baseURL = "http://localhost:8080/admin/books";

createBook(book: Books): Observable<Object> {
  return this.httpClient.post(`${this.baseURL}`, book);
}
```

Le service :
1. connaît **l'URL absolue** du backend (pas de proxy Angular ici) ;
2. sérialise `book` en JSON (automatique par `HttpClient`) ;
3. retourne un `Observable` — le composant s'y abonne avec `subscribe()`.
   **La requête ne part que si quelqu'un s'abonne** (les Observables HTTP sont froids).

---

## Étape 3 — L'intercepteur : `auth.interceptor.ts`

```ts
const token = this.userAuthService.getToken();        // localStorage["jwtToken"]
req = this.addToken(req, token);                      // clone avec le header
```

Chaque requête sortante passe ici (enregistré dans `app.module.ts` via
`HTTP_INTERCEPTORS`). L'intercepteur :

1. lit le token stocké au login (`user-auth.service.ts` → `localStorage`) ;
2. **clone** la requête (les `HttpRequest` sont immuables) en ajoutant
   `Authorization: Bearer <jwt>` ;
3. laisse passer vers le backend.

Cas particuliers : si la requête porte le header `No-Auth: True`, aucune injection
(cas de `/authenticate` lui-même). Sur réponse **401**, l'intercepteur redirige vers
`/login` ; sur **403**, vers `/forbidden`.

---

## Étape 4 — Le filtre backend : `JwtRequestFilter`

Premier code Java touché. C'est un `OncePerRequestFilter` (une exécution garantie
par requête) enregistré **avant** le filtre d'authentification standard :

```java
if (requestTokenHeader.startsWith("Bearer ")) {
    jwtToken = requestTokenHeader.substring(7);
    username = jwtUtil.getUsernameFromToken(jwtToken);   // décode et vérifie la signature
}
...
UserDetails userDetails = jwtService.loadUserByUsername(username);  // l'utilisateur en base
if (jwtUtil.validateToken(jwtToken, userDetails)) {     // signature + expiration
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
}
```

Ce que fait exactement ce filtre :
1. extrait le token du header `Authorization` ;
2. `JwtUtil` **re-signe** les claims avec la clé secrète : si la signature ne
   correspond pas, ou si le token est expiré, exception → pas d'authentification ;
3. recharge l'utilisateur depuis `UsersRepository` (username du token → ligne `users`) ;
4. **peuple le `SecurityContextHolder`** avec les *authorities* :
   `ROLE_` + `roleName` (ex. `ROLE_Admin`).

À partir de là, Spring Security **sait qui parle** pour toute la durée de la requête.

---

## Étape 5 — La chaîne de sécurité : `WebSecurityConfiguration`

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/authenticate", "/borrow/**").permitAll()
    .requestMatchers(HttpMethod.GET, "/admin/books").permitAll()
    ...
    .anyRequest().authenticated())
```

Pour `POST /admin/books` : aucune règle `permitAll` ne correspond → la règle finale
`authenticated()` s'applique. Le filtre est authentifié ? oui → la requête passe au
contrôleur. Puis l'annotation `@PreAuthorize("hasRole('Admin')")` sur la méthode
vérifie le **rôle** (l'une des *authorities* lues à l'étape 4).

**Deux échecs possibles, deux réponses différentes** :
- pas de token / token invalide → `JwtAuthenticationEntryPoint` → **401** ;
- identité connue mais rôle insuffisant → `JsonAccessDeniedHandler` → **403**.

---

## Étape 6 — Le contrôleur : `BooksController.createBook`

```java
@PreAuthorize("hasRole('Admin')")
@PostMapping("/books")
public Books createBook(@RequestBody Books book) {
    return booksRepository.save(book);
}
```

- `@RequestBody` : Jackson désérialise le JSON `{bookName: ...}` en entité `Books` ;
- `@PreAuthorize` : contrôle de rôle au moment de l'invocation (méthode security) ;
- le contrôleur délègue immédiatement au repository : **zéro logique métier ici**.

---

## Étape 7 — Le repository et Hibernate : `BooksRepository`

```java
public interface BooksRepository extends JpaRepository<Books, Integer> { }
```

`save(book)` : l'entité n'a pas d'ID → Hibernate décide d'un **persist**. SQL généré :

```sql
insert into books (book_name, book_author, book_genre, no_of_copies) values (?, ?, ?, ?)
```

Puis Hibernate récupère l'ID généré par la base et le réinjecte dans l'entité
(`select currval(...)` ou `returning` selon la stratégie d'ID).

---

## Étape 8 — La ligne en base (preuve)

Après l'opération :

```sql
bibliotheque=# SELECT book_id, book_name, book_author, no_of_copies FROM books ORDER BY book_id DESC LIMIT 1;
 book_id |  book_name  |    book_author     | no_of_copies
---------+-------------+--------------------+--------------
      12 | Clean Code  | Robert C. Martin   |            2
(1 row)
```

Et la réponse HTTP au navigateur : `200 OK` avec le JSON du livre (ID compris),
que le composant reçoit dans son `subscribe(data => ...)` avant de rediriger
vers la liste — qui, elle, relit la table via `GET /admin/books`.

---

## Les 8 phrases à savoir réciter

1. Le composant relie le formulaire à un objet `Books` et appelle **son service**.
2. Le service construit la requête HTTP vers l'URL du backend et retourne un Observable.
3. L'intercepteur clone la requête et y injecte `Authorization: Bearer <token>`.
4. Le filtre JWT vérifie la signature du token, recharge l'utilisateur et peuple le SecurityContext avec ses rôles.
5. La chaîne de sécurité décide : public / authentifié / rôle requis — 401 si inconnu, 403 si insuffisant.
6. Le contrôleur reçoit le JSON désérialisé et délègue — sans logique métier.
7. Le repository (Spring Data) demande à Hibernate, qui génère l'`INSERT` et récupère l'ID.
8. PostgreSQL persiste la ligne ; la réponse JSON repart par le chemin inverse jusqu'au `subscribe`.

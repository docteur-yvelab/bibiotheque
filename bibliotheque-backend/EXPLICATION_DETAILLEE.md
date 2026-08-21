# 📖 Explication détaillée — Module Réservation

> Ce document explique **ligne par ligne** tout ce qui a été fait dans le projet, depuis la migration Spring Boot jusqu'au module réservation.

---

## 📋 Sommaire

1. [Migration Spring Boot 2 → 3 (pom.xml)](#1--migration-spring-boot-2--3-pomxml)
2. [Migration MySQL → PostgreSQL (application.properties)](#2--migration-mysql--postgresql-applicationproperties)
3. [Migration javax → jakarta (toutes les entités)](#3--migration-javax--jakarta-toutes-les-entités)
4. [Migration Lombok → Getters/Setters manuels](#4--migration-lombok--getterssetters-manuels)
5. [Migration JJWT 0.9.1 → 0.12.5 (JwtUtil)](#5--migration-jjwt-091--0125-jwtutil)
6. [Migration Springfox → SpringDoc OpenAPI](#6--migration-springfox--springdoc-openapi)
7. [Nouveau : Entité Reservation](#7--nouveau--entité-reservation)
8. [Nouveau : ReservationRequest](#8--nouveau--reservationrequest)
9. [Nouveau : ReservationResponse](#9--nouveau--reservationresponse)
10. [Nouveau : ReservationStatus (Enum)](#10--nouveau--reservationstatus-enum)
11. [Nouveau : ReservationRepository](#11--nouveau--reservationrepository)
12. [Nouveau : ConflictException](#12--nouveau--conflictexception)
13. [Nouveau : ReservationService (Logique métier)](#13--nouveau--reservationservice-logique-métier)
14. [Nouveau : ReservationController (API REST)](#14--nouveau--reservationcontroller-api-rest)
15. [Correction : WebSecurityConfiguration](#15--correction--websecurityconfiguration)
16. [Tests unitaires : ReservationServiceTest](#16--tests-unitaires--reservationservicetest)

---

## 1 — Migration Spring Boot 2 → 3 (pom.xml)

### Avant (ancien pom.xml)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.4.5</version>  <!-- Ancienne version -->
</parent>
```

### Après (nouveau pom.xml)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>  <!-- Nouvelle version -->
</parent>
```

**Ligne par ligne :**

| Ligne | Avant | Après | Explication |
|-------|-------|-------|-------------|
| `<version>2.4.5</version>` | Spring Boot 2.4.5 | `3.2.5` | Passage à Spring Boot 3 (nécessite Java 17+) |
| `<java.version>1.8</java.version>` | Java 8 | `21` | Passage à Java 21 (LTS) |
| `<jjwt.version>0.9.1</jjwt.version>` | JJWT 0.9.1 | `0.12.5` | Mise à jour de la lib JWT |
| `<springdoc.version>2.5.0</springdoc.version>` | *(nouveau)* | `2.5.0` | Ajout de SpringDoc OpenAPI |

### Nouvelles dépendances ajoutées

```xml
<!-- Validation Spring Boot 3 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

**Explication :** Permet d'utiliser les annotations `@NotNull`, `@NotBlank` etc. sur les DTOs.

---

## 2 — Migration MySQL → PostgreSQL (application.properties)

### Avant

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/bibliotheque
spring.datasource.username=root
spring.datasource.password=mysql
```

### Après

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/bibliotheque
spring.datasource.username=postgres
spring.datasource.password=123456
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

**Ligne par ligne :**

| Ligne | Avant | Après | Explication |
|-------|-------|-------|-------------|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/bibliotheque` | `jdbc:postgresql://localhost:5432/bibliotheque` | Changement de base de données |
| `spring.datasource.username` | `root` | `postgres` | Utilisateur PostgreSQL par défaut |
| `spring.datasource.password` | `mysql` | `123456` | Mot de passe PostgreSQL |
| `spring.datasource.driver-class-name` | *(absent)* | `org.postgresql.Driver` | Driver JDBC PostgreSQL (obligatoire) |
| `spring.jpa.properties.hibernate.dialect` | `MySQL5InnoDBDialect` | `PostgreSQLDialect` | Dialecte Hibernate pour PostgreSQL |

---

## 3 — Migration javax → jakarta (toutes les entités)

Spring Boot 3 utilise **Jakarta EE** au lieu de **Java EE**. Tous les imports changent.

### Exemple : Books.java

**Avant :**
```java
import javax.persistence.*;
```

**Après :**
```java
import jakarta.persistence.*;
```

### Changements spécifiques

| Fichier | Avant | Après |
|---------|-------|-------|
| `Books.java` | `import javax.persistence.*;` | `import jakarta.persistence.*;` |
| `Borrow.java` | `import javax.persistence.*;` | `import jakarta.persistence.*;` |
| `Users.java` | `import javax.persistence.*;` | `import jakarta.persistence.*;` |
| `Role.java` | `import javax.persistence.*;` | `import jakarta.persistence.*;` |
| `JwtAuthenticationEntryPoint.java` | `import javax.servlet.*;` | `import jakarta.servlet.*;` |
| `JwtRequestFilter.java` | `import javax.servlet.*;` | `import jakarta.servlet.*;` |

**Explication :** `javax` est l'ancien namespace Java EE. `jakarta` est le nouveau namespace Jakarta EE 9+ utilisé par Spring Boot 3.

---

## 4 — Migration Lombok → Getters/Setters manuels

### Avant (avec Lombok)

```java
@Data  // Lombok génère automatiquement getters/setters/toString/equals/hashCode
@Entity
@Table(name = "Books")
public class Books {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer bookId;
    String bookName;
    // Pas de getters/setters manuels
}
```

### Après (sans Lombok)

```java
@Entity
@Table(name = "Books")
public class Books {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer bookId;
    private String bookName;
    private String bookAuthor;
    private String bookGenre;
    private Integer noOfCopies;

    // Getters
    public Integer getBookId() {
        return bookId;
    }

    public String getBookName() {
        return bookName;
    }

    // Setters
    public void setBookId(Integer bookId) {
        this.bookId = bookId;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }
}
```

**Ligne par ligne :**

| Ligne | Avant | Après | Explication |
|-------|-------|-------|-------------|
| `@Data` | Présent | Supprimé | Lombok n'est plus utilisé |
| `Integer bookId;` | Package-private | `private Integer bookId;` | Champ rendu private (bonne pratique) |
| `public Integer getBookId()` | *(absent)* | Ajouté | Getter explicite pour accéder à la valeur |
| `public void setBookId(Integer bookId)` | *(absent)* | Ajouté | Setter explicite pour modifier la valeur |

**Fichiers concernés :** `Books.java`, `Borrow.java`, `Users.java`, `Role.java`

---

## 5 — Migration JJWT 0.9.1 → 0.12.5 (JwtUtil)

### Avant (JJWT 0.9.1)

```java
private static final String SECRET_KEY = "learn_programming_yourself";

private Claims getAllClaimsFromToken(String token) {
    return Jwts.parser()
            .setSigningKey(SECRET_KEY)
            .parseClaimsJws(token)
            .getBody();
}

public String generateToken(UserDetails userDetails) {
    return Jwts.builder()
            .setClaims(claims)
            .setSubject(userDetails.getUsername())
            .setIssuedAt(new Date(System.currentTimeMillis()))
            .setExpiration(new Date(System.currentTimeMillis() + TOKEN_VALIDITY * 1000))
            .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
            .compact();
}
```

### Après (JJWT 0.12.5)

```java
private static final String SECRET_KEY = "learn_programming_yourself_this_is_a_long_key_for_hs512_algorithm_2024";

private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
}

private Claims getAllClaimsFromToken(String token) {
    return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
}

public String generateToken(UserDetails userDetails) {
    Map<String, Object> claims = new HashMap<>();
    return Jwts.builder()
            .claims(claims)
            .subject(userDetails.getUsername())
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(new Date(System.currentTimeMillis() + TOKEN_VALIDITY * 1000))
            .signWith(getSigningKey())
            .compact();
}
```

**Ligne par ligne :**

| Ligne | Avant | Après | Explication |
|-------|-------|-------|-------------|
| `SECRET_KEY` | `"learn_programming_yourself"` (25 chars) | `"learn_programming_yourself_this_is_a_long_key..."` (58 chars) | Clé plus longue requise par HS512 |
| `getSigningKey()` | *(absent)* | `Keys.hmacShaKeyFor(...)` | Génère une `SecretKey` à partir de la chaîne |
| `Jwts.parser().setSigningKey()` | Ancienne API | `Jwts.parser().verifyWith(getSigningKey())` | Nouvelle API fluide JJWT 0.12.x |
| `.parseClaimsJws()` | Ancienne API | `.parseSignedClaims()` | Méthode renommée dans JJWT 0.12.x |
| `.getBody()` | Ancienne API | `.getPayload()` | Méthode renommée dans JJWT 0.12.x |
| `.setClaims(claims)` | Ancienne API | `.claims(claims)` | Méthode renommée |
| `.setSubject()` | Ancienne API | `.subject()` | Méthode renommée |
| `.setIssuedAt()` | Ancienne API | `.issuedAt()` | Méthode renommée |
| `.setExpiration()` | Ancienne API | `.expiration()` | Méthode renommée |
| `.signWith(HS512, key)` | Ancienne API | `.signWith(getSigningKey())` | L'algorithme est déduit de la clé |

---

## 6 — Migration Springfox → SpringDoc OpenAPI

### Avant (Springfox - ne fonctionne plus avec Spring Boot 3)

```xml
<dependency>
    <groupId>io.springfox</groupId>
    <artifactId>springfox-boot-starter</artifactId>
    <version>3.0.0</version>
</dependency>
```

### Après (SpringDoc OpenAPI)

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
</dependency>
```

### Nouvelle configuration SwaggerConfiguration.java

```java
package com.ibizabroker.bibliotheque.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfiguration {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Bibliothèque API")
                        .description("API REST de gestion de bibliothèque - Spring Boot 3 + PostgreSQL")
                        .version("1.0.0")
                        .contact(new Contact().name("KFOKAM48")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `@Configuration` | Indique à Spring que cette classe définit des beans |
| `@Bean` | La méthode retourne un bean Spring (OpenAPI) |
| `new OpenAPI()` | Crée la configuration OpenAPI v3 |
| `.info(new Info()...)` | Métadonnées de l'API (titre, description, version) |
| `.addSecurityItem(...)` | Ajoute la sécurité globale (tous les endpoints) |
| `.components(new Components()...)` | Définit le schéma d'authentification Bearer JWT |

### URL Swagger UI

```
http://localhost:8080/swagger-ui/index.html
```

---

## 7 — Nouveau : Entité Reservation

**Fichier :** `entity/Reservation.java`

```java
package com.ibizabroker.bibliotheque.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "Reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer reservationId;

    @Column(nullable = false)
    private Integer livreId;

    @Column(nullable = false)
    private Integer adherentId;

    @Temporal(TemporalType.TIMESTAMP)
    @JsonSerialize(using = JsonDataSerializer.class)
    @Column(nullable = false)
    private Date dateReservation;

    @Temporal(TemporalType.TIMESTAMP)
    @JsonSerialize(using = JsonDataSerializer.class)
    @Column(nullable = false)
    private Date dateExpiration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus statut;

    // Getters et Setters...
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `@Entity` | Indique que cette classe est une entité JPA (table en base) |
| `@EntityListeners(AuditingEntityListener.class)` | Active l'audit automatique (dates de création/modification) |
| `@Table(name = "Reservation")` | Nom de la table dans PostgreSQL |
| `@Id` | Clé primaire |
| `@GeneratedValue(strategy = GenerationType.IDENTITY)` | Auto-incrément (PostgreSQL SERIAL) |
| `@Column(nullable = false)` | Colonne obligatoire (NOT NULL en base) |
| `@Temporal(TemporalType.TIMESTAMP)` | Stocke la date avec l'heure (pas juste la date) |
| `@JsonSerialize(using = JsonDataSerializer.class)` | Formate la date en JSON |
| `@Enumerated(EnumType.STRING)` | Stocke le nom du statut comme chaîne (pas l'index) |
| `private Integer livreId` | ID du livre réservé (clé étrangère logique) |
| `private Integer adherentId` | ID de l'adhérent qui réserve |
| `private Date dateReservation` | Date de création de la réservation |
| `private Date dateExpiration` | Date d'expiration (dateReservation + 7 jours) |
| `private ReservationStatus statut` | Statut : EN_ATTENTE, DISPONIBLE, ANNULEE, EXPIREE, HONOREE |

---

## 8 — Nouveau : ReservationRequest

**Fichier :** `entity/ReservationRequest.java`

```java
package com.ibizabroker.bibliotheque.entity;

public class ReservationRequest {

    private Integer livreId;
    private Integer adherentId;

    // Getters et Setters...
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `public class ReservationRequest` | DTO (Data Transfer Object) pour recevoir les données du client |
| `private Integer livreId` | ID du livre à réserver |
| `private Integer adherentId` | ID de l'adhérent qui fait la réservation |
| `getLivreId()` / `setLivreId()` | Accesseurs pour JSON (Jackson) |

**Pourquoi un DTO ?** Sépare la couche API de la couche base de données. Le client n'envoie que 2 champs, pas toute l'entité.

---

## 9 — Nouveau : ReservationResponse

**Fichier :** `entity/ReservationResponse.java`

```java
package com.ibizabroker.bibliotheque.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Date;

public class ReservationResponse {

    private Integer reservationId;
    private Integer livreId;
    private Integer adherentId;

    @JsonSerialize(using = JsonDataSerializer.class)
    private Date dateReservation;

    @JsonSerialize(using = JsonDataSerializer.class)
    private Date dateExpiration;

    private ReservationStatus statut;

    // Constructeur à partir de l'entité Reservation
    public ReservationResponse(Reservation reservation) {
        this.reservationId = reservation.getReservationId();
        this.livreId = reservation.getLivreId();
        this.adherentId = reservation.getAdherentId();
        this.dateReservation = reservation.getDateReservation();
        this.dateExpiration = reservation.getDateExpiration();
        this.statut = reservation.getStatut();
    }

    // Getters...
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `public class ReservationResponse` | DTO de sortie (retourné au client) |
| `public ReservationResponse(Reservation reservation)` | Constructeur qui convertit l'entité en DTO |
| `this.reservationId = reservation.getReservationId()` | Copie l'ID de la réservation |
| `this.statut = reservation.getStatut()` | Copie le statut |

**Pourquoi ?** Retourne au client uniquement les champs nécessaires (pas les données internes de JPA).

---

## 10 — Nouveau : ReservationStatus (Enum)

**Fichier :** `entity/ReservationStatus.java`

```java
package com.ibizabroker.bibliotheque.entity;

public enum ReservationStatus {
    EN_ATTENTE,
    DISPONIBLE,
    ANNULEE,
    EXPIREE,
    HONOREE
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `public enum ReservationStatus` | Type énuméré pour les statuts |
| `EN_ATTENTE` | Réservation en attente (livre pas encore disponible) |
| `DISPONIBLE` | Le livre est disponible, l'adhérent doit le récupérer |
| `ANNULEE` | Réservation annulée par l'adhérent ou le système |
| `EXPIREE` | Réservation expirée (dépassée dateExpiration) |
| `HONOREE` | Réservation honorée (livre récupéré par l'adhérent) |

---

## 11 — Nouveau : ReservationRepository

**Fichier :** `dao/ReservationRepository.java`

```java
package com.ibizabroker.bibliotheque.dao;

import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

    List<Reservation> findByStatut(ReservationStatus statut);

    List<Reservation> findByAdherentId(Integer adherentId);

    List<Reservation> findByAdherentIdAndStatut(Integer adherentId, ReservationStatus statut);

    List<Reservation> findByLivreIdAndStatut(Integer livreId, ReservationStatus statut);

    List<Reservation> findByAdherentIdAndStatutIn(Integer adherentId, List<ReservationStatus> statuts);
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `@Repository` | Indique que c'est un accès à la base de données |
| `extends JpaRepository<Reservation, Integer>` | Hérite de JPA → CRUD automatique (save, findById, findAll, delete...) |
| `findByStatut(ReservationStatus statut)` | Recherche par statut → Spring génère la requête SQL automatiquement |
| `findByAdherentId(Integer adherentId)` | Recherche par adhérent |
| `findByAdherentIdAndStatut(...)` | Recherche par adhérent + statut (2 critères) |
| `findByLivreIdAndStatut(...)` | Recherche par livre + statut |
| `findByAdherentIdAndStatutIn(...)` | Recherche par adhérent + liste de statuts (IN SQL) |

**Note :** Spring Data JPA génère automatiquement les requêtes SQL à partir des noms de méthodes. Pas besoin d'écrire du SQL.

---

## 12 — Nouveau : ConflictException

**Fichier :** `exceptions/ConflictException.java`

```java
package com.ibizabroker.bibliotheque.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `@ResponseStatus(value = HttpStatus.CONFLICT)` | Retourne automatiquement le code HTTP **409** |
| `extends RuntimeException` | Exception non-checkée (pas besoin de la capturer) |
| `public ConflictException(String message)` | Constructeur avec message d'erreur |
| `super(message)` | Passe le message à la classe mère `RuntimeException` |

**Utilisé pour :** RG-01, RG-02, RG-03, RG-05, RG-06 (violations de règles de gestion).

---

## 13 — Nouveau : ReservationService (Logique métier)

**Fichier :** `service/ReservationService.java`

```java
@Service
public class ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BooksRepository booksRepository;

    @Autowired
    private UsersRepository usersRepository;
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `@Service` | Indique que c'est un service Spring (couche métier) |
| `@Autowired private ReservationRepository` | Injection du repository pour accéder à la table Reservation |
| `@Autowired private BooksRepository` | Injection du repository pour accéder à la table Books |
| `@Autowired private UsersRepository` | Injection du repository pour accéder à la table Users |

---

### Méthode `creerReservation()`

```java
public ReservationResponse creerReservation(ReservationRequest request) {
    // 1. Validation des champs obligatoires
    if (request.getLivreId() == null) {
        throw new IllegalArgumentException("Le champ 'livreId' est obligatoire.");
    }
    if (request.getAdherentId() == null) {
        throw new IllegalArgumentException("Le champ 'adherentId' est obligatoire.");
    }

    // 2. Vérifier que le livre existe
    Books livre = booksRepository.findById(request.getLivreId())
            .orElseThrow(() -> new NotFoundException("Livre avec l'id " + request.getLivreId() + " non trouvé."));

    // 3. Vérifier que l'adhérent existe
    Users adherent = usersRepository.findById(request.getAdherentId())
            .orElseThrow(() -> new NotFoundException("Adhérent avec l'id " + request.getAdherentId() + " non trouvé."));

    // 4. RG-01 : On ne peut réserver qu'un livre indisponible
    if (livre.getNoOfCopies() > 0) {
        throw new ConflictException("RG-01 : Le livre '" + livre.getBookName() + "' est disponible, réservation impossible.");
    }

    // 5. RG-02 : Un adhérent ne peut avoir qu'une seule réservation active sur un même livre
    List<Reservation> reservationsEnAttente = reservationRepository
            .findByLivreIdAndStatut(request.getLivreId(), ReservationStatus.EN_ATTENTE);
    List<Reservation> reservationsDisponibles = reservationRepository
            .findByLivreIdAndStatut(request.getLivreId(), ReservationStatus.DISPONIBLE);
    List<Reservation> reservationsActivesLivre = new ArrayList<>(reservationsEnAttente);
    reservationsActivesLivre.addAll(reservationsDisponibles);

    boolean dejaReserve = reservationsActivesLivre.stream()
            .anyMatch(r -> r.getAdherentId().equals(request.getAdherentId()));
    if (dejaReserve) {
        throw new ConflictException("RG-02 : L'adhérent a déjà une réservation active pour ce livre.");
    }

    // 6. RG-03 : Un adhérent ne peut pas dépasser 3 réservations actives simultanées
    List<Reservation> reservationsActivesAdherent = reservationRepository
            .findByAdherentIdAndStatutIn(request.getAdherentId(),
                    Arrays.asList(ReservationStatus.EN_ATTENTE, ReservationStatus.DISPONIBLE));
    if (reservationsActivesAdherent.size() >= 3) {
        throw new ConflictException("RG-03 : L'adhérent ne peut pas dépasser 3 réservations actives simultanées.");
    }

    // 7. Création de la réservation
    Reservation reservation = new Reservation();
    reservation.setLivreId(request.getLivreId());
    reservation.setAdherentId(request.getAdherentId());
    reservation.setStatut(ReservationStatus.EN_ATTENTE);

    // 8. RG-04 : dateExpiration = dateReservation + 7 jours
    Date now = new Date();
    reservation.setDateReservation(now);

    Calendar cal = Calendar.getInstance();
    cal.setTime(now);
    cal.add(Calendar.DATE, 7);
    reservation.setDateExpiration(cal.getTime());

    // 9. Sauvegarder et retourner
    Reservation saved = reservationRepository.save(reservation);
    return new ReservationResponse(saved);
}
```

**Explication détaillée :**

| Étape | Ligne(s) | Explication |
|-------|----------|-------------|
| 1 | `if (request.getLivreId() == null)` | Vérifie que le client a envoyé un livreId |
| 2 | `booksRepository.findById(...)` | Cherche le livre en base. Si introuvable → 404 |
| 3 | `usersRepository.findById(...)` | Cherche l'adhérent en base. Si introuvable → 404 |
| 4 | `if (livre.getNoOfCopies() > 0)` | **RG-01** : Le livre est disponible → refus (409) |
| 5 | `findByLivreIdAndStatut(...)` | **RG-02** : Cherche les réservations actives pour ce livre |
| 5 | `reservationsActivesLivre.stream().anyMatch(...)` | Vérifie si l'adhérent a déjà réservé ce livre |
| 6 | `findByAdherentIdAndStatutIn(...)` | **RG-03** : Compte les réservations actives de l'adhérent |
| 6 | `reservationsActivesAdherent.size() >= 3` | Si ≥ 3 → refus (409) |
| 7 | `reservation.setStatut(ReservationStatus.EN_ATTENTE)` | Nouvelle réservation = EN_ATTENTE par défaut |
| 8 | `cal.add(Calendar.DATE, 7)` | **RG-04** : Ajoute 7 jours à la date courante |
| 9 | `reservationRepository.save(reservation)` | Sauvegarde en base PostgreSQL |

---

### Méthode `listerReservations()`

```java
public List<ReservationResponse> listerReservations(ReservationStatus statut, Integer adherentId) {
    List<Reservation> reservations;

    if (statut != null && adherentId != null) {
        reservations = reservationRepository.findByAdherentIdAndStatut(adherentId, statut);
    } else if (statut != null) {
        reservations = reservationRepository.findByStatut(statut);
    } else if (adherentId != null) {
        reservations = reservationRepository.findByAdherentId(adherentId);
    } else {
        reservations = reservationRepository.findAll();
    }

    List<ReservationResponse> responses = new ArrayList<>();
    for (Reservation r : reservations) {
        responses.add(new ReservationResponse(r));
    }
    return responses;
}
```

**Explication :** Gère 4 cas de filtrage :

| Filtres | Méthode utilisée |
|---------|------------------|
| statut + adherentId | `findByAdherentIdAndStatut()` |
| statut seul | `findByStatut()` |
| adherentId seul | `findByAdherentId()` |
| aucun filtre | `findAll()` |

---

### Méthode `annulerReservation()`

```java
public ReservationResponse annulerReservation(Integer id) {
    Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Réservation avec l'id " + id + " non trouvée."));

    // RG-05 + RG-06 : Vérification du statut
    if (reservation.getStatut() != ReservationStatus.EN_ATTENTE
            && reservation.getStatut() != ReservationStatus.DISPONIBLE) {
        throw new ConflictException("RG-05/RG-06 : Une réservation avec le statut '"
                + reservation.getStatut() + "' ne peut pas être annulée.");
    }

    reservation.setStatut(ReservationStatus.ANNULEE);
    Reservation updated = reservationRepository.save(reservation);
    return new ReservationResponse(updated);
}
```

**Explication :**

| Étape | Ligne | Explication |
|-------|-------|-------------|
| 1 | `findById(id)` | Cherche la réservation. Si introuvable → 404 |
| 2 | `if (statut != EN_ATTENTE && statut != DISPONIBLE)` | **RG-05/RG-06** : Seul EN_ATTENTE ou DISPONIBLE peut être annulé |
| 3 | `setStatut(ReservationStatus.ANNULEE)` | Change le statut vers ANNULEE |
| 4 | `save(reservation)` | Sauvegarde en base |

---

## 14 — Nouveau : ReservationController (API REST)

**Fichier :** `controller/ReservationController.java`

```java
@Tag(name = "Réservations", description = "Gestion des réservations de livres")
@CrossOrigin("http://localhost:4200/")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `@Tag(name = "Réservations")` | Groupe Swagger UI pour organiser les endpoints |
| `@CrossOrigin("http://localhost:4200/")` | Autorise les appels depuis Angular (port 4200) |
| `@RestController` | Indique que c'est un contrôleur REST (retourne du JSON) |
| `@RequestMapping("/api/reservations")` | Préfixe de l'URL pour tous les endpoints |
| `@Autowired private ReservationService` | Injection du service métier |

---

### Endpoint POST (Créer)

```java
@Operation(summary = "Créer une réservation", description = "...")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "Succès"),
    @ApiResponse(responseCode = "400", description = "Champ(s) manquant(s)"),
    @ApiResponse(responseCode = "404", description = "Livre/adhérent non trouvé"),
    @ApiResponse(responseCode = "409", description = "RG-01, RG-02 ou RG-03 violée")
})
@PostMapping
public ResponseEntity<ReservationResponse> creerReservation(@RequestBody ReservationRequest request) {
    ReservationResponse response = reservationService.creerReservation(request);
    return new ResponseEntity<>(response, HttpStatus.CREATED);
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `@Operation(summary = "...")` | Description Swagger pour cet endpoint |
| `@ApiResponses(...)` | Liste des codes HTTP possibles et leurs descriptions |
| `@PostMapping` | Méthode HTTP POST (création) |
| `@RequestBody ReservationRequest request` | Le corps de la requête JSON est converti en objet Java |
| `new ResponseEntity<>(response, HttpStatus.CREATED)` | Retourne le code HTTP **201** avec la réponse |

---

### Endpoint GET (Lister)

```java
@GetMapping
public ResponseEntity<List<ReservationResponse>> listerReservations(
        @RequestParam(required = false) ReservationStatus statut,
        @RequestParam(required = false) Integer adherentId) {
    List<ReservationResponse> reservations = reservationService.listerReservations(statut, adherentId);
    return ResponseEntity.ok(reservations);
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `@GetMapping` | Méthode HTTP GET (lecture) |
| `@RequestParam(required = false) ReservationStatus statut` | Paramètre optionnel dans l'URL ?statut=EN_ATTENTE |
| `@RequestParam(required = false) Integer adherentId` | Paramètre optionnel ?adherentId=101 |
| `ResponseEntity.ok(reservations)` | Retourne le code HTTP **200** avec la liste |

---

### Endpoint PATCH (Annuler)

```java
@PatchMapping("/{id}/annuler")
public ResponseEntity<ReservationResponse> annulerReservation(@PathVariable Integer id) {
    ReservationResponse response = reservationService.annulerReservation(id);
    return ResponseEntity.ok(response);
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `@PatchMapping("/{id}/annuler")` | Méthode HTTP PATCH (mise à jour partielle) |
| `@PathVariable Integer id` | Récupère l'ID dans l'URL (/api/reservations/**1**/annuler) |

---

### Endpoint DELETE (Supprimer)

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> supprimerReservation(@PathVariable Integer id) {
    reservationService.supprimerReservation(id);
    return ResponseEntity.noContent().build();
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `@DeleteMapping("/{id}")` | Méthode HTTP DELETE (suppression) |
| `ResponseEntity.noContent().build()` | Retourne le code HTTP **204** (pas de contenu) |

---

## 15 — Correction : WebSecurityConfiguration

**Fichier :** `configuration/WebSecurityConfiguration.java`

### Correction du bug 401

**Avant (ne fonctionnait pas) :**
```java
.requestMatchers("/authenticate", "/borrow/**", "/admin/books/", "/api/reservations/**").permitAll()
```

**Après (corrigé) :**
```java
.requestMatchers("/authenticate", "/borrow/**", "/admin/books/", "/api/reservations", "/api/reservations/**").permitAll()
```

**Explication du bug :**

| Pattern | `/api/reservations` | `/api/reservations/1` | `/api/reservations/1/annuler` |
|---------|--------------------|-----------------------|------------------------------|
| `/api/reservations/**` (avant) | ❌ **Ne matche PAS** | ✅ Matche | ✅ Matche |
| `/api/reservations` + `/api/reservations/**` (après) | ✅ Matche | ✅ Matche | ✅ Matche |

**Pourquoi ?** Le pattern `/**` en fin de chaîne ne correspond PAS au chemin racine sans `/` final. Il faut ajouter le pattern exact `/api/reservations` séparément.

---

## 16 — Tests unitaires : ReservationServiceTest

**Fichier :** `src/test/java/.../service/ReservationServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BooksRepository booksRepository;

    @Mock
    private UsersRepository usersRepository;

    @InjectMocks
    private ReservationService reservationService;
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `@ExtendWith(MockitoExtension.class)` | Active Mockito pour les tests JUnit 5 |
| `@Mock` | Crée un objet factice (mock) au lieu du vrai repository |
| `@InjectMocks` | Crée une instance de `ReservationService` avec les mocks injectés |

### Exemple de test RG-01

```java
@Test
void rg01_creerReservation_livreDisponible_doitEchouer() {
    // 1. Préparer les données factices
    when(booksRepository.findById(2)).thenReturn(Optional.of(livreDisponible));
    when(usersRepository.findById(10)).thenReturn(Optional.of(adherent));

    // 2. Créer la requête
    ReservationRequest request = new ReservationRequest();
    request.setLivreId(2);  // livre disponible (noOfCopies = 3)
    request.setAdherentId(10);

    // 3. Vérifier que l'exception est levée
    ConflictException ex = assertThrows(ConflictException.class,
            () -> reservationService.creerReservation(request));

    // 4. Vérifier le contenu du message
    assertTrue(ex.getMessage().contains("RG-01"));
    assertTrue(ex.getMessage().contains("disponible"));

    // 5. Vérifier qu'aucune sauvegarde n'a eu lieu
    verify(reservationRepository, never()).save(any());
}
```

**Ligne par ligne :**

| Ligne | Explication |
|-------|-------------|
| `when(booksRepository.findById(2)).thenReturn(...)` | Quand on cherche le livre 2, retourne le mock |
| `when(usersRepository.findById(10)).thenReturn(...)` | Quand on cherche l'utilisateur 10, retourne le mock |
| `assertThrows(ConflictException.class, ...)` | Vérifie que la méthode lance bien une `ConflictException` |
| `assertTrue(ex.getMessage().contains("RG-01"))` | Vérifie que le message contient "RG-01" |
| `verify(reservationRepository, never()).save(any())` | Vérifie que `save()` n'a JAMAIS été appelé |

---

## 📊 Résumé des fichiers créés/modifiés

| Fichier | Action | Description |
|---------|--------|-------------|
| `pom.xml` | **Modifié** | Migration Spring Boot 2→3, Java 21, PostgreSQL, JJWT, SpringDoc |
| `application.properties` | **Modifié** | Migration MySQL → PostgreSQL |
| `WebSecurityConfiguration.java` | **Modifié** | Ajout pattern `/api/reservations` |
| `Books.java` | **Modifié** | javax → jakarta, Lombok → manuel |
| `Borrow.java` | **Modifié** | javax → jakarta, Lombok → manuel |
| `Users.java` | **Modifié** | javax → jakarta, Lombok → manuel |
| `Role.java` | **Modifié** | javax → jakarta, Lombok → manuel |
| `JwtUtil.java` | **Modifié** | JJWT 0.9.1 → 0.12.5 |
| `JwtAuthenticationEntryPoint.java` | **Modifié** | javax.servlet → jakarta.servlet |
| `JwtRequestFilter.java` | **Modifié** | javax.servlet → jakarta.servlet |
| `JwtRequest.java` | **Modifié** | Corrections setters |
| `JwtResponse.java` | **Modifié** | Réorganisation getters/setters |
| `SwaggerConfiguration.java` | **Créé** | Configuration SpringDoc OpenAPI |
| `Reservation.java` | **Créé** | Entité JPA pour les réservations |
| `ReservationRequest.java` | **Créé** | DTO de requête |
| `ReservationResponse.java` | **Créé** | DTO de réponse |
| `ReservationStatus.java` | **Créé** | Enum des statuts |
| `ReservationRepository.java` | **Créé** | Repository JPA |
| `ReservationService.java` | **Créé** | Logique métier (RG-01 à RG-06) |
| `ReservationController.java` | **Créé** | API REST (5 endpoints) |
| `ConflictException.java` | **Créé** | Exception HTTP 409 |
| `ReservationServiceTest.java` | **Créé** | Tests unitaires (15 tests) |

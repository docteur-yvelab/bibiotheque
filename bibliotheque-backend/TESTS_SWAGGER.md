# 🧪 Guide de test complet — Module Réservation via Swagger UI

> **URL Swagger UI** : `http://localhost:8080/swagger-ui/index.html`

---

## 📋 Sommaire

1. [Authentification](#1--authentification)
2. [Préparation des données](#2--préparation-des-données)
3. [RG-01 : Réserver un livre indisponible](#3--rg-01--réserver-un-livre-indisponible)
4. [RG-02 : Une seule réservation active par livre/adhérent](#4--rg-02--une-seule-réservation-active-par-livreadhérent)
5. [RG-03 : Max 3 réservations actives simultanées](#5--rg-03--max-3-réservations-actives-simultanées)
6. [RG-04 : dateExpiration = dateReservation + 7 jours](#6--rg-04--dateexpiration--datereservation--7-jours)
7. [RG-05 : Annulation si EN_ATTENTE ou DISPONIBLE](#7--rg-05--annulation-si-en_attente-ou-disponible)
8. [RG-06 : Statut figé (ANNULEE / EXPIREE / HONOREE)](#8--rg-06--statut-figé-annulée--expirée--honorée)
9. [Lecture et filtrage](#9--lecture-et-filtrage)
10. [Suppression](#10--suppression)

---

## 1 — Authentification

### POST `/authenticate`

> ⚠️ **Nécessaire pour accéder aux endpoints protégés par Spring Security**

**Request Body :**

```json
{
  "username": "admin",
  "password": "123456"
}
```

**Response (200 OK) :**

```json
{
  "user": {
    "userId": 1,
    "username": "admin",
    "name": "Administrateur",
    "password": "...",
    "role": [
      {
        "roleId": 1,
        "roleName": "Admin"
      }
    ]
  },
  "jwtToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

**➡️ Copiez le `jwtToken` → Cliquez sur "Authorize" en haut de Swagger → Entrez `Bearer <votre_token>`**

---

## 2 — Préparation des données

### Vérifier les livres existants

**GET `/admin/books`**

> Liste tous les livres. Notez les `bookId` et `noOfCopies`.

---

### Mettre un livre en indisponible (noOfCopies = 0)

**PUT `/admin/books/{id}`**

**Request Body (exemple avec bookId=1) :**

```json
{
  "bookId": 1,
  "bookName": "toto",
  "bookAuthor": "lui",
  "bookGenre": "drift",
  "noOfCopies": 0
}
```

**Response (200 OK) :** Le livre avec `noOfCopies: 0`

---

### OU Créer un nouveau livre indisponible

**POST `/admin/books`**

**Request Body :**

```json
{
  "bookName": "Livre Indisponible Test",
  "bookAuthor": "Auteur Test",
  "bookGenre": "Genre Test",
  "noOfCopies": 0
}
```

**Response (200 OK) :**

```json
{
  "bookId": 3,
  "bookName": "Livre Indisponible Test",
  "bookAuthor": "Auteur Test",
  "bookGenre": "Genre Test",
  "noOfCopies": 0
}
```

> **Notez le `bookId` retourné (ex: 3) pour les tests suivants.**

---

### Vérifier les utilisateurs

**GET `/admin/users`**

> Liste tous les utilisateurs. Notez un `userId` pour servir d'adherentId (ex: 101).

---

## 3 — RG-01 : Réserver un livre indisponible

> **Règle** : On ne peut réserver qu'un livre **indisponible** (`noOfCopies == 0`)

---

### ✅ Test 3a — Livre indisponible → SUCCÈS

**POST `/api/reservations`**

**Request Body :**

```json
{
  "livreId": 1,
  "adherentId": 101
}
```

> ⚠️ Assurez-vous que le livre 1 a `noOfCopies = 0` (voir étape 2)

**Response attendue (201 Created) :**

```json
{
  "reservationId": 1,
  "livreId": 1,
  "adherentId": 101,
  "dateReservation": "2026-08-21T10:30:00.000+00:00",
  "dateExpiration": "2026-08-28T10:30:00.000+00:00",
  "statut": "EN_ATTENTE"
}
```

---

### ❌ Test 3b — Livre disponible → ÉCHEC

**POST `/api/reservations`**

**Request Body :**

```json
{
  "livreId": 2,
  "adherentId": 101
}
```

> ⚠️ Le livre 2 a `noOfCopies = 18` (disponible)

**Response attendue (409 Conflict) :**

```json
{
  "timestamp": "...",
  "status": 409,
  "error": "Conflict",
  "message": "RG-01 : Le livre 'rose' est disponible, réservation impossible.",
  "path": "/api/reservations"
}
```

---

## 4 — RG-02 : Une seule réservation active par livre/adhérent

> **Règle** : Un adhérent ne peut avoir qu'**une seule réservation active** (EN_ATTENTE ou DISPONIBLE) pour un même livre.

---

### ❌ Test 4a — Deuxième réservation même livre → ÉCHEC

**POST `/api/reservations`**

**Request Body :**

```json
{
  "livreId": 1,
  "adherentId": 101
}
```

> ⚠️ Cette réservation existe déjà (créée au test 3a)

**Response attendue (409 Conflict) :**

```json
{
  "timestamp": "...",
  "status": 409,
  "error": "Conflict",
  "message": "RG-02 : L'adhérent a déjà une réservation active pour ce livre.",
  "path": "/api/reservations"
}
```

---

### ✅ Test 4b — Réservation pour un AUTRE livre → SUCCÈS

> Créez d'abord un 2ème livre indisponible (bookId=4, noOfCopies=0)

**POST `/api/reservations`**

**Request Body :**

```json
{
  "livreId": 4,
  "adherentId": 101
}
```

**Response attendue (201 Created) :**

```json
{
  "reservationId": 2,
  "livreId": 4,
  "adherentId": 101,
  "dateReservation": "2026-08-21T11:00:00.000+00:00",
  "dateExpiration": "2026-08-28T11:00:00.000+00:00",
  "statut": "EN_ATTENTE"
}
```

---

## 5 — RG-03 : Max 3 réservations actives simultanées

> **Règle** : Un adhérent ne peut pas dépasser **3 réservations actives** simultanées.

> ⚠️ Créez 4 livres indisponibles (noOfCopies=0) avec les bookId 5, 6, 7, 8

---

### ✅ Test 5a — 3 réservations → SUCCÈS

**Requête 1 :**

**POST /api/reservations **

```json
{
  "livreId": 5,
  "adherentId": 101
}
```

**Requête 2 :**

**POST /api/reservations **
```json
{
  "livreId": 6,
  "adherentId": 101
}
```

**Requête 3 :**

POST /api/reservations
```json
{
  "livreId": 7,
  "adherentId": 101
}
```

> Les 3 doivent retourner **201 Created**

---

### ❌ Test 5b — 4ème réservation → ÉCHEC

**POST `/api/reservations`**

**Request Body :**

```json
{
  "livreId": 8,
  "adherentId": 101
}
```

**Response attendue (409 Conflict) :**

```json
{
  "timestamp": "...",
  "status": 409,
  "error": "Conflict",
  "message": "RG-03 : L'adhérent ne peut pas dépasser 3 réservations actives simultanées.",
  "path": "/api/reservations"
}
```

---

## 6 — RG-04 : dateExpiration = dateReservation + 7 jours

> **Règle** : La date d'expiration est calculée automatiquement = `dateReservation + 7 jours`.

**Vérification** : Reprenez la réponse du test 3a et vérifiez :

```json
{
  "reservationId": 1,
  "livreId": 1,
  "adherentId": 101,
  "dateReservation": "2026-08-21T10:30:00.000+00:00",
  "dateExpiration": "2026-08-28T10:30:00.000+00:00",
  "statut": "EN_ATTENTE"
}
```

> ✅ `dateExpiration` = `dateReservation` + **7 jours exactement**

---

## 7 — RG-05 : Annulation si EN_ATTENTE ou DISPONIBLE

> **Règle** : Une réservation ne peut être annulée que si son statut est **EN_ATTENTE** ou **DISPONIBLE**.

---

### ✅ Test 7a — Annuler réservation EN_ATTENTE → SUCCÈS

**PATCH `/api/reservations/{id}/annuler`**

> Remplacez `{id}` par l'ID d'une réservation EN_ATTENTE (ex: 1)

**Request Body :** (aucun)

**Response attendue (200 OK) :**

```json
{
  "reservationId": 1,
  "livreId": 1,
  "adherentId": 101,
  "dateReservation": "2026-08-21T10:30:00.000+00:00",
  "dateExpiration": "2026-08-28T10:30:00.000+00:00",
  "statut": "ANNULEE"
}
```

---

### ✅ Test 7b — Annuler réservation DISPONIBLE → SUCCÈS

> Créez une réservation, mettez-la en statut DISPONIBLE (manuellement ou via un endpoint si disponible), puis :

**PATCH `/api/reservations/{id}/annuler`**

**Response attendue (200 OK) :**

```json
{
  "reservationId": 2,
  "livreId": 4,
  "adherentId": 101,
  "dateReservation": "2026-08-21T11:00:00.000+00:00",
  "dateExpiration": "2026-08-28T11:00:00.000+00:00",
  "statut": "ANNULEE"
}
```

---

## 8 — RG-06 : Statut figé (ANNULEE / EXPIREE / HONOREE)

> **Règle** : Une réservation avec le statut **ANNULEE**, **EXPIREE** ou **HONOREE** ne peut plus changer d'état.

---

### ❌ Test 8a — Annuler une réservation déjà ANNULEE → ÉCHEC

**PATCH `/api/reservations/1/annuler`**

> La réservation 1 est déjà ANNULEE (test 7a)

**Response attendue (409 Conflict) :**

```json
{
  "timestamp": "...",
  "status": 409,
  "error": "Conflict",
  "message": "RG-05/RG-06 : Une réservation avec le statut 'ANNULEE' ne peut pas être annulée.",
  "path": "/api/reservations/1/annuler"
}
```

---

### ❌ Test 8b — Annuler une réservation EXPIREE → ÉCHEC

> Créez une réservation, puis mettez-la manuellement en statut EXPIREE en base :

```sql
UPDATE "Reservation" SET statut = 'EXPIREE' WHERE "reservation_id" = 2;
```

**PATCH `/api/reservations/2/annuler`**

**Response attendue (409 Conflict) :**

```json
{
  "timestamp": "...",
  "status": 409,
  "error": "Conflict",
  "message": "RG-05/RG-06 : Une réservation avec le statut 'EXPIREE' ne peut pas être annulée.",
  "path": "/api/reservations/2/annuler"
}
```

---

### ❌ Test 8c — Annuler une réservation HONOREE → ÉCHEC

> Créez une réservation, puis mettez-la manuellement en statut HONOREE en base :

```sql
UPDATE "Reservation" SET statut = 'HONOREE' WHERE "reservation_id" = 3;
```

**PATCH `/api/reservations/3/annuler`**

**Response attendue (409 Conflict) :**

```json
{
  "timestamp": "...",
  "status": 409,
  "error": "Conflict",
  "message": "RG-05/RG-06 : Une réservation avec le statut 'HONOREE' ne peut pas être annulée.",
  "path": "/api/reservations/3/annuler"
}
```

---

## 9 — Lecture et filtrage

### GET `/api/reservations` — Toutes les réservations

**Response (200 OK) :**

```json
[
  {
    "reservationId": 1,
    "livreId": 1,
    "adherentId": 101,
    "dateReservation": "2026-08-21T10:30:00.000+00:00",
    "dateExpiration": "2026-08-28T10:30:00.000+00:00",
    "statut": "ANNULEE"
  },
  {
    "reservationId": 2,
    "livreId": 4,
    "adherentId": 101,
    "dateReservation": "2026-08-21T11:00:00.000+00:00",
    "dateExpiration": "2026-08-28T11:00:00.000+00:00",
    "statut": "EXPIREE"
  }
]
```

---

### GET `/api/reservations?statut=EN_ATTENTE` — Filtrer par statut

**Response (200 OK) :** Liste des réservations avec le statut `EN_ATTENTE`

---

### GET `/api/reservations?adherentId=101` — Filtrer par adhérent

**Response (200 OK) :** Liste des réservations de l'adhérent 101

---

### GET `/api/reservations?statut=EN_ATTENTE&adherentId=101` — Filtrer par statut ET adhérent

**Response (200 OK) :** Liste combinée

---

### GET `/api/reservations/{id}` — Consulter une réservation

**Response (200 OK) :**

```json
{
  "reservationId": 1,
  "livreId": 1,
  "adherentId": 101,
  "dateReservation": "2026-08-21T10:30:00.000+00:00",
  "dateExpiration": "2026-08-28T10:30:00.000+00:00",
  "statut": "ANNULEE"
}
```

---

### GET `/api/reservations/{id}` — Réservation inexistante

**Response (404 Not Found) :**

```json
{
  "timestamp": "...",
  "status": 404,
  "error": "Not Found",
  "message": "Réservation avec l'id 999 non trouvée.",
  "path": "/api/reservations/999"
}
```

---

## 10 — Suppression

### DELETE `/api/reservations/{id}` — Supprimer une réservation

**Response (204 No Content) :** (aucun body)

---

### DELETE `/api/reservations/{id}` — Réservation inexistante

**Response (404 Not Found) :**

```json
{
  "timestamp": "...",
  "status": 404,
  "error": "Not Found",
  "message": "Réservation avec l'id 999 non trouvée.",
  "path": "/api/reservations/999"
}
```

---

## 📊 Récapitulatif des endpoints

| Méthode | Endpoint | Description | Codes HTTP |
|---------|----------|-------------|------------|
| `POST` | `/authenticate` | Connexion JWT | 200, 401 |
| `POST` | `/admin/books` | Créer un livre (Admin) | 200 |
| `PUT` | `/admin/books/{id}` | Modifier un livre (Admin) | 200, 404 |
| `GET` | `/admin/books` | Lister les livres | 200 |
| `GET` | `/admin/users` | Lister les utilisateurs | 200 |
| `POST` | `/api/reservations` | Créer une réservation | 201, 400, 404, 409 |
| `GET` | `/api/reservations` | Lister (filtres optionnels) | 200 |
| `GET` | `/api/reservations/{id}` | Consulter | 200, 404 |
| `PATCH` | `/api/reservations/{id}/annuler` | Annuler | 200, 404, 409 |
| `DELETE` | `/api/reservations/{id}` | Supprimer | 204, 404 |

---

## 🎯 Ordre de test recommandé

| Étape | Action | Résultat attendu |
|-------|--------|-----------------|
| 1 | Authentifier admin | Token JWT |
| 2 | Modifier livre 1 → noOfCopies=0 | Livre indisponible |
| 3 | Créer réservation (RG-01 ✅) | 201 Created |
| 4 | Créer réservation même livre (RG-02 ❌) | 409 Conflict |
| 5 | Créer réservation livre disponible (RG-01 ❌) | 409 Conflict |
| 6 | Créer 3 réservations livreId 5,6,7 (RG-03 ✅) | 3 × 201 Created |
| 7 | Créer 4ème réservation livreId 8 (RG-03 ❌) | 409 Conflict |
| 8 | Vérifier dates réponse (RG-04) | dateExpiration = +7 jours |
| 9 | Annuler réservation EN_ATTENTE (RG-05 ✅) | 200 → ANNULEE |
| 10 | Annuler réservation ANNULEE (RG-06 ❌) | 409 Conflict |
| 11 | Lister toutes les réservations | 200 OK |
| 12 | Filtrer par statut=EN_ATTENTE | 200 OK |
| 13 | Filtrer par adherentId=101 | 200 OK |
| 14 | Consulter réservation par ID | 200 OK |
| 15 | Supprimer réservation | 204 No Content |

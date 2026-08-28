# Rapport d'implémentation — Module Réservation (Frontend Angular)

**Branche :** `feature/reservation-ui-thiakou-stive`
**Date :** 28 août 2026
**Exercice :** Séance 3 — KFOKAM48 Batch 2 Phase 3

---

## 1. Résumé

Implémentation d'un écran de gestion des réservations dans l'application Angular existante (Library Management System). L'écran comprend une liste filtrable, un formulaire de création, et une action d'annulation, avec une gestion complète des 4 états (chargement, données, liste vide, erreur) et des erreurs métier. L'interface est entièrement responsive (desktop + mobile).

---

## 2. Fichiers créés (11 fichiers)

| Fichier | Description |
|---|---|
| `src/app/_model/reservation.ts` | Modèle `Reservation` (reservationId, livreId, adherentId, book, user, status, reservationDate, expirationDate) |
| `src/app/_service/reservation.service.ts` | Service API dédié — `getAll()`, `create()`, `annuler()` — aucun appel API dans les composants |
| `src/app/reservation-container/reservation-container.component.ts` | Composant conteneur — gère l'état global (loading, data, empty, error) et orchestre les appels API |
| `src/app/reservation-container/reservation-container.component.html` | Template du conteneur — 4 états avec messages clairs, icônes Bootstrap, layout responsive |
| `src/app/reservation-container/reservation-container.component.css` | Styles du conteneur (vide) |
| `src/app/reservation-list/reservation-list.component.ts` | Composant liste — filtre par statut, bouton annuler conditionné, confirmation avant annulation |
| `src/app/reservation-list/reservation-list.component.html` | Template de la liste — tableau responsive (desktop) + cards (mobile) |
| `src/app/reservation-list/reservation-list.component.css` | Styles de la liste (vide) |
| `src/app/reservation-form/reservation-form.component.ts` | Composant formulaire — dropdowns alimentés par l'API, validation côté client |
| `src/app/reservation-form/reservation-form.component.html` | Template du formulaire — 2 selects responsive (col-12 col-md-6) + bouton désactivé |
| `src/app/reservation-form/reservation-form.component.css` | Styles du formulaire (vide) |

---

## 3. Fichiers modifiés (5 fichiers)

| Fichier | Modification |
|---|---|
| `src/app/_auth/auth.interceptor.ts` | Retiré le `catchError` qui masquait les erreurs (`"Some thing is wrong"`) → les erreurs remontent maintenant intactes aux services. Utilisation de `throwError(() => err)` au lieu de `throwError(err)`. |
| `src/app/app-routing.module.ts` | Ajouté la route `{path: 'reservations', component: ReservationContainerComponent, canActivate: [AuthGuard], data: {roles: ['Admin']}}` |
| `src/app/header/header.component.html` | Ajouté le lien de navigation "Reservations" visible uniquement pour le rôle Admin |
| `src/app/app.module.ts` | Déclaré les 3 nouveaux composants (`ReservationContainerComponent`, `ReservationListComponent`, `ReservationFormComponent`) |
| `src/styles.css` | Ajouté des styles responsive (table overflow, card text, alert icons, mobile spacing) |

---

## 4. Corrections appliquées

### 4.1 Payload de création corrigé

**Problème :** Le backend attend des champs plats `livreId` et `adherentId`, mais le formulaire envoyait des objets imbriqués `book: { bookId }` et `user: { userId }`.

**Erreur affichée :** `Le champ 'livreId' est obligatoire.`

**Correction :** Le modèle `Reservation` a été mis à jour avec les champs `livreId` et `adherentId`, et le formulaire envoie maintenant :
```json
{
  "livreId": 1,
  "adherentId": 2
}
```

### 4.2 Interface responsive

**Desktop (≥768px) :** Tableau avec colonnes + scroll horizontal si besoin
**Mobile (<768px) :** Cartes empilées avec les informations de chaque réservation

| Composant | Adaptation responsive |
|---|---|
| `reservation-list` | `d-none d-md-block` pour le tableau, `d-md-none` pour les cartes |
| `reservation-form` | Grille `col-12 col-md-6` pour les dropdowns côte à côte sur desktop |
| `reservation-container` | `container-fluid` + padding adaptatif `px-3 px-md-4` |
| `styles.css` | Media queries pour mobile (padding, font-size, spacing) |

---

## 5. Architecture respectée

### Exigence : « Un service dédié aux appels API. Aucun HttpClient appelé directement depuis un composant. »

✅ **Conforme.** Tous les appels API sont isolés dans `ReservationService`. Les composants n'utilisent que des méthodes du service.

### Exigence : « Découpage en composants — minimum un conteneur, un liste, un formulaire. »

✅ **Conforme.** Trois composants distincts :
- `ReservationContainerComponent` → orchestrateur d'état
- `ReservationListComponent` → affichage tableau + filtre
- `ReservationFormComponent` → formulaire de création

### Exigence : « Aucune donnée codée en dur. Tout vient de l'API. »

✅ **Conforme.** Les dropdowns livres/adhérents sont alimentés par `BooksService` et `UsersService` existants. Les statuts du filtre correspondent aux valeurs renvoyées par l'API.

### Exigence : « Interface cohérente avec le reste du projet. »

✅ **Conforme.** Interface en anglais, cohérente avec le reste du projet existant.

---

## 6. Gestion des 4 états (exigence : 6 points)

| État | Implémentation | Fichier |
|---|---|---|
| **Chargement** | Spinner Bootstrap + message "Loading reservations, please wait..." | `reservation-container.component.html` |
| **Données** | Tableau rempli avec filtre + formulaire de création | `reservation-list.component.html`, `reservation-form.component.html` |
| **Liste vide** | Message explicite "No reservations found" avec icône info | `reservation-container.component.html` |
| **Erreur** | Message détaillé avec icône triangle + bouton "Retry" | `reservation-container.component.html` |

---

## 7. Messages d'erreur personnalisés (exigence : 8 points)

### Erreurs de chargement (GET /api/reservations)

| Code HTTP | Message affiché |
|---|---|
| 0 (réseau/timeout) | "Cannot reach the server. The backend may be stopped or unreachable. Please verify that the server is running and try again." |
| 401 | Redirection vers /login |
| 403 | Redirection vers /forbidden |
| 500 | "An internal server error occurred. Please try again later or contact support." |
| Autre | "An unexpected error occurred while loading reservations (HTTP X). Please try again." |

### Erreurs de création (POST /api/reservations)

| Code HTTP | Message affiché |
|---|---|
| 409 | Message du backend OU "Cannot create reservation. This book may already be reserved, the quota of 3 active reservations may have been reached, or the book is currently available for direct borrowing." |
| 400 | Message du backend OU "Missing or invalid fields. Please check that you have selected both a book and a member." |
| 404 | Message du backend OU "The selected book or member was not found on the server. Please refresh the page and try again." |
| 0 | "Cannot reach the server. The backend may be stopped. Please verify the server is running and try again." |
| Autre | "An unexpected error occurred (HTTP X). Please try again." |

### Erreurs d'annulation (PATCH /api/reservations/{id}/annuler)

| Code HTTP | Message affiché |
|---|---|
| 409 | Message du backend OU "Cannot cancel this reservation. It may have already been processed, expired, or is no longer in a cancellable state (EN_ATTENTE or DISPONIBLE)." |
| 404 | "This reservation was not found on the server. It may have already been deleted." |
| 0 | "Cannot reach the server. The backend may be stopped. Please verify the server is running and try again." |
| Autre | "An unexpected error occurred while cancelling the reservation (HTTP X). Please try again." |

---

## 8. Fonctionnalités implémentées

### 8.1 Liste des réservations
- Colonnes : Book Title, Member, Status, Reservation Date, Expiration Date, Action
- Filtre par statut : ALL, EN_ATTENTE, DISPONIBLE, ANNULEE, EXPIREE, HONOREE
- Données imbriquées : `r.book?.bookName`, `r.user?.name`
- **Responsive** : tableau sur desktop, cartes sur mobile

### 8.2 Formulaire de création
- Deux dropdowns : livres (via `BooksService.getBooksList()`) et adhérents (via `UsersService.getUsersList()`)
- Bouton désactivé tant que les deux champs ne sont pas remplis
- Payload : `livreId` + `adherentId` (champs plats conformes au backend)
- Messages d'erreur détaillés affichés dans une alerte stylisée avec icône
- Après succès : la liste se rafraîchit automatiquement + message de confirmation
- **Responsive** : dropdowns côte à côte sur desktop, empilés sur mobile

### 8.3 Annulation
- Bouton "Cancel" visible uniquement pour les statuts EN_ATTENTE et DISPONIBLE
- Confirmation demandée avant l'appel (`confirm()` avec message "This action cannot be undone")
- En cas de succès : la liste se rafraîchit + message de confirmation
- En cas d'erreur : message détaillé affiché pendant 6 secondes

---

## 9. Barème — Auto-évaluation

| Élément | Points | Status |
|---|---|---|
| Liste fonctionnelle avec ses colonnes et le filtre par statut | /8 | ✅ |
| Les quatre états correctement gérés | /6 | ✅ |
| Formulaire fonctionnel, listes déroulantes alimentées par l'API | /6 | ✅ |
| Affichage lisible des erreurs métier (409, 400, 404) | /8 | ✅ |
| Annulation avec confirmation et mise à jour de la liste | /5 | ✅ |
| Architecture : service isolé, composants découpés | /5 | ✅ |
| **Total** | **/38** | ✅ |

---

## 10. Comment tester

1. **Chargement** : rafraîchir la page → spinner visible
2. **Liste remplie** : s'assurer que le backend contient des réservations
3. **Liste vide** : supprimer toutes les réservations dans la base
4. **Erreur réseau** : arrêter le backend → message "Cannot reach the server..."
5. **Création succès** : sélectionner un livre + adhérent → cliquer "Create Reservation"
6. **Création refus (409)** : réserver un livre déjà réservé → message détaillé
7. **Annulation** : cliquer "Cancel" sur une ligne EN_ATTENTE → confirmation → succès
8. **Annulation refus (409)** : tenter d'annuler une réservation HONOREE → message détaillé
9. **Responsive desktop** : redimensionner à >768px → tableau avec colonnes
10. **Responsive mobile** : redimensionner à <768px → cartes empilées

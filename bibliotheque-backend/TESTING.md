# TESTING.md — Comptes de test et vérification (Séance 4)

## Comptes de test (créés automatiquement au démarrage)

Créés par `TestDataInitializer` (idempotent : rien n'est dupliqué au redémarrage).

| Rôle | Username | Mot de passe | Notes |
|---|---|---|---|
| ADHERENT | `adherent1` | `adherent1` | Possède 1 réservation EN_ATTENTE sur « Clean Code » |
| ADHERENT | `adherent2` | `adherent2` | Aucune réservation |
| BIBLIOTHECAIRE | `biblio1` | `biblio1` | Voit tout, peut réserver pour n'importe qui, peut supprimer |

Rôles en base : `ADHERENT`, `BIBLIOTHECAIRE`, `Admin` (casse cohérente avec l'existant).

Livres seedés :
- **Clean Code** — `noOfCopies = 0` → réservable (RG-01 OK)
- **Effective Java** — `noOfCopies = 3` → NON réservable (RG-01 : 409)

## Démarrage

```bash
# Base PostgreSQL requise : jdbc:postgresql://localhost:5432/bibliotheque (postgres/123456)
mvn spring-boot:run
```

## Obtenir un token

```bash
curl -s -X POST http://localhost:8080/authenticate \
  -H "Content-Type: application/json" \
  -d '{"username":"adherent1","password":"adherent1"}'
# -> {"user":{...},"jwtToken":"eyJ..."}
```

## Scénarios de vérification manuelle (curl)

`TOKEN=<valeur de jwtToken>`

| # | Scénario | Commande | Attendu |
|---|---|---|---|
| 1 | Sans token (RS-01) | `curl -i http://localhost:8080/api/reservations` | **401** |
| 2 | ADHERENT liste ses réservations (RS-05) | `curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/reservations` | **200** (les siennes uniquement) |
| 3 | ADHERENT tente de falsifier adherentId (RS-04) | `curl -i -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"livreId":1,"adherentId":999}' http://localhost:8080/api/reservations` | **201**, réservation créée pour `adherent1` (adherentId du body ignoré) |
| 4 | ADHERENT consulte la réservation d'un autre (RS-03) | `curl -i -H "Authorization: Bearer $TOKEN_ADHERENT2" http://localhost:8080/api/reservations/1` | **403** |
| 5 | ADHERENT appelle DELETE (RS-02) | `curl -i -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/reservations/1` | **403** |
| 6 | BIBLIOTHECAIRE voit tout | `curl -i -H "Authorization: Bearer $TOKEN_BIBLIO" http://localhost:8080/api/reservations` | **200** (toutes) |
| 7 | BIBLIOTHECAIRE supprime (RS-02) | `curl -i -X DELETE -H "Authorization: Bearer $TOKEN_BIBLIO" http://localhost:8080/api/reservations/1` | **204** |
| 8 | Non-régression : borrow | `curl -i http://localhost:8080/borrow` | **200** (permitAll, inchangé) |
| 9 | Non-régression : authenticate | voir plus haut | **200** |

## Tests automatisés

```bash
mvn test
```

- `ReservationServiceTest` — RG-03 (2 → OK, 3 → `ConflictException`), repositories mockés, **aucune base requise**.
- `ReservationControllerIntegrationTest` — 401 / 200 / 403 sur `GET /api/reservations`, profil `test` (H2 en mémoire), vrai JWT via `JwtService`.

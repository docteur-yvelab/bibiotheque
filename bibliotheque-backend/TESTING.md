# Comptes et procédures de test — Séance 4

## Comptes de test (créés automatiquement au démarrage, idempotent)

Le `TestDataInitializer` crée ces comptes au premier démarrage.
Redémarrer l'application ne duplique rien et ne modifie pas les mots de passe.

| Rôle           | Identifiant | Mot de passe | Notes                            |
|----------------|-------------|--------------|----------------------------------|
| ADHERENT       | `adherent1` | `adherent1`  | Possède 2 réservations seedées   |
| ADHERENT       | `adherent2` | `adherent2`  | Possède 1 réservation seedée     |
| BIBLIOTHECAIRE | `biblio1`   | `biblio1`    | Accès complet, DELETE autorisé   |
| Admin          | `admin`     | `admin`      | Compte historique du projet      |

Livres seedés : « Clean Code » (0 copie) et « Effective Java » (0 copie)
— tous deux réservables (RG-01 ok).

## Obtenir un token

```bash
curl -s -X POST http://localhost:8080/authenticate \
  -H "Content-Type: application/json" \
  -d '{"username":"adherent1","password":"adherent1"}'
# => {"user":{...},"jwtToken":"eyJ..."}
```

## Vérifications manuelles des règles RS

```bash
BASE=http://localhost:8080
TOKEN=$(curl -s -X POST $BASE/authenticate -H "Content-Type: application/json" \
  -d '{"username":"adherent1","password":"adherent1"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['jwtToken'])")
TOKEN_BIBLIO=$(curl -s -X POST $BASE/authenticate -H "Content-Type: application/json" \
  -d '{"username":"biblio1","password":"biblio1"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['jwtToken'])")

# RS-01 — sans token => 401
curl -i $BASE/api/reservations | head -1

# RS-05 — adherent1 ne voit que les siennes => 200
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/reservations | python3 -m json.tool

# RS-03 — adherent1 lit une réservation d'adherent2 => 403
RES_ADH2=$(curl -s -H "Authorization: Bearer $TOKEN_BIBLIO" "$BASE/api/reservations?adherentId=6" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['reservationId'])")
curl -i -H "Authorization: Bearer $TOKEN" $BASE/api/reservations/$RES_ADH2 | head -1

# RS-02 — DELETE par un ADHERENT => 403 (jamais 401)
curl -i -X DELETE -H "Authorization: Bearer $TOKEN" $BASE/api/reservations/$RES_ADH2 | head -1

# RS-02 — DELETE par le BIBLIOTHECAIRE => 204
curl -i -X DELETE -H "Authorization: Bearer $TOKEN_BIBLIO" $BASE/api/reservations/$RES_ADH2 | head -1

# RS-04 — adherent1 falsifie adherentId => créé pour lui-même (201)
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"livreId":1,"adherentId":999}' $BASE/api/reservations | python3 -m json.tool
```

## Tests automatisés

```bash
mvn test
# 54 tests : 21 unitaires (service, repos mockés), 16 intégration
# contrôleur (JWT réels sur H2), 16 intégration sécurité, 1 contexte.
```

Base des tests d'intégration : H2 en mémoire (`application-test.properties`),
aucune PostgreSQL requise.

#!/bin/bash
BASE_URL="http://localhost:8080"

echo "============================================"
echo "  RAPPORT D'INITIALISATION DES DONNÉES"
echo "============================================"
echo ""

# === Étape 1 : Vérification de l'application ===
echo "[1/6] Vérification de l'application..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/authenticate" -X POST -H "Content-Type: application/json" -d '{}' 2>/dev/null)
if [ "$HTTP_CODE" = "000" ]; then
  echo "  ❌ L'application ne tourne pas sur le port 8080"
  exit 1
fi
echo "  ✅ Application accessible (HTTP $HTTP_CODE)"

# === Étape 2 : Authentification admin ===
echo ""
echo "[2/6] Authentification admin..."
ADMIN_RESP=$(curl -s -X POST "$BASE_URL/authenticate" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}')
ADMIN_TOKEN=$(echo "$ADMIN_RESP" | grep -o '"jwtToken":"[^"]*' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "  ⚠️  Échec authentification admin (admin/admin)"
  echo "  Réponse: $ADMIN_RESP"
  echo "  Tentative avec admin/admin123..."
  ADMIN_RESP=$(curl -s -X POST "$BASE_URL/authenticate" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')
  ADMIN_TOKEN=$(echo "$ADMIN_RESP" | grep -o '"jwtToken":"[^"]*' | cut -d'"' -f4)
fi

if [ -z "$ADMIN_TOKEN" ]; then
  echo "  ❌ Impossible de s'authentifier. Vérifiez les identifiants admin."
  exit 1
fi
echo "  ✅ Token admin obtenu"

# === Étape 3 : Création des livres (L1 à L5) ===
echo ""
echo "[3/6] Création des livres (L1 à L5)..."
BOOK_RESULTS=""
for i in 1 2 3 4 5; do
  RESP=$(curl -s -X POST "$BASE_URL/admin/books" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"bookName\":\"L$i\",\"bookAuthor\":\"Auteur L$i\",\"bookGenre\":\"Roman\",\"noOfCopies\":1}")
  BOOK_ID=$(echo "$RESP" | grep -o '"bookId":[0-9]*' | cut -d':' -f2)
  if [ -n "$BOOK_ID" ]; then
    echo "  ✅ L$i créée (ID: $BOOK_ID)"
    BOOK_RESULTS="$BOOK_RESULTS $BOOK_ID"
  else
    echo "  ❌ L$i échoué: $(echo "$RESP" | head -c 80)"
  fi
done

# === Étape 4 : Création des adhérents (A1, A2, A3) ===
echo ""
echo "[4/6] Création des adhérents (A1, A2, A3)..."
USER_RESULTS=""
for i in 1 2 3; do
  RESP=$(curl -s -X POST "$BASE_URL/admin/users" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"A$i\",\"password\":\"123456\",\"email\":\"a$i@pkf.com\",\"name\":\"Adhérent $i\",\"role\":\"ROLE_USER\"}")
  USER_ID=$(echo "$RESP" | grep -o '"userId":[0-9]*' | cut -d':' -f2)
  if [ -n "$USER_ID" ]; then
    echo "  ✅ A$i créée (ID: $USER_ID)"
    USER_RESULTS="$USER_RESULTS $USER_ID"
  else
    echo "  ❌ A$i échoué: $(echo "$RESP" | head -c 80)"
  fi
done

# === Étape 5 : Authentification de A3 ===
echo ""
echo "[5/6] Authentification de A3..."
TOKEN_A3=$(curl -s -X POST "$BASE_URL/authenticate" \
  -H "Content-Type: application/json" \
  -d '{"username":"A3","password":"123456"}' \
  | grep -o '"jwtToken":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN_A3" ]; then
  echo "  ❌ Impossible d'authentifier A3"
  exit 1
fi

# Récupérer l'ID de A3
A3_USER_ID=$(curl -s -X POST "$BASE_URL/authenticate" \
  -H "Content-Type: application/json" \
  -d '{"username":"A3","password":"123456"}' \
  | grep -o '"userId":[0-9]*' | head -1 | cut -d':' -f2)
echo "  ✅ A3 authentifiée (ID: $A3_USER_ID)"

# === Étape 6 : Emprunts de L2 à L5 par A3 ===
echo ""
echo "[6/6] Emprunts de L2 à L5 par A3..."
BORROW_COUNT=0
for book_id in 2 3 4 5; do
  RESP=$(curl -s -X POST "$BASE_URL/borrow" \
    -H "Authorization: Bearer $TOKEN_A3" \
    -H "Content-Type: application/json" \
    -d "{\"userId\":$A3_USER_ID,\"bookId\":$book_id}")
  if echo "$RESP" | grep -q "has borrowed"; then
    echo "  ✅ L$book_id empruntée par A3"
    BORROW_COUNT=$((BORROW_COUNT + 1))
  else
    echo "  ❌ L$book_id échoué: $(echo "$RESP" | head -c 80)"
  fi
done

# === RÉSUMÉ ===
echo ""
echo "============================================"
echo "  RAPPORT RÉCAPITULATIF"
echo "============================================"
echo ""
echo "📚 Livres créés     : 5 (L1 à L5, 1 exemplaire chacun)"
echo "👤 Adhérents créés  : 3 (A1, A2, A3)"
echo "📖 Emprunts effectués: $BORROW_COUNT/4 (L2-L5 par A3)"
echo ""
echo "État attendu :"
echo "  • L1 : 1 exemplaire DISPONIBLE"
echo "  • L2 : 0 exemplaire (emprunté par A3)"
echo "  • L3 : 0 exemplaire (emprunté par A3)"
echo "  • L4 : 0 exemplaire (emprunté par A3)"
echo "  • L5 : 0 exemplaire (emprunté par A3)"
echo ""
echo "📋 Commandes utiles pour vérifier :"
echo "  curl -s http://localhost:8080/admin/books"
echo "  curl -s -H 'Authorization: Bearer \$TOKEN_A3' http://localhost:8080/borrow/user/$A3_USER_ID"
echo ""
echo "✅ Initialisation terminée avec succès !"

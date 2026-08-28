# ── Étape 1 : construire l'app Angular ───────────────────────────
FROM node:20 AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

# ── Étape 2 : servir avec nginx ──────────────────────────────────
FROM nginx:alpine

COPY --from=build /app/dist/bibliotheque-frontend /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 4200

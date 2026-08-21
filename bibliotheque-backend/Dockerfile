# ── Étape 1 : construire le JAR ──────────────────────────────────
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copier le wrapper Maven et le pom.xml d'abord (cache des dépendances)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copier le source et construire le fat JAR
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ── Étape 2 : image d'exécution ─────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/target/bibliotheque-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

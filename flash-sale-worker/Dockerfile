# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Cache dependencies first (layer caching)
COPY pom.xml .
COPY flash-sale-common/pom.xml flash-sale-common/pom.xml
COPY flash-sale-api/pom.xml flash-sale-api/pom.xml
COPY flash-sale-worker/pom.xml flash-sale-worker/pom.xml
RUN mvn dependency:go-offline -B -pl flash-sale-common,flash-sale-worker -am

# Build common + worker modules
COPY flash-sale-common/src flash-sale-common/src
COPY flash-sale-worker/src flash-sale-worker/src
RUN mvn package -DskipTests -B -pl flash-sale-common,flash-sale-worker -am && \
    mv flash-sale-worker/target/*.jar app.jar

# --- Runtime stage ---
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/app.jar app.jar

# Lightweight JVM settings for background worker
ENV JAVA_OPTS="-XX:+UseZGC \
  -Xms256m \
  -Xmx512m \
  -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

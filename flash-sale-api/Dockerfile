# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Cache dependencies first (layer caching)
COPY pom.xml .
COPY flash-sale-common/pom.xml flash-sale-common/pom.xml
COPY flash-sale-api/pom.xml flash-sale-api/pom.xml
COPY flash-sale-worker/pom.xml flash-sale-worker/pom.xml
RUN mvn dependency:go-offline -B -pl flash-sale-common,flash-sale-api -am

# Build common + api modules
COPY flash-sale-common/src flash-sale-common/src
COPY flash-sale-api/src flash-sale-api/src
RUN mvn package -DskipTests -B -pl flash-sale-common,flash-sale-api -am && \
    mv flash-sale-api/target/*.jar app.jar

# --- Runtime stage ---
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/app.jar app.jar

# JVM tuning for high-throughput flash sale workload
ENV JAVA_OPTS="-XX:+UseZGC \
  -XX:+ZGenerational \
  -Xms512m \
  -Xmx1g \
  -XX:+AlwaysPreTouch \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

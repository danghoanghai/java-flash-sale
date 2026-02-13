# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Cache dependencies first (layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build the application
COPY src src
RUN mvn package -DskipTests -B && \
    mv target/*.jar app.jar

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
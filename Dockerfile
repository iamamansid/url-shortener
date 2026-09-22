# ---- Build stage ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies first for faster rebuilds
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as non-root
RUN useradd -m appuser
USER appuser

COPY --from=build /app/target/url-shortener-*.jar app.jar

EXPOSE 8080

# Container-aware JVM: respect cgroup memory limits, fast startup
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

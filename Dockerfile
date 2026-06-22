# --- build stage: compile and test, produce the boot jar -------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first so they cache independently of source changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package

# --- runtime stage: JRE only, non-root ------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user — payment ingress should never run as root.
RUN useradd --system --uid 10001 appuser
USER appuser

COPY --from=build /app/target/iso20022-processor-*.jar app.jar

EXPOSE 8080
# TCP readiness check using bash (no wget/curl in the base image).
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD bash -c ':</dev/tcp/localhost/8080' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ── Stage 1: Build ────────────────────────────────────────────────────────────
# eclipse-temurin is the officially maintained OpenJDK Docker image (replaces
# the deprecated openjdk image). The full JDK is needed here for javac and Maven.
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy the Maven wrapper and pom.xml first to leverage Docker layer caching:
# dependency downloads are only re-executed when pom.xml changes, not on every
# source code change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Copy source and build the fat JAR.
# -DskipTests: tests run in CI with a real Redis; not inside the Docker build context.
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# JRE-only Alpine image: no compiler, no Maven, no source code.
# Result: ~100MB image vs ~600MB with a full JDK image.
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Create a non-root user to run the application.
# Running as root inside a container is a security risk — if the process is
# compromised, the attacker has root access to the container filesystem.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy only the compiled JAR from the build stage — nothing else travels to prod.
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

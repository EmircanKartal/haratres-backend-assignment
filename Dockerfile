# ── Build stage ──────────────────────────────────────────────────────────────
# Full JDK + Maven Wrapper: compiles and runs all tests inside the container.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build

# Cache Maven dependencies in a separate layer so they survive code-only changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && \
    ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

# Build and verify (runs all 124 tests)
COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress clean verify

# ── Run stage ────────────────────────────────────────────────────────────────
# Slim JRE-only image — no compiler, no build tools.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Turkish characters require a UTF-8 locale inside the container.
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

COPY --from=build /build/target/assessment.jar /app/assessment.jar

# Run as a non-root user (UID/GID 10001 need not exist in /etc/passwd).
USER 10001:10001

# Encoding flags are passed directly on the java command line so they don't
# print the "Picked up JAVA_TOOL_OPTIONS" noise to stderr.
# CMD supplies the default subcommand; users override: docker run --rm -it haratres-assessment shop
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar", "/app/assessment.jar"]
CMD ["--help"]

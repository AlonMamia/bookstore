# Production image for the Spring Boot backend.
#
# Expects a prebuilt Spring Boot fat jar at build/app.jar in the build context (see
# .github/workflows/pp.yml / prod.yml: CI runs `mvn clean verify` once and this image
# reuses that exact tested artifact instead of rebuilding it).
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

COPY build/app.jar app.jar

USER spring
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

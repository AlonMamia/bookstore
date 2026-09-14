# Bookstore

This project is a Spring Boot application with four runtime environments:

- local
- dev
- pp
- prod

## Environment configuration

The default local configuration lives in `src/main/resources/application.properties`.

Additional environment-specific settings are provided in:

- `src/main/resources/application-dev.properties`
- `src/main/resources/application-pp.properties`
- `src/main/resources/application-prod.properties`

The active profile is controlled by `spring.profiles.active`, with the default set to `local`:

```properties
spring.profiles.active=${APP_PROFILE:local}
```

You can override it with the `APP_PROFILE` environment variable or with Maven when starting the app.

## Run the app

### Local

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Dev

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### PP

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=pp
```

### Prod

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Database configuration

The app uses PostgreSQL and reads the connection details from environment variables with sensible defaults:

```bash
DB_URL=jdbc:postgresql://localhost:5432/bookstore_db
DB_USERNAME=myuser
DB_PASSWORD=secret
```

Example:

```bash
DB_URL=jdbc:postgresql://localhost:5432/bookstore_db DB_USERNAME=myuser DB_PASSWORD=secret ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## CORS configuration

CORS origins are configurable per environment using `app.cors.allowed-origins`.

Example override:

```bash
CORS_ALLOWED_ORIGINS="http://localhost:3000,http://localhost:4200" ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Default values:

- local: `http://localhost:8080`
- dev: `http://localhost:8081`
- pp: `http://localhost:8082`
- prod: `http://localhost:8083`

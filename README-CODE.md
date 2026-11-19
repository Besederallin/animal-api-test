# Code overview

Java 21, Spring Boot 4, Maven (multi-module, built with the Maven Wrapper).

## Modules

```
pom.xml                         parent POM: Spring Boot version, Java version, module list
animal-pictures-core/           all application code: REST API, service, HTTP client, JPA, DB schema, UI
animal-pictures-postgres/       runnable app = core + PostgreSQL driver      (docker-compose.yml)
animal-pictures-embedded/       runnable app = core + embedded H2 database   (docker-compose.embedded.yml)
```

The two "versions" differ only in their database driver and `application.yml`. Each has a tiny
`@SpringBootApplication` main class that picks up everything from `core`. A fix in `core` is a fix in
both versions.

## Request flow

```
POST /api/pictures/dog?count=3
  PictureController  ->  PictureService.fetchAndSave(DOG, 3)
                            |-- 3 x AnimalPictureClient.fetch(DOG)  -> GET https://place.dog/500/500
                            '-- PictureRepository.saveAll(...)       -> INSERT INTO picture ...
  <- 201 [ {id, animal, contentType, sizeBytes, sourceUrl, createdAt}, ... ]

GET /api/pictures/dog/latest
  PictureController  ->  PictureService.latest(DOG)  ->  newest row for DOG
  <- 200 image/jpeg  (the stored bytes)
```

## Classes (`animal-pictures-core`, package `com.example.animalpictures`)

**`picture`** – the domain and the REST API
- `AnimalType` – enum `DUCK`, `DOG`, `BEAR`. `fromValue("Dog")` parses the URL value case-insensitively
  and rejects unknown animals with a 400.
- `PictureController` – the two endpoints. `POST /{animal}` returns metadata as JSON; `GET /{animal}/latest`
  returns the raw image bytes with the stored content type plus `X-Picture-Id` / `X-Picture-Created-At` headers.
- `PictureService` – business logic. Checks `count` (1..`max-count`), downloads all pictures **first**
  and then saves them in one `saveAll` transaction, so a failed download stores nothing (no half-finished batches).
  `latest()` returns the newest picture or throws 404.
- `Picture` – JPA entity: animal, content type, size, source URL, timestamp and the image bytes.
- `PictureRepository` – Spring Data JPA. `findFirstByAnimalTypeOrderByCreatedAtDescIdDesc` gets the latest
  picture; the `id` breaks ties between pictures saved in the same batch.
- `PictureResponse` – JSON record returned by the POST endpoint (metadata only, no image bytes).

**`source`** – calling the public picture APIs
- `AnimalPictureClient` – interface "give me one picture of this animal". Tests replace it with a fake.
- `HttpAnimalPictureClient` – implementation with Spring's `RestClient`. Follows redirects, checks that the
  response really is a non-empty image under the size limit, and turns any failure into `PictureSourceException`.
- `FetchedPicture` – the downloaded bytes, content type and source URL.

**`config`**
- `AnimalPicturesProperties` – `animal-pictures.*` settings with defaults (source URLs, max count,
  max image size, timeouts).
- `AnimalPicturesConfiguration` – registers the properties, a `Clock` (so tests can fix the time) and the
  HTTP client timeouts.

**`web`** – error handling and request logging
- `RequestLoggingFilter` – gives each request an ID (from the `X-Request-Id` header or a new UUID), puts it in
  the logging MDC so every log line of that request carries it, returns it in the response, and logs one line
  per request with status and duration.
- `ApiExceptionHandler` – maps exceptions to RFC 9457 problem-detail JSON:
  `InvalidRequestException` → 400, `PictureNotFoundException` → 404, `PictureSourceException` → 502.

**Resources**
- `db/migration/V1__create_picture_table.sql` – the schema, applied by **Flyway** at startup. It is written
  to work on both PostgreSQL and H2 (H2 runs in PostgreSQL mode). Images are stored in a `BYTEA` column,
  and an index on `(animal_type, created_at DESC, id DESC)` makes "latest" a single index lookup.
  Hibernate only *validates* the schema (`ddl-auto: validate`); it never changes it.
- `animal-pictures-defaults.yml` – logging and actuator/metrics settings shared by both apps (each app's
  `application.yml` imports it with `spring.config.import`).
- `static/index.html` – the simple UI (plain HTML + `fetch()`), served at `/`.

## Logging and metrics in the code

- **Logs**: SLF4J loggers. INFO for one line per request and for stored pictures, WARN for picture API failures,
  DEBUG for each picture API call and service step. The log pattern adds `[requestId]`.
- **Metrics** (Micrometer, exported to Prometheus by `micrometer-registry-prometheus`):
  `PictureService` counts `animal.pictures.stored`, and `HttpAnimalPictureClient` times each call in
  `animal.pictures.source.requests` (tags `animal`, `outcome`) and records `animal.pictures.source.image.size`.
  Spring Boot adds HTTP, JVM, database-pool and CPU metrics automatically.
- **Monitoring stack**: `docker-compose.monitoring.yml` + `monitoring/` (Prometheus scrape config, Grafana data
  source and the provisioned dashboard). `docker-compose.debug.yml` enables the Java debugger.

## Tests

| Test | Module | What it covers |
|---|---|---|
| `AnimalTypeTest` | core | parsing animal names |
| `PictureServiceTest` | core | count validation, all-or-nothing saving, latest / not found (Mockito) |
| `HttpAnimalPictureClientTest` | core | correct URL per animal, content-type handling, error / empty / oversized responses (`MockRestServiceServer`) |
| `EmbeddedPictureApiIntegrationTest` | embedded | the full app with H2: endpoints, status codes, error JSON, health, request ID, metrics, runtime log levels |
| `PostgresPictureApiIntegrationTest` | postgres | the full app with real PostgreSQL (Testcontainers): binary storage and latest lookup |
| `scripts/smoke-test.sh` | – | end-to-end against a running container and the **real** picture APIs |

## Build and containers

- **Portable build**: `./mvnw` (Maven Wrapper) downloads a pinned Maven 3.9.16, so the build only needs a
  JDK. The `Dockerfile` builds inside `eclipse-temurin:21-jdk`, so it needs only Docker.
- **`Dockerfile`**: multi-stage. `--build-arg MODULE=...` picks the version. The runtime image is
  `eclipse-temurin:21-jre-alpine`, runs as a non-root user, and has a health check on `/actuator/health/readiness`.
- **`docker-compose.yml`**: PostgreSQL (with health check and a data volume) + the app, started only once the
  database is healthy. **`docker-compose.embedded.yml`**: the app alone with a volume for the H2 file.

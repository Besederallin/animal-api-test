# Animal Pictures

A small Java (Spring Boot) microservice that fetches random pictures of **ducks**, **dogs** or **bears**
from public APIs, stores them in a database and serves back the latest one.

| Animal | Source API |
|---|---|
| duck | https://random-d.uk/api/v2/randomimg |
| dog  | https://place.dog/500/500 |
| bear | https://placebear.com/500/500 |

The project comes in **two versions** that share the same code:

| Version | Database | How to run |
|---|---|---|
| `animal-pictures-postgres` | PostgreSQL | `docker compose up --build` |
| `animal-pictures-embedded` | Embedded H2 (file on disk) | `docker compose -f docker-compose.embedded.yml up --build` |

How the code is organised is described in [README-CODE.md](README-CODE.md).

## Requirements

- **Docker** with Docker Compose, to run with containers (nothing else needed).
- **JDK 21+**, only to build or run without Docker. Maven is **not** required: the included Maven Wrapper
  (`./mvnw`, or `mvnw.cmd` on Windows) downloads the right Maven version.

## Run with Docker Compose

PostgreSQL version (app + PostgreSQL 17):

```bash
docker compose up --build
```

Embedded-database version (a single container, H2 stored in a Docker volume):

```bash
docker compose -f docker-compose.embedded.yml up --build
```

Then open **http://localhost:8080** for the UI, or use the API below.
Use `APP_PORT=9090 docker compose up --build` to choose another port.
Stop with `docker compose down` (add `-v` to also delete the stored pictures).

## Run without Docker

Embedded version (needs only a JDK):

```bash
./mvnw -pl animal-pictures-embedded -am package -DskipTests
java -jar animal-pictures-embedded/target/animal-pictures-embedded.jar
```

PostgreSQL version (needs a PostgreSQL; the defaults match this one-liner):

```bash
docker run -d --name animal-db -p 5432:5432 -e POSTGRES_DB=animal_pictures \
  -e POSTGRES_USER=animal -e POSTGRES_PASSWORD=animal postgres:17-alpine
./mvnw -pl animal-pictures-postgres -am package -DskipTests
java -jar animal-pictures-postgres/target/animal-pictures-postgres.jar
```

## REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/pictures/{animal}?count=N` | Fetch `N` (1–10, default 1) pictures of `duck`, `dog` or `bear` and store them. Returns `201` with the stored pictures' metadata. |
| `GET`  | `/api/pictures/{animal}/latest` | Return the most recently stored picture of the animal as an image (`image/jpeg`, ...). |
| `GET`  | `/actuator/health` | Health check. |

```bash
# Fetch and store 3 dog pictures
curl -X POST "http://localhost:8080/api/pictures/dog?count=3"
# [{"id":1,"animal":"dog","contentType":"image/jpeg","sizeBytes":457480,"sourceUrl":"https://place.dog/500/500","createdAt":"..."}, ...]

# Download the latest stored dog picture
curl -o dog.jpg http://localhost:8080/api/pictures/dog/latest
```

Errors are returned as JSON [problem details](https://www.rfc-editor.org/rfc/rfc9457):
`400` for an unknown animal or an invalid `count`, `404` when no picture of that animal is stored yet,
`502` when a picture API fails (in that case nothing from the request is stored).

## UI

http://localhost:8080 has a simple page: choose an animal and a count, click **Fetch & save**, and the
latest stored picture is shown. **Show latest** displays the newest stored picture without fetching.

## Tests

```bash
./mvnw verify
```

This runs:

- **Unit tests** (`animal-pictures-core`): animal name parsing, the service logic, and the HTTP client
  against a mocked server (no network).
- **Integration tests with H2** (`animal-pictures-embedded`): the full REST API, database schema and
  error handling.
- **Integration tests with PostgreSQL** (`animal-pictures-postgres`): the same flow against a real
  PostgreSQL started by [Testcontainers](https://testcontainers.com/). **These need Docker running.**

The picture APIs are faked in these tests so they are fast and reliable. To check against the real
APIs, use the end-to-end smoke test on a running service:

```bash
docker compose up --build -d --wait
scripts/smoke-test.sh http://localhost:8080
```

GitHub Actions (`.github/workflows/ci.yml`) runs `./mvnw verify` and then the smoke test for both
versions in Docker Compose on every push and pull request.

## Configuration

Every setting has a default and can be overridden with an environment variable:

| Variable | Default | Meaning |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `jdbc:postgresql://localhost:5432/animal_pictures`, `animal` / `animal` | Database connection (PostgreSQL version) |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `animal` / `animal` | Database credentials used by `docker-compose.yml` |
| `DATA_DIR` | `./data` (`/app/data` in Docker) | Where the H2 database file is written (embedded version) |
| `ANIMAL_PICTURES_MAX_COUNT` | `10` | Most pictures one request may fetch |
| `ANIMAL_PICTURES_CONNECT_TIMEOUT` / `_READ_TIMEOUT` | `5s` / `15s` | Timeouts when calling the picture APIs |
| `APP_PORT` | `8080` | Host port used by the Compose files |

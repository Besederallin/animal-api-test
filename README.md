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
Logs, metrics, Grafana dashboards and IDE debugging are described in
[DEBUGGING-AND-MONITORING.md](DEBUGGING-AND-MONITORING.md).

## Requirements

| You want to... | You need |
|---|---|
| Run with Docker Compose | **Docker** with Docker Compose (Docker Desktop on Windows). Nothing else: the code is compiled inside Docker. |
| Build, test or run without Docker | **JDK 21** or newer (a full JDK, not just a Java runtime). Maven is **not** needed: the Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads the right version. |
| Run the PostgreSQL integration tests | JDK 21+ **and** Docker running |

### Check your JDK

Run both commands (the same on Linux, macOS and Windows):

```bash
java -version
javac -version
```

Both must print version **21 or higher**, for example `openjdk version "21.0.5"` and `javac 21.0.5`.

- `java` works but `javac` is "not found": only a Java runtime (JRE) is installed. Install a JDK as shown below.
- The version is lower than 21: install JDK 21 and make it the default (or set `JAVA_HOME`, see below).
- The wrapper uses `JAVA_HOME` if it is set, otherwise the `java` on your `PATH`. Check which one it picks with
  `./mvnw -v` (Linux) or `mvnw.cmd -v` (Windows). Look for `Java version: 21...`.

### Install JDK 21

**Linux (Ubuntu / Debian)**

```bash
sudo apt update && sudo apt install -y openjdk-21-jdk
# If several Java versions are installed, choose 21 as the default:
sudo update-alternatives --config java
sudo update-alternatives --config javac
```

Fedora / RHEL: `sudo dnf install java-21-openjdk-devel`. Any OS: [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) or [SDKMAN](https://sdkman.io/) (`sdk install java 21-tem`).

**Windows**

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

Or download the **Eclipse Temurin 21 JDK** `.msi` from https://adoptium.net and tick
**"Set JAVA_HOME variable"** and **"Add to PATH"** during setup. Open a **new** terminal afterwards.

If an older Java is still picked up, set `JAVA_HOME` for the current terminal (adjust the path to your install):

```powershell
# PowerShell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

```bash
# Linux
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
```

## Run with Docker Compose

The commands are the same on **Linux** and **Windows** (PowerShell or Command Prompt, with Docker Desktop running).
Run them from the project folder.

PostgreSQL version (app + PostgreSQL 17):

```bash
docker compose up --build
```

Embedded-database version (a single container, H2 stored in a Docker volume):

```bash
docker compose -f docker-compose.embedded.yml up --build
```

Then open **http://localhost:8080** for the UI, or use the API below.
Stop with `docker compose down` (add `-v` to also delete the stored pictures).

To use another port than 8080:

```bash
# Linux
APP_PORT=9090 docker compose up --build
```

```powershell
# Windows PowerShell
$env:APP_PORT = "9090"; docker compose up --build
```

## Run without Docker (JDK 21 required)

### Linux / macOS

Embedded version:

```bash
./mvnw -pl animal-pictures-embedded -am package -DskipTests
java -jar animal-pictures-embedded/target/animal-pictures-embedded.jar
```

If you get `Permission denied` on `./mvnw`, run `chmod +x mvnw` once.

PostgreSQL version (needs a PostgreSQL; the app's defaults match this container):

```bash
docker run -d --name animal-db -p 5432:5432 -e POSTGRES_DB=animal_pictures \
  -e POSTGRES_USER=animal -e POSTGRES_PASSWORD=animal postgres:17-alpine
./mvnw -pl animal-pictures-postgres -am package -DskipTests
java -jar animal-pictures-postgres/target/animal-pictures-postgres.jar
```

### Windows (PowerShell)

Use `mvnw.cmd` instead of `./mvnw` and `\` in paths:

```powershell
.\mvnw.cmd -pl animal-pictures-embedded -am package -DskipTests
java -jar animal-pictures-embedded\target\animal-pictures-embedded.jar
```

PostgreSQL version:

```powershell
docker run -d --name animal-db -p 5432:5432 -e POSTGRES_DB=animal_pictures `
  -e POSTGRES_USER=animal -e POSTGRES_PASSWORD=animal postgres:17-alpine
.\mvnw.cmd -pl animal-pictures-postgres -am package -DskipTests
java -jar animal-pictures-postgres\target\animal-pictures-postgres.jar
```

In Command Prompt (`cmd`) the commands are the same, without the leading `.\` and with `^` instead of
the backtick for line continuation.

To connect to a different database, set the connection before `java -jar`:

```bash
# Linux
export SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/animal_pictures
```

```powershell
# Windows PowerShell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://db-host:5432/animal_pictures"
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
./mvnw verify          # Linux / macOS
```

```powershell
.\mvnw.cmd verify      # Windows
```

This runs:

- **Unit tests** (`animal-pictures-core`): animal name parsing, the service logic, and the HTTP client
  against a mocked server (no network).
- **Integration tests with H2** (`animal-pictures-embedded`): the full REST API, database schema and
  error handling.
- **Integration tests with PostgreSQL** (`animal-pictures-postgres`): the same flow against a real
  PostgreSQL started by [Testcontainers](https://testcontainers.com/). **These need Docker running.**
  Without Docker, run the other tests only: `./mvnw verify -pl animal-pictures-core,animal-pictures-embedded`
  (Windows: `.\mvnw.cmd verify -pl animal-pictures-core,animal-pictures-embedded`).

The picture APIs are faked in these tests so they are fast and reliable. To check against the real
APIs, use the end-to-end smoke test on a running service:

```bash
docker compose up --build -d --wait
scripts/smoke-test.sh http://localhost:8080
```

The smoke test is a Bash script: on Windows run it from **Git Bash** or **WSL** with the same command.

GitHub Actions (`.github/workflows/ci.yml`) runs `./mvnw verify` and then the smoke test for both
versions in Docker Compose on every push and pull request.

## Debugging and monitoring

Add-on Compose files work with either version. Full guide: [DEBUGGING-AND-MONITORING.md](DEBUGGING-AND-MONITORING.md).

```bash
# Prometheus + Grafana dashboard (http://localhost:3000)
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up --build
# Java debugger on port 5005 + DEBUG logs
docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build
```

- Every response has an `X-Request-Id` header, and every log line for that request carries the same ID.
- `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`, and `/actuator/loggers`
  to change log levels without a restart.
- `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` switches to JSON logs.

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
| `LOGGING_LEVEL_COM_EXAMPLE_ANIMALPICTURES` | `INFO` | Log level of this app (`DEBUG` for more detail) |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | (plain text) | `ecs` or `logstash` for JSON logs |
| `GRAFANA_PORT` / `PROMETHEUS_PORT` / `DEBUG_PORT` | `3000` / `9090` / `5005` | Ports of the monitoring and debug add-ons |

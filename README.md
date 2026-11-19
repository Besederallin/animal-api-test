# Animal Pictures

A small Java (Spring Boot) microservice that fetches random pictures of **ducks**, **dogs** or **bears**
from public APIs, stores them in a database and serves back the latest one.

| Animal | Source API |
|---|---|
| duck | https://random-d.uk/api/v2/randomimg |
| dog  | https://place.dog/500/500 |
| bear | https://placebear.com/500/500 |

## Quick start

You need **[Git](https://git-scm.com/downloads)** and **[Docker](https://docs.docker.com/get-docker/)**
(on Windows: [Docker Desktop](https://www.docker.com/products/docker-desktop/), started).
No Java or Maven is needed: everything is built inside Docker.

```bash
git clone https://github.com/Besederallin/animal-api-test.git
cd animal-api-test
docker compose up --build
```

Wait until the log says `Started PostgresAnimalPicturesApplication` (the first build takes a few minutes),
then open **http://localhost:8080**, pick an animal and click **Fetch & save**.

Stop with `Ctrl+C`, then `docker compose down` (add `-v` to also delete the stored pictures).

These commands are the same on **Linux, macOS and Windows** (PowerShell, Command Prompt or Git Bash).

## Two versions

The same code runs with two kinds of database. Pick one:

| Version | Database | Start command |
|---|---|---|
| PostgreSQL *(default)* | PostgreSQL 17 in a second container | `docker compose up --build` |
| Embedded | H2, built into the app (one container) | `docker compose -f docker-compose.embedded.yml up --build` |

## Optional extras

| What | Start command | Then open |
|---|---|---|
| Monitoring (Prometheus + Grafana dashboard) | `docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up --build` | http://localhost:3000 |
| Debugging (Java debugger on port 5005, DEBUG logs) | `docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build` | attach your IDE to `localhost:5005` |

Details: [DEBUGGING-AND-MONITORING.md](DEBUGGING-AND-MONITORING.md). How the code works: [README-CODE.md](README-CODE.md).

### Troubleshooting

- **`port is already allocated`** – something else uses port 8080. Pick another port:
  `APP_PORT=9090 docker compose up --build` (Linux/macOS) or
  `$env:APP_PORT="9090"; docker compose up --build` (Windows PowerShell), then open http://localhost:9090.
- **`Cannot connect to the Docker daemon`** / `docker: command not found` – start Docker (Docker Desktop on Windows/macOS).
- **`Repository not found` when cloning** – the repository is private: ask the owner for access and sign in
  to GitHub (for example with `gh auth login`), or download it as a ZIP from the GitHub page
  (*Code → Download ZIP*) and run the same `docker compose` command in the unpacked folder.

## Run without Docker

Only needed if you want to build or run the app with your own Java. Requires **JDK 21 or newer**.
Maven is not needed: the included Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads it.

<details>
<summary><b>Check / install JDK 21</b></summary>

Check (Linux, macOS and Windows). Both must show version **21 or higher**:

```bash
java -version
javac -version
```

If `javac` is "not found", only a Java runtime is installed: install a JDK.

- **Ubuntu / Debian**: `sudo apt install -y openjdk-21-jdk`
  (if several versions are installed: `sudo update-alternatives --config java` and `... --config javac`)
- **Fedora / RHEL**: `sudo dnf install java-21-openjdk-devel`
- **Windows**: `winget install EclipseAdoptium.Temurin.21.JDK`, or the installer from https://adoptium.net
  (tick *Set JAVA_HOME* and *Add to PATH*). Open a new terminal afterwards.
- **macOS**: `brew install --cask temurin@21`

The wrapper uses `JAVA_HOME` if set, otherwise `java` from your `PATH`. `./mvnw -v` (Windows: `.\mvnw.cmd -v`)
shows which Java it uses. To point it at JDK 21:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64                          # Linux
```

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"     # Windows PowerShell (adjust the path)
```

</details>

<details>
<summary><b>Build and run</b></summary>

Embedded version (no database needed):

```bash
./mvnw -pl animal-pictures-embedded -am package -DskipTests                     # Linux / macOS
java -jar animal-pictures-embedded/target/animal-pictures-embedded.jar
```

```powershell
.\mvnw.cmd -pl animal-pictures-embedded -am package -DskipTests                 # Windows
java -jar animal-pictures-embedded\target\animal-pictures-embedded.jar
```

PostgreSQL version: start a database first (the app's defaults match this one), then build and run
`animal-pictures-postgres` the same way:

```bash
docker run -d --name animal-db -p 5432:5432 -e POSTGRES_DB=animal_pictures -e POSTGRES_USER=animal -e POSTGRES_PASSWORD=animal postgres:17-alpine
./mvnw -pl animal-pictures-postgres -am package -DskipTests
java -jar animal-pictures-postgres/target/animal-pictures-postgres.jar
```

To use another database, set `SPRING_DATASOURCE_URL` (and `_USERNAME`, `_PASSWORD`) before `java -jar`.
On Linux, if `./mvnw` gives `Permission denied`, run `chmod +x mvnw` once.

</details>

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

Needs JDK 21 (see above). The PostgreSQL tests also need Docker running.

```bash
./mvnw verify          # Linux / macOS
.\mvnw.cmd verify      # Windows
```

- **Unit tests**: animal name parsing, service logic, HTTP client against a mocked server.
- **Integration tests**: the full REST API against H2 and against a real PostgreSQL
  ([Testcontainers](https://testcontainers.com/)).
  Without Docker, skip the PostgreSQL ones: `./mvnw verify -pl animal-pictures-core,animal-pictures-embedded`.
- **Smoke test** against the real picture APIs, on a running app (Bash; on Windows use Git Bash or WSL):
  `scripts/smoke-test.sh http://localhost:8080`

GitHub Actions runs all of these on every push.

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

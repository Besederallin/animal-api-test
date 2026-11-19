# Debugging and monitoring

Quick reference:

| I want to... | Do this |
|---|---|
| See the logs | `docker compose logs -f app` |
| Follow one request through the logs | Search for its `X-Request-Id` (returned in every response) |
| Get more detail in the logs | Run with `docker-compose.debug.yml`, or switch to DEBUG at runtime via `/actuator/loggers` |
| Step through the code | Run with `docker-compose.debug.yml` and attach your IDE to port `5005` |
| Check if it is healthy | `GET /actuator/health` |
| See numbers (traffic, latency, errors) | `GET /actuator/prometheus`, or start Grafana with `docker-compose.monitoring.yml` |

All commands below work on Linux and Windows (PowerShell). Where they differ, both are shown.
Combine the add-on files freely, e.g. app + monitoring + debug:

```bash
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml -f docker-compose.debug.yml up --build
```

For the embedded version, use `-f docker-compose.embedded.yml` instead of `-f docker-compose.yml`.

---

## 1. Logging

### Reading the logs

```bash
docker compose logs -f app                 # follow the app logs
docker compose logs --since 10m app        # only the last 10 minutes
```

Without Docker, logs go to the console of `java -jar ...`.

### What gets logged

```
2026-11-19T10:33:18.459Z DEBUG [debug-demo] ... c.e.a.picture.PictureService         : Fetching 1 duck picture(s)
2026-11-19T10:33:19.746Z DEBUG [debug-demo] ... c.e.a.source.HttpAnimalPictureClient : GET https://random-d.uk/api/v2/randomimg -> success in 1286 ms
2026-11-19T10:33:19.907Z  INFO [debug-demo] ... c.e.a.picture.PictureService         : Stored 1 duck picture(s), ids [54]
2026-11-19T10:33:19.924Z  INFO [debug-demo] ... c.e.a.web.RequestLoggingFilter       : POST /api/pictures/duck -> 201 (1480 ms)
```

- **One line per request** (INFO): method, path, status code and duration.
- **Request ID** in `[...]` on every line. It comes from the caller's `X-Request-Id` header or is generated,
  and is returned in the `X-Request-Id` response header. When something fails, take the ID from the
  response and search for it:

  ```bash
  docker compose logs app | grep debug-demo                    # Linux
  docker compose logs app | Select-String debug-demo           # Windows PowerShell
  ```

- **Stored pictures** (INFO) with their database IDs.
- **Picture API failures** (WARN) with the cause (timeout, HTTP status, not an image...).
- **Each picture API call with its duration** and service steps (DEBUG, off by default).

### More detail: DEBUG logs

At startup, with the debug add-on (sets `LOGGING_LEVEL_COM_EXAMPLE_ANIMALPICTURES=DEBUG`):

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build
```

**At runtime, without a restart**, through the actuator `loggers` endpoint:

```bash
# Linux
curl -X POST localhost:8080/actuator/loggers/com.example.animalpictures \
     -H 'Content-Type: application/json' -d '{"configuredLevel":"DEBUG"}'
# back to normal
curl -X POST localhost:8080/actuator/loggers/com.example.animalpictures \
     -H 'Content-Type: application/json' -d '{"configuredLevel":null}'
```

```powershell
# Windows PowerShell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/actuator/loggers/com.example.animalpictures `
  -ContentType 'application/json' -Body '{"configuredLevel":"DEBUG"}'
```

Useful logger names: `com.example.animalpictures` (this app), `org.hibernate.SQL` (SQL statements),
`org.springframework.web` (Spring MVC request handling).
Without Docker: `java -jar app.jar --logging.level.com.example.animalpictures=DEBUG`.

### JSON logs (for ELK, Loki, Datadog, CloudWatch...)

Set `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (or `logstash`) and each line becomes a JSON object, with
the request ID as a field:

```json
{"@timestamp":"...","log":{"level":"INFO","logger":"...RequestLoggingFilter"},"requestId":"debug-demo","message":"POST /api/pictures/duck -> 201 (1480 ms)", ...}
```

In Compose, add it under `app: environment:`.

---

## 2. Debugging with an IDE

The debug add-on starts the JVM with the Java debugger listening on port **5005**:

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build
```

Then attach:

- **IntelliJ IDEA**: *Run → Edit Configurations → + → Remote JVM Debug*, host `localhost`, port `5005`, module
  `animal-pictures-core`. Click Debug, set a breakpoint (e.g. in `PictureService.fetchAndSave`), and call the API.
- **VS Code** (Extension Pack for Java): add to `.vscode/launch.json`:
  ```json
  { "type": "java", "name": "Attach to Docker", "request": "attach", "hostName": "localhost", "port": 5005 }
  ```
- **Eclipse**: *Run → Debug Configurations → Remote Java Application*, port `5005`.

Use `DEBUG_PORT=5006` to change the port. Do **not** use the debug add-on in production: anyone who can reach
the port can run code in the JVM.

**Without Docker** you can simply run the main class (`EmbeddedAnimalPicturesApplication` or
`PostgresAnimalPicturesApplication`) in Debug mode from your IDE, or start the jar with the debugger:

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar animal-pictures-embedded/target/animal-pictures-embedded.jar
```

---

## 3. Health and info

| Endpoint | Shows |
|---|---|
| `GET /actuator/health` | `UP`/`DOWN`, plus database and disk status |
| `GET /actuator/health/liveness` | Is the process alive (for Kubernetes liveness probes) |
| `GET /actuator/health/readiness` | Ready for traffic (used by the Docker `HEALTHCHECK` and the smoke test) |
| `GET /actuator/info` | App name, version, build time, Java version, OS |

`docker compose ps` shows the container as `healthy` / `unhealthy` based on the readiness check.

---

## 4. Metrics

All metrics are available at `GET /actuator/prometheus` (Prometheus format) and
`GET /actuator/metrics/<name>` (JSON, good for a quick look).

| Metric (Prometheus name) | Tags | Meaning |
|---|---|---|
| `animal_pictures_stored_total` | `animal` | Pictures stored since the app started |
| `animal_pictures_source_requests_seconds_*` | `animal`, `outcome` (`success`/`error`) | Calls to the picture APIs: count, duration, latency histogram |
| `animal_pictures_source_image_size_bytes_*` | `animal` | Size of downloaded images |
| `http_server_requests_seconds_*` | `method`, `uri`, `status` | Every REST request: count, duration, latency histogram |
| `hikaricp_connections_*` | | Database connection pool (active, idle, waiting) |
| `jvm_memory_*`, `jvm_gc_*`, `jvm_threads_*`, `process_cpu_usage` | | JVM and process |

Every metric carries an `application` tag (`animal-pictures-postgres` or `animal-pictures-embedded`).

Quick looks:

```bash
curl localhost:8080/actuator/metrics/animal.pictures.stored
curl "localhost:8080/actuator/metrics/animal.pictures.source.requests?tag=animal:dog"
curl localhost:8080/actuator/prometheus | grep animal_pictures
```

---

## 5. Prometheus and Grafana

```bash
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up --build
```

| | URL | |
|---|---|---|
| **Grafana** | http://localhost:3000 | Opens on the **Animal Pictures** dashboard. View without login; edit as `admin` / `admin` |
| **Prometheus** | http://localhost:9090 | Raw queries; *Status → Targets* shows whether the app is being scraped |

The dashboard (`monitoring/grafana/dashboards/animal-pictures.json`) shows:

- **Service up**, **pictures stored** per animal, **API error rate** (5xx share)
- **API requests/s** by endpoint and status, and **API latency p95**
- **Picture API calls/s** by animal and outcome, and **picture API latency p95**: shows when random-d.uk,
  place.dog or placebear.com is slow or failing
- **JVM heap**, **database connections**, **CPU**

Prometheus keeps 7 days of data. Change the ports with `GRAFANA_PORT` / `PROMETHEUS_PORT`, and the Grafana
password with `GRAFANA_ADMIN_PASSWORD`. Stop with the same `-f` flags plus `down` (`down -v` also deletes the data).

Useful PromQL to start alerts from:

```promql
# Picture API failing more than 20% of the time
sum by (animal) (rate(animal_pictures_source_requests_seconds_count{outcome="error"}[5m]))
  / sum by (animal) (rate(animal_pictures_source_requests_seconds_count[5m])) > 0.2

# REST API p95 latency above 5 seconds
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri!~"/actuator.*"}[5m]))) > 5
```

---

## 6. Common problems

| Symptom | Where to look |
|---|---|
| `502 Picture API unavailable` | The `detail` field and the WARN log line name the API and the cause. The *Picture API calls* panel shows `outcome="error"`. Usually the public API is down or slow; try again. |
| App container keeps restarting | `docker compose logs app`. Most often the database is not reachable (check `SPRING_DATASOURCE_URL` and `docker compose ps postgres`). |
| Requests are slow | Compare *API latency* with *Picture API latency* in Grafana. If they match, the time is spent waiting for the public picture APIs. |
| Grafana panels say "No data" | Prometheus → *Status → Targets*: the `animal-pictures` target must be `UP`. Make some API calls first; some panels only show data after traffic. |

## Security note

`/actuator` is on the same port as the API so it is easy to try out. In production, keep only `health` public:
put the actuator on a separate internal port (`MANAGEMENT_SERVER_PORT=8081`) or limit the exposed endpoints
(`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus`), since `loggers` lets callers change log levels.

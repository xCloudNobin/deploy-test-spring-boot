# Spring Boot Taskboard

A meaningful **Spring Boot 3** application for the xCloud app-compatibility
suite: a project/task board with a JSON API and a DOM-rendered UI, persisted to
**SQLite** through Spring Data JPA.

It is a production-process fixture, not a success-page shell: every workflow
reads and writes through parameterized JPA queries, all input is validated
server-side with meaningful error payloads, and `scripts/verify.sh` exercises
the real production process (`java -jar`) end to end.

## Feature summary

- Spring Boot **3.5.16** on **Java 21** (executable fat jar).
- Projects and tasks with status/priority, search (`q`), and status/priority /
  project filters — served over a JSON API consumed by a small DOM-rendered
  client (`src/main/resources/static`).
- Validated CRUD: blank/over-long fields, invalid status/priority, malformed
  JSON, missing task `projectId`, and not-found resources all return meaningful
  JSON errors (`400`/`404`); output is escaped client-side by rendering via
  `textContent` only (no `innerHTML` with user data).
- Parameterized JPQL everywhere with `LIKE` wildcards (`%`, `_`) and the escape
  character (`!`) escaped server-side — no string-built queries from user input.
- Idempotent, deterministic schema setup (`CREATE TABLE IF NOT EXISTS`,
  executed at startup by `SchemaInitializer`) and repeatable seed data guarded by
  a one-time `seed_flag` so restarts never duplicate rows.
- Persistence: explicit SQLite file (`DATA_DIR`/`DB_URL`); the app never stores
  permanent state in an ephemeral release directory. A `PRAGMA foreign_keys=ON`
  is applied on every pooled connection so `ON DELETE CASCADE` integrity holds.
- Timestamps are stored as ISO-8601 **TEXT** (SQLite-native); no JPA temporal
  conversion is used, so round-tripping is deterministic on this dialect/JDBC
  stack.
- `/actuator/health/liveness` (process alive) and `/actuator/health/readiness`
  (real write-to-heartbeat database probe; **503** while the database is
  unavailable — proven by `scripts/smoke.sh`).
- Non-sensitive release marker: `scripts/build.sh` writes a generated `VERSION`
  file (git SHA by default — `BUILD_MARKER` to override); it is baked into the
  jar at build time and served by `/api/meta` and shown in the UI footer.
- Graceful SIGTERM/SIGINT shutdown (`server.shutdown=graceful`, drain timeout),
  logs to stdout/stderr.

## Runtime and dependencies

- **Java 21** (validated 21.0.12).
- **Spring Boot 3.5.16** (parent BOM pins Spring Framework, Hibernate, Tomcat).
- **Hibernate 6.6.53** with the `hibernate-community-dialects` SQLite dialect.
- **sqlite-jdbc 3.49.1.0** for SQLite persistence.
- **Maven 3.8.7** validated; dependencies are pinned by the Spring Boot BOM and
  the `pom.xml` (no floating ranges), reproducible from Maven Central.

Runtime versions (this verification):

| Component        | Version |
|------------------|---------|
| Java             | 21.0.12 |
| Spring Boot      | 3.5.16  |
| Hibernate        | 6.6.53.Final |
| sqlite-jdbc      | 3.49.1.0 |
| SQLite           | 3.45.1 (system CLI) |
| Maven            | 3.8.7 |

## Quick start (development)

```bash
mvn -B -ntp clean verify   # compile + integration tests + fat jar
java -jar target/taskboard.jar     # or: mvn -B -ntp spring-boot:run
```

Open http://localhost:8080 — the seeder created two demo projects and four
tasks on first boot.

## Production start

```bash
scripts/build.sh           # write VERSION release marker
mvn -B -ntp clean package
# bind address/port + persistent DB path via env (see .env.example):
PORT=8080 BIND_HOST=0.0.0.0 DATA_DIR=/var/lib/taskboard \
    java -jar target/taskboard.jar
```

- Binds to `BIND_HOST:PORT` (defaults **0.0.0.0:8080**).
- Run from the repository root so `./data` (default `DATA_DIR`) and the release
  marker resolve correctly.
- Logs go to stdout/stderr; the process answers SIGTERM/SIGINT with a graceful,
  draining shutdown.
- For redeploys, mount/point `DATA_DIR` (or `DB_URL`) at durable storage that
  survives beyond the release directory; the embedded SQLite file is the single
  source of truth.

## Health and readiness

| Endpoint | Meaning |
|----------|---------|
| `GET /actuator/health/liveness` | Process is alive (always 200 while serving). |
| `GET /actuator/health/readiness` | Aggregate readiness: includes the `db` indicator and a custom `dbProbe` that opens the SQLite file and performs a real write/read on the `heartbeat` table. **503** with `status:"DOWN"` while the database is unavailable. |

`/actuator/health/readiness` is a genuine dependency probe, not a static marker.
`scripts/smoke.sh` proves it: it starts the jar against a database path that
cannot be created (parent is a regular file), observes readiness drop to **503**
while liveness stays **200**, then restores a healthy process and proves
readiness recovers.

## Environment variables

See `.env.example` for the full commented list.

| Variable   | Required | Default | Purpose |
|------------|----------|---------|---------|
| `PORT`       | no | `8080` | bind port |
| `BIND_HOST`  | no | `0.0.0.0` | bind address |
| `DATA_DIR`   | no | `<repo>/data` | base data directory |
| `DB_URL`     | no | `jdbc:sqlite:<DATA_DIR>/taskboard.db` | full SQLite JDBC URL (persistent path) |
| `BUILD_MARKER` | no | git SHA | release marker in `/api/meta` and the UI footer |

No credentials or secrets are committed or required.

## Persistence

Data lives in the SQLite file at the `DB_URL` path (default
`<DATA_DIR>/taskboard.db`, gitignored). For redeploys that reuse or replace the
release directory, mount a persistent volume at `DATA_DIR`/`DB_URL` so the file
survives. `scripts/smoke.sh` proves persistence: it creates a `persist-*`
survivor project+task over HTTP, gracefully stops the production process,
restarts it on the **same database path**, and verifies the records are still
served — then confirms the file on disk with `sqlite3`.

## Schema

Created idempotently at startup by `SchemaInitializer` (`CREATE TABLE IF NOT
EXISTS`; also documented in code as `Schema.DDL_STATEMENTS`):

- `project` — id, name, description, status (`active|archived`), ISO timestamps.
- `task` — id, `project_id` FK (`ON DELETE CASCADE`), title, description, status
  (`todo|in_progress|done`), priority (`low|medium|high`), ISO timestamps.
- `seed_flag` — marks the one-time seed as applied (id=1).
- `heartbeat` — backing table for the readiness write probe.

Seeding is repeatable: `SchemaInitializer` only seeds when `seed_flag` is empty,
so re-opens never add rows.

## API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/actuator/health/liveness` | liveness |
| GET | `/actuator/health/readiness` | readiness (DB probe) |
| GET | `/api/meta` | release marker + runtime versions |
| GET/POST | `/api/projects` | list (filters `q`, `status`) / create projects |
| GET/PATCH/DELETE | `/api/projects/{id}` | read / update / delete a project |
| GET/POST | `/api/tasks` | list (filters `q`, `status`, `priority`, `projectId`) / create tasks |
| GET/PATCH/DELETE | `/api/tasks/{id}` | read / update / delete a task |

The UI at `/` consumes the same JSON API.

## Automated verification

```bash
scripts/verify.sh
```

Runs, in order:

1. `scripts/build.sh` — writes the `VERSION` release marker.
2. Clean build — `mvn -B -ntp clean verify` (fresh compile + integration suite).
3. Integration tests (MockMvc + real context/TempFile SQLite) — CRUD,
   search/status/priority filters, `LIKE`-wildcard escaping, validation
   negatives (blank/over-long/mistyped fields, invalid status/priority,
   malformed JSON, missing project, unknown project, 404s), cascade delete,
   schema/seed idempotency, on-disk persistence through an independent JDBC
   connection, release marker, health probes.
4. `scripts/smoke.sh` — real production process (`java -jar`): liveness /
   readiness, CRUD over HTTP, search/status filters, release marker, negative
   cases, graceful SIGTERM stop → restart persistence, database-unavailable
   readiness (liveness 200 / readiness **503**) and recovery.

Exit 0 only when every check passes.

## Repository layout

```
pom.xml                        Spring Boot 3.5.16 parent + sqlite-jdbc + tests
scripts/                       build.sh (VERSION marker), smoke.sh (production
                               check), verify.sh (full verification)
src/main/java/dev/xcloud/taskboard/
  TaskboardApplication.java    Spring Boot entrypoint
  domain/                      entity + enum + repository + ISO-time helper
  dto/                         request/response records + ApiError
  web/                         controllers + @RestControllerAdvice error map
  service/                     TaskboardService, SchemaInitializer,
                               DatabaseHealthIndicator
src/main/resources/            application.properties + static UI (index.html,
                               app.js, style.css)
src/test/java/                 TaskboardIntegrationTest (10 tests)
.env.example                   safe example environment
README.md, VERIFICATION.md, LICENSE (MIT)
```

## License

MIT — see [LICENSE](LICENSE). This fixture is part of the MIT-licensed
[xCloud app-compatibility suite](https://github.com/xCloudNobin/app-compatibility).
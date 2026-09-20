# Verification record

Fixture: `xCloudNobin/deploy-test-spring-boot` (public)
Category: Spring Boot (Java 21)

## Result

**PASS** — `scripts/verify.sh` exit 0, run locally on the production process
(`java -jar target/taskboard.jar`), not the development server.

Note: this is a *local* verification of the candidate commit. No live xCloud
platform qualification has been run; deployment is **not** claimed verified.

## Candidate

- Commit: `8c3832d25d33fa028d4643c7b9e9861e136c081d`
  (`feat/compatibility-spring-boot`, short `8c3832d`)
- PR: https://github.com/xCloudNobin/deploy-test-spring-boot/pull/1
- Date: 2026-09-20

## Environment

| Component | Version |
|-----------|---------|
| Java | OpenJDK 21.0.12 |
| Spring Boot | 3.5.16 |
| Hibernate | 6.6.53.Final (hibernate-community-dialects) |
| sqlite-jdbc | 3.49.1.0 |
| SQLite (CLI, disk checks) | 3.45.1 |
| Maven | 3.8.7 |
| OS | Ubuntu 24.04 (x86_64) |

## Commands and outcome

| Command | Outcome |
|---------|---------|
| `scripts/build.sh` | wrote `VERSION` marker (`8c3832d`) |
| `mvn -B -ntp clean verify` | BUILD SUCCESS; `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` |
| `scripts/smoke.sh` | passed (all steps below) |

`scripts/verify.sh` (build + clean Maven build/tests + production smoke) exited
**0**; full transcript in `/root/projects/compat-finish/logs/spring-boot-verify.log`.

## Smoke coverage (real production jar)

- Liveness `200`, readiness `200` on a healthy database.
- `/api/meta` served release marker equals the built `VERSION`.
- Project CRUD: create `201`, get `200`, patch `200` (status change), delete
  `204`, get-after-delete `404`.
- Task CRUD: create `201` (with project name), search `q`, status/priority and
  projectId filters, patch `200`, delete `204`, `404` after delete.
- Error cases: blank name `400`, malformed JSON `400`, invalid status `400`,
  unknown project on task create `404`, patch/delete missing resource `404`.
- Persistence: `persist-*` survivor project+task created over HTTP, graceful
  SIGTERM (drain logged, exit 143/0), restart on the same SQLite file, survivor
  records still served; `sqlite3` confirmed the row count in the file.
- Database-unavailable readiness: process started with an un-creatable SQLite
  path reported liveness `200` and readiness `503`, body `status:"DOWN"`, then
  a healthy instance recovered to readiness `200`.
- sqlite file confirmed persisted at the configured `DATA_DIR`.

## Security / licensing notes

- MIT license with attribution; no credentials or secrets committed; no
  real secrets in logs (`.env.example` uses placeholders).
- Parameterized JPQL/LIKE-escaping; output rendered with `textContent` only.
- No sessions/cookies, so CSRF is not applicable to the mutation surface.
- New repo, no prior history published (base branch is empty).

## Limitations

- Local verification only; live xCloud deployment qualification is a separate
  step and has not been performed.
- SQLite embedded DB is single-writer; adequate for this fixture.
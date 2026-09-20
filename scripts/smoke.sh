#!/usr/bin/env bash
# Production smoke test for the Spring Boot taskboard fixture.
#
# Starts the REAL executable jar (java -jar) and exercises over HTTP:
#   - liveness + readiness probes
#   - project/task CRUD with 201/200/204
#   - search (q) and status/priority filters
#   - validation negatives (blank fields, bad enums, malformed JSON) -> 400
#   - not-found (missing resources) -> 404
#   - release marker (VERSION) served by /api/meta
#   - graceful SIGTERM shutdown, then RESTART on the same persistent database
#     directory: a survivor record must still be served
#   - database-unavailable readiness: a process whose SQLite path cannot be
#     created must report liveness 200 and readiness 503 (DOWN), proving the
#     readiness probe genuinely depends on the database
#
# Exit code is nonzero if any check fails.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA="${JAVA:-$(command -v java)}"
[ -n "$JAVA" ] || { echo "java executable not found" >&2; exit 1; }

JAR="$ROOT/target/taskboard.jar"
[ -f "$JAR" ] || { echo "missing $JAR - run scripts/verify.sh or mvn package first" >&2; exit 1; }

step() { printf '\n=== %s ===\n' "$*"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

WORK="$(mktemp -d)"
DATA_DIR="$WORK/data"
BLOCKED="$WORK/blocked"
mkdir -p "$BLOCKED"
: > "$BLOCKED/not-a-directory"
PORT="$((21000 + ($$ % 900)))"
BAD_PORT="$((PORT + 1))"
RUN_ID="$$"

pid=""
log="$WORK/app.log"
cleanup() {
    if [ -n "$pid" ] && kill -0 "$pid" >/dev/null 2>&1; then
        kill -TERM "$pid" >/dev/null 2>&1 || true
        wait "$pid" >/dev/null 2>&1 || true
    fi
    rm -rf "$WORK"
}
trap cleanup EXIT

http_code() { curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$@"; }

http_body() { curl -s --max-time 3 "$@"; }

wait_up() {
    local base="$1" tries="${2:-90}" wait=0
    while [ "$wait" -lt "$tries" ]; do
        [ "$(http_code "$base/actuator/health/readiness")" = "200" ] && return 0
        sleep 1
        wait=$((wait + 1))
    done
    echo "timed out waiting for $base" >&2
    tail -n 60 "$log" >&2 || true
    return 1
}

start_app() {
    local port="$1" data_dir="$2" db_url="$3" stdout="$4"
    PORT="$port" BIND_HOST="127.0.0.1" DATA_DIR="$data_dir" DB_URL="$db_url" \
        nohup "$JAVA" -jar "$JAR" >>"$stdout" 2>&1 &
    pid=$!
}

stop_graceful() {
    local instance_log="${1:-$log}"
    kill -TERM "$pid"
    local exitcode=0
    wait "$pid" || exitcode=$?
    pid=""
    if [ "$exitcode" -ne 0 ] && [ "$exitcode" -ne 143 ]; then
        fail "process did not terminate after SIGTERM (exit $exitcode)"
    fi
    grep -q "Commencing graceful shutdown" "$instance_log" || fail "no graceful shutdown log line in $instance_log"
}

survivor="persist-$RUN_ID"
base="http://127.0.0.1:$PORT"

step "start production process (port $PORT)"
start_app "$PORT" "$DATA_DIR" "jdbc:sqlite:$DATA_DIR/taskboard.db" "$log"
wait_up "$base" || fail "app did not become ready"
[ "$(http_code "$base/actuator/health/liveness")" = "200" ] || fail "liveness not 200"
[ "$(http_code "$base/actuator/health/readiness")" = "200" ] || fail "readiness not 200"

step "release marker via /api/meta"
meta="$(http_body "$base/api/meta")"
echo "$meta" | jq -e --arg want "$(cat "$ROOT/VERSION")" '.release == $want' >/dev/null \
    || fail "served release marker does not match VERSION (got: $(echo "$meta" | jq -r .release))"

step "project CRUD"
pj="$(curl -s -X POST "$base/api/projects" -H 'Content-Type: application/json' \
    -d '{"name":"Smoke Project","description":"created by smoke","status":"active"}')"
pid_created="$(echo "$pj" | jq -er .id)" || fail "create project: $pj"
echo "$pj" | jq -e '.name == "Smoke Project" and .status == "active"' >/dev/null || fail "unexpected project payload: $pj"

http_body "$base/api/projects/$pid_created" | jq -e '.id == '"$pid_created" >/dev/null \
    || fail "GET project failed"
curl -s -X PATCH "$base/api/projects/$pid_created" -H 'Content-Type: application/json' \
    -d '{"status":"archived"}' | jq -e '.status == "archived"' >/dev/null \
    || fail "PATCH project failed"
[ "$(http_code -X DELETE "$base/api/projects/$pid_created")" = "204" ] || fail "DELETE project not 204"
[ "$(http_code "$base/api/projects/$pid_created")" = "404" ] || fail "deleted project still found"

step "task CRUD"
tp="$(curl -s -X POST "$base/api/projects" -H 'Content-Type: application/json' \
    -d '{"name":"Smoke Host"}')"
ph="$(echo "$tp" | jq -er .id)" || fail "create host project: $tp"
tk="$(curl -s -X POST "$base/api/tasks" -H 'Content-Type: application/json' \
    -d "{\"projectId\":$ph,\"title\":\"Smoke Task\",\"description\":\"x\",\"status\":\"todo\",\"priority\":\"high\"}")"
tk_id="$(echo "$tk" | jq -er .id)" || fail "create task: $tk"
echo "$tk" | jq -e '.projectName == "Smoke Host" and .status == "todo"' >/dev/null || fail "task payload: $tk"

http_body "$base/api/tasks?q=smoke" | jq -e 'any(.[]; .id == '"$tk_id"')' >/dev/null \
    || fail "task search (q=smoke) missing created task"
http_body "$base/api/tasks?status=done" | jq -e 'any(.[]; .id == '"$tk_id"') | not' >/dev/null \
    || fail "task status filter leaked a todo task"
curl -s -X PATCH "$base/api/tasks/$tk_id" -H 'Content-Type: application/json' \
    -d '{"status":"done","priority":"medium"}' | jq -e '.status == "done" and .priority == "medium"' >/dev/null \
    || fail "PATCH task failed"
http_body "$base/api/tasks?status=done&projectId=$ph" | jq -e 'any(.[]; .id == '"$tk_id"')' >/dev/null \
    || fail "combined filters missed updated task"
[ "$(http_code -X DELETE "$base/api/tasks/$tk_id")" = "204" ] || fail "DELETE task not 204"
[ "$(http_code "$base/api/tasks/$tk_id")" = "404" ] || fail "deleted task still found"

step "validation and error cases"
code="$(http_code -X POST "$base/api/projects" -H 'Content-Type: application/json' -d '{"name":"   "}')"
[ "$code" = "400" ] || fail "blank-name project expected 400, got $code"
code="$(http_code -X POST "$base/api/projects" -H 'Content-Type: application/json' -d '{not json')"
[ "$code" = "400" ] || fail "malformed JSON expected 400, got $code"
code="$(http_code -X POST "$base/api/tasks" -H 'Content-Type: application/json' -d '{"projectId":999999,"title":"ghost"}')"
[ "$code" = "404" ] || fail "unknown project expected 404, got $code"
code="$(http_code -X POST "$base/api/tasks" -H 'Content-Type: application/json' -d '{"projectId":'"$ph"',"title":"x","status":"banana"}')"
[ "$code" = "400" ] || fail "invalid status expected 400, got $code"
code="$(http_code -X PATCH "$base/api/projects/999999" -H 'Content-Type: application/json' -d '{"status":"active"}')"
[ "$code" = "404" ] || fail "PATCH missing project expected 404, got $code"
code="$(http_code "$base/api/tasks/999999")"
[ "$code" = "404" ] || fail "unknown task expected 404, got $code"
body="$(http_code -X POST "$base/api/projects" -H 'Content-Type: application/json' -d '{"name":" "}')"
[ "$body" = "400" ] || fail "blank-name project expected 400, got $body"

step "persistence survivor record before restart"
sp="$(curl -s -X POST "$base/api/projects" -H 'Content-Type: application/json' -d "{\"name\":\"$survivor\"}")"
sp_id="$(echo "$sp" | jq -er .id)" || fail "create survivor project: $sp"
curl -s -X POST "$base/api/tasks" -H 'Content-Type: application/json' \
    -d "{\"projectId\":$sp_id,\"title\":\"survivor-task\"}" | jq -e '.title == "survivor-task"' >/dev/null \
    || fail "create survivor task"

step "graceful SIGTERM shutdown"
stop_graceful

step "restart on the same persistent database"
start_app "$PORT" "$DATA_DIR" "jdbc:sqlite:$DATA_DIR/taskboard.db" "$log"
wait_up "$base" || fail "app did not become ready after restart"
http_body "$base/api/projects?q=$survivor" | jq -e 'any(.[]; .name == "'"$survivor"'")' >/dev/null \
    || fail "survivor project lost after restart"
http_body "$base/api/tasks?q=survivor-task" | jq -e 'any(.[]; .title == "survivor-task")' >/dev/null \
    || fail "survivor task lost after restart"
stop_graceful

step "readiness must drop when the database is unavailable (liveness stays up)"
bad_log="$WORK/bad.log"
start_app "$BAD_PORT" "$DATA_DIR" "jdbc:sqlite:$BLOCKED/not-a-directory/sub/taskboard.db" "$bad_log"
tries=0
ok=0
while [ "$tries" -lt 90 ]; do
    live="$(http_code "http://127.0.0.1:$BAD_PORT/actuator/health/liveness")"
    ready="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "http://127.0.0.1:$BAD_PORT/actuator/health/readiness")"
    if [ "$ready" = "503" ] && [ "$live" = "200" ]; then ok=1; break; fi
    sleep 1
    tries=$((tries + 1))
done
[ "$ok" = "1" ] || fail "expected liveness 200 + readiness 503 with unavailable DB (got live=$live ready=$ready)"
ready_body="$(curl -s --max-time 15 "http://127.0.0.1:$BAD_PORT/actuator/health/readiness")"
echo "$ready_body" | jq -e '.status == "DOWN"' >/dev/null || fail "readiness body not DOWN: $ready_body"
[ "$(http_code "http://127.0.0.1:$BAD_PORT/actuator/health/liveness")" = "200" ] || fail "liveness dropped while DB down"
stop_graceful "$bad_log"

step "sqlite file persisted on disk"
[ -f "$DATA_DIR/taskboard.db" ] || fail "no sqlite file at $DATA_DIR/taskboard.db"
count="$(sqlite3 "$DATA_DIR/taskboard.db" "SELECT COUNT(*) FROM project WHERE name = '$survivor';")"
[ "$count" = "1" ] || fail "sqlite3 check: survivor project count=$count"

step "smoke passed"
echo "port: $PORT"
echo "release marker: $(cat "$ROOT"/VERSION)"
echo "sqlite: $(sqlite3 --version | cut -d' ' -f1)"
echo "java: $($JAVA -version 2>&1 | head -n1)"
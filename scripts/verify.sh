#!/usr/bin/env bash
# Full verification for the Spring Boot taskboard fixture:
#
#   1. scripts/build.sh            -> writes the VERSION release marker
#   2. clean Maven build + tests    -> mvn -B clean verify (downloads pinned
#                                      deps from the Spring Boot BOM; runs the
#                                      MockMvc integration suite)
#   3. scripts/smoke.sh             -> real production process (java -jar):
#                                      health/readiness, CRUD, search/filter,
#                                      negatives, release marker, graceful
#                                      shutdown, restart persistence,
#                                      DB-unavailable readiness
#
# Usage:
#   scripts/verify.sh
#
# Exit codes: 0 = all checks passed, nonzero = a check failed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MVN="${MVN:-$(command -v mvn)}"
[ -n "$MVN" ] || { echo "mvn executable not found" >&2; exit 1; }

step() { printf '\n=== %s ===\n' "$*"; }

step "build release marker"
"$ROOT/scripts/build.sh"

step "clean Maven build + integration tests (spring-boot:3.5.16, java 21)"
"$MVN" -B -ntp clean verify
"$MVN" -B -ntp surefire-report:report-only >/dev/null 2>&1 || true

step "production smoke: real process + CRUD + negatives + persistence + readiness"
"$ROOT/scripts/smoke.sh"

step "verification complete (all steps passed)"
printf '%s\n' "mvn: $($MVN --version | head -n1)"
printf '%s\n' "java: $(java -version 2>&1 | head -n1)"
printf '%s\n' "release marker: $(cat "$ROOT/VERSION")"
printf '%s\n' "jar: $(ls -lh "$ROOT/target/taskboard.jar" | awk '{print $5, $9}')"
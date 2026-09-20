#!/usr/bin/env bash
# Release-marker build: writes the non-sensitive VERSION file used at runtime.
#
#   scripts/build.sh            # uses git SHA when available
#   BUILD_MARKER=mybuild scripts/build.sh   # explicit override
#
# The output VERSION file is a build artifact (gitignored) and is copied into
# the packaged jar by maven-resources-plugin, so the marker served by
# /api/meta always reflects the deployed revision.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

marker="${BUILD_MARKER:-}"
if [ -z "$marker" ] && command -v git >/dev/null 2>&1; then
    top="$(git -C "$ROOT" rev-parse --show-toplevel 2>/dev/null || true)"
    if [ -n "$top" ] && [ "$top" = "$ROOT" ] \
        && git -C "$ROOT" rev-parse --verify HEAD >/dev/null 2>&1; then
        marker="$(git -C "$ROOT" rev-parse --short HEAD)"
    fi
fi
if [ -z "$marker" ]; then
    marker="build-$(date +%Y%m%d%H%M%S)-$RANDOM"
fi

printf '%s\n' "$marker" > "$ROOT/VERSION"
printf 'release marker: %s\n' "$marker"
#!/usr/bin/env bash
set -euo pipefail
GRADLE_VERSION="8.10.2"
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
CACHE="${HOME}/.gradle/wrapper/dists/cineview-gradle-${GRADLE_VERSION}"
mkdir -p "$CACHE"
ZIP="$CACHE/gradle-${GRADLE_VERSION}-bin.zip"
if [ ! -f "$ZIP" ]; then
  command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 1; }
  curl -fL --retry 3 --connect-timeout 15 -o "$ZIP" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
fi
DIST="$CACHE/gradle-${GRADLE_VERSION}"
if [ ! -x "$DIST/bin/gradle" ]; then
  rm -rf "$DIST"
  unzip -q "$ZIP" -d "$CACHE"
fi
exec "$DIST/bin/gradle" "$@"

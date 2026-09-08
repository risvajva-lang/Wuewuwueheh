#!/usr/bin/env bash
set -euo pipefail
test -f settings.gradle.kts
test -f build.gradle.kts
test -f app/build.gradle.kts
test -f app/src/main/AndroidManifest.xml
grep -R -n "com.cineview.app.R" app/src/main/java && exit 1 || true
grep -R -n "runBlocking" app/src/main/java && exit 1 || true
grep -R -n "signingInfo\.apkContentsSigners\.first()" app/src/main/java && exit 1 || true
grep -R -n "signatures\.first()" app/src/main/java && exit 1 || true
if command -v node >/dev/null 2>&1; then
  while IFS= read -r -d '' f; do node --check "$f"; done < <(find app/src/main/assets/web -name '*.js' -print0)
fi
echo "Source audit passed."

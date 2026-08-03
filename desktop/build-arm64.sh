#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DESKTOP="$ROOT/desktop"
RUNTIME="$DESKTOP/runtime"

if [[ "$(uname -m)" != "arm64" ]]; then
  echo "This release build supports Apple Silicon arm64 only." >&2
  exit 2
fi
command -v java >/dev/null || { echo "JDK 17 is required." >&2; exit 2; }
command -v npm >/dev/null || { echo "npm is required." >&2; exit 2; }

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "/opt/homebrew/opt/openjdk@17" ]]; then
    JAVA_HOME="/opt/homebrew/opt/openjdk@17"
  else
    JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  fi
fi
[[ -x "$JAVA_HOME/bin/java" && -x "$JAVA_HOME/bin/jlink" ]] || {
  echo "JDK 17 with jlink is required. Set JAVA_HOME to an installed JDK 17." >&2
  exit 2
}
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

[[ -x "$RUNTIME/postgresql/bin/initdb" ]] || {
  echo "PostgreSQL runtime is missing. Run desktop/prepare-postgresql-arm64.sh first." >&2
  exit 3
}

cd "$ROOT/frontend"
npm ci
VITE_API_BASE_URL=http://127.0.0.1:8080 npm run build -- --mode desktop

cd "$ROOT/backend-java"
mvn -B -DskipTests package

rm -rf "$RUNTIME/jre"
mkdir -p "$RUNTIME"
jlink \
  --module-path "$JAVA_HOME/jmods" \
  --add-modules java.base,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.net.http,java.security.jgss,java.sql,java.xml,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.management,jdk.unsupported \
  --strip-debug --no-man-pages --no-header-files --compress=2 --output "$RUNTIME/jre"

"$RUNTIME/jre/bin/java" -version >/dev/null 2>&1 || { echo "JRE_INVALID: bundled Java could not start." >&2; exit 4; }
"$RUNTIME/jre/bin/java" --list-modules | grep -q '^java.instrument@' || {
  echo "JRE_INVALID: java.instrument module is missing." >&2
  exit 4
}

for binary in "$RUNTIME/postgresql/bin/initdb" "$RUNTIME/postgresql/bin/pg_ctl" "$RUNTIME/postgresql/bin/createdb" "$RUNTIME/postgresql/bin/psql" "$RUNTIME/postgresql/bin/postgres"; do
  [[ -x "$binary" ]] || { echo "Missing bundled PostgreSQL/pgvector runtime: $binary" >&2; exit 3; }
done
[[ -f "$RUNTIME/postgresql/lib/postgresql/vector.dylib" ]] || {
  echo "POSTGRES_RUNTIME_INVALID: bundled pgvector extension is missing." >&2
  exit 3
}

# Run the exact bundled PostgreSQL and JRE before packaging. This catches a
# broken runtime while the build still has enough context to explain the stage.
SMOKE_ROOT="$(mktemp -d /private/tmp/codereviewx-build-smoke.XXXXXX)"
SMOKE_PG="$SMOKE_ROOT/postgres"
SMOKE_LOG="$SMOKE_ROOT/backend.log"
SMOKE_PORT=55439
SMOKE_BACKEND_PORT=18080
SMOKE_POSTGRES_PID=""
SMOKE_BACKEND_PID=""
cleanup_smoke() {
  if [[ -n "$SMOKE_BACKEND_PID" ]]; then kill "$SMOKE_BACKEND_PID" 2>/dev/null || true; fi
  if [[ -n "$SMOKE_POSTGRES_PID" ]]; then kill "$SMOKE_POSTGRES_PID" 2>/dev/null || true; fi
  rm -rf "$SMOKE_ROOT"
}
trap cleanup_smoke EXIT

"$RUNTIME/postgresql/bin/initdb" -D "$SMOKE_PG" --encoding=UTF8 --locale=C >/dev/null 2>&1 || {
  echo "POSTGRES_INIT_FAILED: bundled PostgreSQL could not initialize." >&2
  exit 5
}
"$RUNTIME/postgresql/bin/postgres" -D "$SMOKE_PG" -p "$SMOKE_PORT" >"$SMOKE_ROOT/postgres.log" 2>&1 &
SMOKE_POSTGRES_PID=$!
for attempt in {1..60}; do
  if "$RUNTIME/postgresql/bin/psql" -p "$SMOKE_PORT" -d postgres -c 'SELECT 1' >/dev/null 2>&1; then break; fi
  if ! kill -0 "$SMOKE_POSTGRES_PID" 2>/dev/null; then
    echo "POSTGRES_RUNTIME_INVALID: bundled PostgreSQL exited during smoke test." >&2
    sed -n '1,80p' "$SMOKE_ROOT/postgres.log" >&2
    exit 5
  fi
  sleep 0.5
  if [[ "$attempt" == 60 ]]; then echo "POSTGRES_RUNTIME_INVALID: bundled PostgreSQL did not become ready." >&2; exit 5; fi
done
"$RUNTIME/postgresql/bin/psql" -p "$SMOKE_PORT" -d postgres -c "CREATE ROLE codereviewx LOGIN;" >/dev/null 2>&1 || true
"$RUNTIME/postgresql/bin/createdb" -p "$SMOKE_PORT" -O codereviewx codereviewx >/dev/null 2>&1 || true
"$RUNTIME/postgresql/bin/psql" -p "$SMOKE_PORT" -d codereviewx -c 'CREATE EXTENSION IF NOT EXISTS vector' >/dev/null 2>&1 || {
  echo "POSTGRES_RUNTIME_INVALID: bundled pgvector extension could not be created." >&2
  exit 5
}

SPRING_PROFILES_ACTIVE=postgres SERVER_ADDRESS=127.0.0.1 BACKEND_PORT="$SMOKE_BACKEND_PORT" \
POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$SMOKE_PORT" POSTGRES_DB=codereviewx POSTGRES_USER=codereviewx POSTGRES_PASSWORD='' \
RAG_ENABLED=false MODEL_PROVIDER=custom MODEL_BASE_URL=https://example.invalid/v1 MODEL_NAME=smoke MODEL_API_KEY='' \
GITHUB_TOKEN='' "$RUNTIME/jre/bin/java" -jar "$ROOT/backend-java/target/backend-java-0.0.1-SNAPSHOT.jar" >"$SMOKE_LOG" 2>&1 &
SMOKE_BACKEND_PID=$!
for attempt in {1..120}; do
  if curl --fail --silent "http://127.0.0.1:${SMOKE_BACKEND_PORT}/actuator/health/liveness" >/dev/null 2>&1; then break; fi
  if ! kill -0 "$SMOKE_BACKEND_PID" 2>/dev/null; then
    echo "BACKEND_START_FAILED: bundled Backend exited during smoke test." >&2
    sed -n '1,120p' "$SMOKE_LOG" >&2
    exit 6
  fi
  sleep 0.5
  if [[ "$attempt" == 120 ]]; then
    echo "HEALTH_TIMEOUT: bundled Backend health check timed out." >&2
    sed -n '1,120p' "$SMOKE_LOG" >&2
    exit 6
  fi
done
echo "Bundled runtime smoke test passed: JRE, PostgreSQL, pgvector, Backend health."
trap - EXIT
cleanup_smoke

cd "$DESKTOP"
npm ci
npm run dist

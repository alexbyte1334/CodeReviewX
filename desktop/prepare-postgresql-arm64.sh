#!/usr/bin/env bash
set -euo pipefail

# Prepare the redistributable PostgreSQL + pgvector tree used by the DMG.
# This is intentionally a build-time step; PostgreSQL is not installed on the
# user's Mac. Homebrew is only required on the release/build machine.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/desktop/runtime/postgresql"
POSTGRES_FORMULA="${POSTGRES_FORMULA:-postgresql@17}"
PG_PREFIX="${POSTGRES_PREFIX:-$(brew --prefix "$POSTGRES_FORMULA" 2>/dev/null || true)}"
VECTOR_PREFIX="${PGVECTOR_PREFIX:-$(brew --prefix pgvector 2>/dev/null || true)}"

[[ -n "$PG_PREFIX" && -d "$PG_PREFIX" ]] || { echo "Set POSTGRES_PREFIX to an arm64 PostgreSQL installation." >&2; exit 2; }
[[ -n "$VECTOR_PREFIX" && -d "$VECTOR_PREFIX" ]] || { echo "Set PGVECTOR_PREFIX to an arm64 pgvector installation." >&2; exit 2; }

PG_MAJOR="$(basename "$PG_PREFIX" | sed -E 's/.*@([0-9]+)$/\1/')"
[[ "$PG_MAJOR" =~ ^[0-9]+$ ]] || { echo "Could not determine PostgreSQL major version from $PG_PREFIX." >&2; exit 2; }
PG_SHARE="$PG_PREFIX/share/postgresql@$PG_MAJOR"
[[ -d "$PG_SHARE" ]] || PG_SHARE="$PG_PREFIX/share/postgresql"
VECTOR_SHARE="$VECTOR_PREFIX/share/postgresql@$PG_MAJOR"
VECTOR_LIB="$VECTOR_PREFIX/lib/postgresql@$PG_MAJOR/vector.dylib"
[[ -d "$PG_SHARE" && -d "$VECTOR_SHARE" && -f "$VECTOR_LIB" ]] || {
  echo "pgvector has no matching PostgreSQL $PG_MAJOR runtime. Install a supported PostgreSQL formula or set POSTGRES_PREFIX." >&2
  exit 2
}

rm -rf "$OUT"
mkdir -p "$OUT/bin" "$OUT/lib" "$OUT/share"
mkdir -p "$OUT/lib/postgresql"
cp "$PG_PREFIX/bin/initdb" "$PG_PREFIX/bin/pg_ctl" "$PG_PREFIX/bin/createdb" "$PG_PREFIX/bin/psql" "$PG_PREFIX/bin/postgres" "$OUT/bin/"
cp "$PG_PREFIX/lib/postgresql/"*.dylib "$OUT/lib/"
for dependency in gettext zstd lz4 openssl@3 krb5 icu4c@78; do
  dependency_prefix="$(brew --prefix "$dependency" 2>/dev/null || true)"
  [[ -d "$dependency_prefix/lib" ]] && cp "$dependency_prefix/lib/"*.dylib "$OUT/lib/" 2>/dev/null || true
done
cp "$PG_SHARE/postgresql.conf.sample" "$OUT/share/"
cp -R "$PG_SHARE" "$OUT/share/postgresql"
cp "$VECTOR_LIB" "$OUT/lib/"
cp "$VECTOR_LIB" "$OUT/lib/postgresql/vector.dylib"
mkdir -p "$OUT/share/extension"
cp "$VECTOR_SHARE/extension/vector.control" "$OUT/share/extension/"
cp "$VECTOR_SHARE/extension/vector--"*.sql "$OUT/share/extension/"

# Homebrew binaries contain absolute dependency paths. Rewrite those paths so
# the copied runtime remains self-contained on a Mac without Homebrew.
for binary in "$OUT/bin/"* "$OUT/lib/"*.dylib; do
  [[ -f "$binary" ]] || continue
  while IFS= read -r dependency_path; do
    dependency_name="$(basename "$dependency_path")"
    [[ -f "$OUT/lib/$dependency_name" ]] || continue
    install_name_tool -change "$dependency_path" "@loader_path/../lib/$dependency_name" "$binary"
  done < <(otool -L "$binary" | awk '/^\t\/opt\/homebrew\// {print $1}')
done

echo "Prepared $OUT"

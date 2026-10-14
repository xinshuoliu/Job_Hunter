#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh
load_db_key

database="${1:-jobs.db}"
[ -f "$database" ] || { echo "Database '$database' not found" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

scripts/ci/encrypt-db.sh "$database" "$work/jobs.db.enc"

gh release delete db-seed --yes --cleanup-tag >/dev/null 2>&1 || true
gh release create db-seed "$work/jobs.db.enc" \
  --title "Database seed" \
  --notes "Encrypted Job Hunter database used once to seed the scheduled workflow. Safe to delete after the first run." \
  --prerelease >/dev/null
echo "Uploaded an encrypted copy of $database as the db-seed release"

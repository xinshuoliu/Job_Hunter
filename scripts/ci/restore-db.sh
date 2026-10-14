#!/usr/bin/env bash
set -euo pipefail

target="${1:-data/jobs.db}"
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"
: "${JOBHUNTER_DB_KEY:?JOBHUNTER_DB_KEY is not set}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$(dirname "$target")"

decrypt() {
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:JOBHUNTER_DB_KEY -in "$1" -out "$target"
}

artifact_id="$(gh api "repos/$repo/actions/artifacts?name=jobs-db&per_page=20" \
  -q '[.artifacts[] | select(.expired | not)] | sort_by(.created_at) | last | .id // empty')"

if [ -n "$artifact_id" ]; then
  gh api "repos/$repo/actions/artifacts/$artifact_id/zip" > "$work/jobs-db.zip"
  unzip -q "$work/jobs-db.zip" -d "$work"
  decrypt "$work/jobs.db.enc"
  echo "Restored database from artifact $artifact_id"
elif gh release download db-seed --repo "$repo" --pattern jobs.db.enc --dir "$work" 2>/dev/null; then
  decrypt "$work/jobs.db.enc"
  echo "Restored database from the db-seed release"
elif [ "${JOBHUNTER_ALLOW_EMPTY_DB:-false}" = "true" ]; then
  echo "No saved database found, starting empty"
else
  echo "No saved database found. Seed one with scripts/seed-db.sh, or set JOBHUNTER_ALLOW_EMPTY_DB=true" >&2
  echo "to start empty (every current posting will be announced again)." >&2
  exit 1
fi

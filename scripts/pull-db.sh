#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh
load_db_key

target="${1:-jobs.db}"
repo="$(repo_name)"

if [ -f "$target" ]; then
  backup="$target.local-$(date +%Y%m%d-%H%M%S).bak"
  cp "$target" "$backup"
  echo "Backed up the current $target to $backup"
fi

GITHUB_REPOSITORY="$repo" scripts/ci/restore-db.sh "$target"
echo "Pulled the latest database from GitHub Actions into $target"

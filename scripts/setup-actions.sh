#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

config="${1:-config.yml}"
[ -f "$config" ] || { echo "Config file '$config' not found" >&2; exit 1; }

if [ ! -f "$KEY_FILE" ]; then
  openssl rand -base64 32 > "$KEY_FILE"
  echo "Created a new database key in $KEY_FILE (keep it; it decrypts the saved database)"
fi
load_db_key

ci_config="$(mktemp)"
trap 'rm -f "$ci_config"' EXIT

awk '
  /^[^[:space:]#][^:]*:/ { section = $1; sub(":", "", section); sub_section = "" }
  /^  [^[:space:]#][^:]*:/ { sub_section = $1; sub(":", "", sub_section) }
  section == "database" && /^  path:/ { print "  path: \"data/jobs.db\""; next }
  section == "notifications" && sub_section == "console" && /^    enabled:/ { print "    enabled: false"; next }
  { print }
' "$config" > "$ci_config"

grep -q 'path: "data/jobs.db"' "$ci_config" || printf '\ndatabase:\n  path: "data/jobs.db"\n' >> "$ci_config"

gh secret set JOBHUNTER_CONFIG < "$ci_config"
printf '%s' "$JOBHUNTER_DB_KEY" | gh secret set JOBHUNTER_DB_KEY
echo "Uploaded the JOBHUNTER_CONFIG and JOBHUNTER_DB_KEY secrets to $(repo_name)"

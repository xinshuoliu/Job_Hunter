#!/usr/bin/env bash

KEY_FILE="${JOBHUNTER_KEY_FILE:-.jobhunter-db.key}"

load_db_key() {
  if [ -z "${JOBHUNTER_DB_KEY:-}" ]; then
    if [ ! -f "$KEY_FILE" ]; then
      echo "No database key found. Run scripts/setup-actions.sh first, or set JOBHUNTER_DB_KEY." >&2
      exit 1
    fi
    JOBHUNTER_DB_KEY="$(tr -d '\r\n' < "$KEY_FILE")"
  fi
  export JOBHUNTER_DB_KEY
}

repo_name() {
  gh repo view --json nameWithOwner -q .nameWithOwner
}

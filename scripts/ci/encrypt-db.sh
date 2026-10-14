#!/usr/bin/env bash
set -euo pipefail

source_db="${1:?usage: encrypt-db.sh <database> <output>}"
output="${2:?usage: encrypt-db.sh <database> <output>}"
: "${JOBHUNTER_DB_KEY:?JOBHUNTER_DB_KEY is not set}"

openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:JOBHUNTER_DB_KEY -in "$source_db" -out "$output"

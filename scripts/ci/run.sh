#!/usr/bin/env bash
set -euo pipefail

jar="$(ls target/job-hunter-*.jar | grep -v original | head -n 1)"
read -r -a args <<< "${COMMAND:-once}"

case "${args[0]:-once}" in
  once|stats)
    java -jar "$jar" --config config.yml "${args[@]}"
    ;;
  mark)
    if java -jar "$jar" --config config.yml "${args[@]}" > /dev/null; then
      echo "Status updated."
    else
      echo "Could not update the status. Check the id with 'list' locally and the status name." >&2
      exit 1
    fi
    ;;
  *)
    echo "Unsupported command '${args[0]}'. Use once, stats, or mark <id> <status>." >&2
    exit 2
    ;;
esac

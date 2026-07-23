#!/usr/bin/env bash

set -euo pipefail

PIPELINE_DATE=""
INDEX_NAME=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --index-name)
      INDEX_NAME="${2:-}"
      shift 2
      ;;
    -*)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
    *)
      if [[ -z "$PIPELINE_DATE" ]]; then
        PIPELINE_DATE="$1"
      else
        echo "Unexpected argument: $1" >&2
        exit 1
      fi
      shift
      ;;
  esac
done

if [[ -z "$PIPELINE_DATE" || -z "$INDEX_NAME" ]]; then
  echo "Usage: $0 <pipeline_date> --index-name <index_name>" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PROJECT_DIR/../../.." && pwd)"

cd "$PROJECT_DIR"

"$SCRIPT_DIR/fetch_es_env.sh" "$PIPELINE_DATE" --index-name "$INDEX_NAME" --output-env-file ".env.local"

"$REPO_ROOT/builds/run_sbt_task_in_docker.sh" "project transformer_calm" "stage"

docker compose -f local.docker-compose.yml up -d --build localstack localstack-setup calm-transformer

echo "Local CALM transformer is running."
echo "Next: enqueue IDs from a file with run_local/enqueue_calm_ids.sh <ids_file>"
echo "Streaming logs from calm-transformer (Ctrl+C to stop log tail; services keep running)..."

docker compose -f local.docker-compose.yml logs -f calm-transformer

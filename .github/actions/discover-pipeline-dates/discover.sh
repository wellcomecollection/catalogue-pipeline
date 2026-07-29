#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail

TF_DIR="${1:-}"

if [[ -z "$TF_DIR" ]]; then
  echo "Usage: discover.sh <terraform_dir>" >&2
  exit 1
fi

if [[ ! -d "$TF_DIR" ]]; then
  echo "Terraform directory not found: $TF_DIR" >&2
  exit 1
fi

# Dated pipeline directories e.g., YYYY-MM-DD*, most recent first
ALL_DATES=$(ls -1 "$TF_DIR" | grep -E '^[0-9]{4}-[0-9]{2}-[0-9]{2}' | sort -r || true)
if [[ -z "${ALL_DATES}" ]]; then
  echo "No dated pipeline directories found in $TF_DIR" >&2
  exit 1
fi

PIPELINE_DATE=$(head -n1 <<< "$ALL_DATES")

# deploy_settings.json chooses between deploying every dated pipeline
# and only the most recent one (the default).
SETTINGS_FILE="$TF_DIR/deploy_settings.json"
DEPLOY_ALL="false"
if [[ -f "$SETTINGS_FILE" ]]; then
  DEPLOY_ALL=$(jq -r '.deploy_all_pipelines // false' "$SETTINGS_FILE")
fi

if [[ "$DEPLOY_ALL" == "true" ]]; then
  PIPELINE_DATES=$(jq -cRn '[inputs]' <<< "$ALL_DATES")
else
  PIPELINE_DATES=$(jq -cn --arg date "$PIPELINE_DATE" '[$date]')
fi

echo "pipeline_date=${PIPELINE_DATE}" >> "$GITHUB_OUTPUT"
echo "pipeline_dates=${PIPELINE_DATES}" >> "$GITHUB_OUTPUT"

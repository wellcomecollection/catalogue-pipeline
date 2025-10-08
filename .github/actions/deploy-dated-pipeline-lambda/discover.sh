#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail

TF_DIR="${1:-}"
LAMBDA_SUFFIX="${2:-}"

if [[ -z "$TF_DIR" || -z "$LAMBDA_SUFFIX" ]]; then
  echo "Usage: discover.sh <terraform_dir> <lambda_suffix>" >&2
  exit 1
fi

if [[ ! -d "$TF_DIR" ]]; then
  echo "Terraform directory not found: $TF_DIR" >&2
  exit 1
fi

# Find most recent (lexicographically greatest) dated directory e.g., YYYY-MM-DD*
PIPELINE_DATE=$(ls -1 "$TF_DIR" | grep -E '^[0-9]{4}-[0-9]{2}-[0-9]{2}' | sort -r | head -n1 || true)
if [[ -z "${PIPELINE_DATE}" ]]; then
  echo "No dated pipeline directories found in $TF_DIR" >&2
  exit 1
fi

LAMBDA_NAME="catalogue-${PIPELINE_DATE}-${LAMBDA_SUFFIX}"

echo "pipeline_date=${PIPELINE_DATE}" >> "$GITHUB_OUTPUT"
echo "lambda_name=${LAMBDA_NAME}" >> "$GITHUB_OUTPUT"

#!/usr/bin/env bash

set -o errexit
set -o nounset

ENVIRONMENT=$(aws lambda get-function-configuration \
  --function-name sierra-adapter-20200604-sierra_progress_reporter \
  | jq .Environment.Variables
)

export BUCKET=$(echo "$ENVIRONMENT" | jq -r .BUCKET)
export SKIPPED_RESOURCE_TYPES=$(echo "$ENVIRONMENT" | jq -r '.SKIPPED_RESOURCE_TYPES // ""')

python3 sierra_progress_reporter.py

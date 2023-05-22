#!/usr/bin/env bash

set -o errexit
set -o nounset

ENVIRONMENT=$(aws lambda get-function-configuration \
  --function-name sierra-adapter-20200604-sierra_progress_reporter \
  | jq .Environment.Variables
)

export BUCKET=$(echo "$ENVIRONMENT" | jq -r .BUCKET)

python3 src/sierra_progress_reporter.py

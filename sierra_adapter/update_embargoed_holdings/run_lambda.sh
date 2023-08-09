#!/usr/bin/env bash

set -o errexit
set -o nounset

ENVIRONMENT=$(aws lambda get-function-configuration \
  --function-name "update_embargoed_holdings" \
  | jq .Environment.Variables
)

export HOLDINGS_READER_TOPIC_ARN=$(echo "$ENVIRONMENT" | jq -r .HOLDINGS_READER_TOPIC_ARN)

python3 update_embargoed_holdings.py

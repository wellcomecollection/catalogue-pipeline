#!/usr/bin/env bash
# This allows you to run the Sierra reader Lambda locally.
#
# It fetches certain configuration options from the environment, so

set -o errexit
set -o nounset

if (( $# != 2 ))
then
  echo "Usage: $0 RESOURCE_TYPE WINDOW" >&2
  exit 1
fi

export RESOURCE_TYPE="$1"
WINDOW="$2"

ENVIRONMENT=$(aws lambda get-function-configuration \
  --function-name "sierra-adapter-20200604-sierra-reader-$RESOURCE_TYPE" \
  | jq .Environment.Variables
)

export SIERRA_FIELDS=$(echo "$ENVIRONMENT" | jq -r .SIERRA_FIELDS)
export READER_BUCKET=$(echo "$ENVIRONMENT" | jq -r .READER_BUCKET)
export TOPIC_ARN=$(echo "$ENVIRONMENT" | jq -r .TOPIC_ARN)

python3 sierra_reader.py "$WINDOW"

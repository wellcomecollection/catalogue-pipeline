#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail

PIPELINE_DATE="${1:-}"
LAMBDA_SUFFIX="${2:-}"

if [[ -z "$PIPELINE_DATE" || -z "$LAMBDA_SUFFIX" ]]; then
	echo "Usage: discover.sh <pipeline_date> <lambda_suffix>" >&2
	exit 1
fi

LAMBDA_NAME="catalogue-${PIPELINE_DATE}-${LAMBDA_SUFFIX}"

echo "lambda_name=${LAMBDA_NAME}" >> "$GITHUB_OUTPUT"

#!/usr/bin/env bash

set -o errexit
set -o nounset

ROOT=$(git rev-parse --show-toplevel)
SCRIPT_DIR="$ROOT/pipeline/terraform/scripts"

# The ec provider needs the Elastic Cloud API key, held in Secrets Manager.
EC_API_KEY=$(. "$SCRIPT_DIR/get_elastic_cloud_api_key.sh")

EC_API_KEY=$EC_API_KEY terraform "$@"

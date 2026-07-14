#!/usr/bin/env bash

set -o errexit
set -o nounset

ROOT=$(git rev-parse --show-toplevel)
SCRIPT_DIR="$ROOT/pipeline/terraform/scripts"

# Get the Elastic Cloud API key, which we need for the EC provider
EC_API_KEY=$(. "$SCRIPT_DIR/get_elastic_cloud_api_key.sh")

# Now run Terraform itself, passing any arguments directly to the underlying
# Terraform binary.
EC_API_KEY=$EC_API_KEY terraform "$@"

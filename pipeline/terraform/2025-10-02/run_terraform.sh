#!/usr/bin/env bash

set -o errexit
set -o nounset

# Get the path to the current directory, which we can use to find the
# 'scripts' folder and the date of the current pipeline.
#
# https://stackoverflow.com/q/59895/1558022
THIS_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SCRIPT_DIR="$(dirname "$THIS_DIR")/scripts"

# Create the config file that tells Terraform which pipeline we're running
# in and where to store the remote state.
export PIPELINE_DATE="$(basename "$THIS_DIR")"
. $SCRIPT_DIR/create_terraform_config_file.sh

# Get the Elastic Cloud API key, which we need for the EC provider
EC_API_KEY=$(. "$SCRIPT_DIR/get_elastic_cloud_api_key.sh")

# Now run Terraform itself, passing any arguments directly to the underlying
# Terraform binary.
EC_API_KEY=$EC_API_KEY terraform "$@"

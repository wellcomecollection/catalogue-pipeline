#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <PIPELINE_DATE>"
  exit 1
fi

export PIPELINE_DATE=$1

PROJECT_NAME="relation_embedder"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"/..

# Read .template.env, substitute variables, and write to .env
envsubst < template.env > .env

# Build the project
pushd ../../..
sbt "project $PROJECT_NAME" ";stage"
popd

# Build the docker image
docker compose -f local.docker-compose.yml \
  build lambda

# Run the docker image
docker compose -f local.docker-compose.yml \
  run --rm  --service-ports lambda

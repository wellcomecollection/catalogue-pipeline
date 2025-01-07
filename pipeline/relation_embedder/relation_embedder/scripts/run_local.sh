#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <PIPELINE_DATE> [--skip-build]"
  exit 1
fi

export PIPELINE_DATE=$1
SKIP_BUILD=false
if [ "$#" -eq 2 ] && [ "$2" == "--skip-build" ]; then
  SKIP_BUILD=true
fi

PROJECT_NAME="relation_embedder"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"/..

# Read template.env, substitute variables, and write to .env
if [ -f template.env ]; then
  envsubst < template.env > .env
fi

# Build the project, skipping if requested
if [ "$SKIP_BUILD" = true ]; then
  echo "Skipping build"
else
  pushd ../../..
  sbt "project $PROJECT_NAME" ";stage"
  popd
fi

# Build the docker image
docker compose -f local.docker-compose.yml \
  build lambda

# Run the docker image
docker compose -f local.docker-compose.yml \
  run --rm  --service-ports lambda

#!/usr/bin/env bash

# Usage: scripts/run_local.sh <PROJECT_ID> [<PIPELINE_DATE>] [--skip-build]
# Example: scripts/run_local.sh relation_embedder/batcher 2021-09-01 --skip-build
#
# This script runs a project locally using docker-compose, it expects the following
# files to exist in a project directory, and that it is a scala project:
# - local.docker-compose.yml, with a target service named 'lambda'
# - template.env

set -eo pipefail

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." >/dev/null 2>&1 && pwd )"
pushd "$ROOT_DIR"

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <PROJECT_ID> [<PIPELINE_DATE>] [--skip-build]"
  exit 1
fi

# loop over args to set variables
for i in "$@"
do
case $i in
    --skip-build)
    SKIP_BUILD=true
    shift
    ;;
    *)
    if [ -z "$PROJECT_ID" ]; then
      PROJECT_ID=$1
    elif [ -z "$PIPELINE_DATE" ]; then
      PIPELINE_DATE=$1
    fi
    shift
    ;;
esac
done

if [ -z "$PIPELINE_DATE" ]; then
  echo "Fetching latest pipeline date from API ..."
  echo ""
  PIPELINE_DATE=$( \
  curl -s https://api.wellcomecollection.org/catalogue/v2/_elasticConfig | \
    jq -r '.worksIndex' | \
    sed "s/^works-indexed-//" \
  )
fi

echo "Running $PROJECT_ID with pipeline: $PIPELINE_DATE"

PIPELINE_DIR="pipeline/$PROJECT_ID"
PROJECT_DIR="$ROOT_DIR/$PIPELINE_DIR"
SBT_PROJECT_ID=$(echo "$PROJECT_ID" | rev | cut -d'/' -f1 | rev)

# Check local.docker-compose.yml exists in project directory
if [ ! -f "$PROJECT_DIR/local.docker-compose.yml" ]; then
  echo "local.docker-compose.yml not found in $PIPELINE_DIR"
  exit 1
fi

# Build the project, skipping if requested
if [ "$SKIP_BUILD" = true ]; then
  echo "Skipping build"
else
  sbt "project $SBT_PROJECT_ID" ";stage"
fi

pushd "$PROJECT_DIR"

# Read template.env, substitute variables, and write to .env
if [ -f template.env ]; then
  echo "Substituting variables in template.env, creating .env"
  envsubst < template.env > .env
fi

# Build the docker image
docker compose -f local.docker-compose.yml \
  build lambda

# Run the docker image
docker compose -f local.docker-compose.yml  up


#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o verbose

ROOT=$(git rev-parse --show-toplevel)
BUILDS_DIR="$ROOT/builds"

for service in feature_inferrer palette_inferrer aspect_ratio_inferrer
do
  docker build \
    --file "$ROOT/pipeline/inferrer/$service/Dockerfile" \
    --tag "$PROJECT" \
    "$ROOT/pipeline/inferrer/$service"
done

$BUILDS_DIR/run_sbt_task_in_docker.sh \
  "project inference_manager" \
  ";dockerComposeUp;testOnly **.integration.*;dockerComposeStop"

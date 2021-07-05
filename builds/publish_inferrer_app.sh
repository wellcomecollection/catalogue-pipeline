#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o verbose

PROJECT="$1"

CURRENT_COMMIT=$(git rev-parse HEAD)

ROOT=$(git rev-parse --show-toplevel)
BUILDS_DIR="$ROOT/builds"

docker build \
  --file "$ROOT/pipeline/inferrer/$PROJECT/Dockerfile" \
  --tag "$PROJECT:$CURRENT_COMMIT" \
  "$ROOT/pipeline/inferrer/$PROJECT"

mkdir -p "$ROOT/.releases"
echo "$CURRENT_COMMIT" >> "$ROOT/.releases/$PROJECT"

$BUILDS_DIR/publish_image_with_weco_deploy.sh "$PROJECT"

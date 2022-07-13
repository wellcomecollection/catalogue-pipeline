#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o verbose

PROJECT="$1"

ROOT=$(git rev-parse --show-toplevel)
BUILDS_DIR="$ROOT/builds"

case "$PROJECT" in
  "aspect_ratio_inferrer" | "feature_inferrer" | "palette_inferrer")
    $BUILDS_DIR/publish_inferrer_app.sh "$PROJECT"
    ;;

  *)
    $BUILDS_DIR/publish_sbt_app.sh "$PROJECT"
    ;;
esac

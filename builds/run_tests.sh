#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o verbose

PROJECT="$1"

ROOT=$(git rev-parse --show-toplevel)
BUILDS_DIR="$ROOT/builds"

case "$PROJECT" in
  "aspect_ratio_inferrer" | "feature_inferrer" | "palette_inferrer")
    $BUILDS_DIR/run_inferrer_tests.sh "$PROJECT"
    ;;

  "inference_manager")
    $BUILDS_DIR/run_inference_manager_tests.sh
    ;;
  *)
    $BUILDS_DIR/run_sbt_tests.sh "$PROJECT"
    ;;
esac

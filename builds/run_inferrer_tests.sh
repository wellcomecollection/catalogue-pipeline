#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o verbose

PROJECT="$1"

PROJECT_DIRECTORY=$(jq -r .folder ".sbt_metadata/$PROJECT.json")

ROOT=$(git rev-parse --show-toplevel)
BUILDS_DIR="$ROOT/builds"

$BUILDS_DIR/run_python_tests.sh "$PROJECT_DIRECTORY"
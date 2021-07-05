#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o verbose

PROJECT="$1"

ROOT=$(git rev-parse --show-toplevel)
BUILDS_DIR="$ROOT/builds"

$BUILDS_DIR/run_python_tests.sh pipeline/inferrer/$PROJECT
$BUILDS_DIR/run_inference_manager_integration_tests.sh

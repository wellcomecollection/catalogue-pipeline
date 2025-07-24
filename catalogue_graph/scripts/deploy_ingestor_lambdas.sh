#!/usr/bin/env bash

# Convenience script to build and deploy all lambdas

set -o errexit
set -o nounset

# set ROOT to the root of the project
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

LAMBDAS=(
  "catalogue-graph-ingestor-loader"
  "catalogue-graph-ingestor-loader-monitor"
  "catalogue-graph-ingestor-trigger-monitor"
  "catalogue-graph-ingestor-indexer"
  "catalogue-graph-ingestor-trigger"
)

export AWS_PROFILE=platform-developer

pushd $ROOT/..

$ROOT/scripts/build.sh  --push --skip-container-build

for LAMBDA in "${LAMBDAS[@]}"; do
  echo "Deploying $LAMBDA"
  ./catalogue_graph/scripts/deploy_lambda_zip.sh $LAMBDA
done

#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o verbose

PROJECT="$1"

ROOT=$(git rev-parse --show-toplevel)

cd "$ROOT"/"$PROJECT"

docker-compose run app py.test

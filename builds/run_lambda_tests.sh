#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o verbose

PROJECT="$1"

ROOT=$(git rev-parse --show-toplevel)

docker run --rm --tty \
  --volume "$ROOT:$ROOT" \
  --workdir "$ROOT/$PROJECT" \
  wellcome/tox:latest --workdir /tmp/.tox

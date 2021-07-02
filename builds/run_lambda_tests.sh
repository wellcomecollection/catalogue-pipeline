#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o verbose

PATH="$1"

ROOT=$(git rev-parse --show-toplevel)

docker run --rm --tty \
  --volume "$ROOT:$ROOT" \
  --workdir "$ROOT/$PATH" \
  wellcome/tox:latest --workdir /tmp/.tox

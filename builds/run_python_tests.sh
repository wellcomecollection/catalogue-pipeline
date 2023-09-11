#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o verbose

PROJECT="$1"

ROOT=$(git rev-parse --show-toplevel)

if [[ -f "$ROOT/$PROJECT/tox.ini" ]]
then
  docker run --rm --tty \
    --volume "$ROOT:$ROOT" \
    --workdir "$ROOT/$PROJECT" \
    760097843905.dkr.ecr.eu-west-1.amazonaws.com/wellcome/tox:latest --workdir /tmp/.tox
else
  echo "No tox.ini, no tests to run!"
fi

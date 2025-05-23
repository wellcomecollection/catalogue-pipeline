#!/usr/bin/env bash

set -o errexit
set -o nounset

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

pytest "$ROOT" \
    --cov "$ROOT/src" \
    --cov-report term \
    --cov-report xml:"$ROOT/coverage.xml" \
    "$@"

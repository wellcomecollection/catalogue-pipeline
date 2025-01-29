#!/usr/bin/env bash

set -o errexit
set -o nounset

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

# change working directory to the root of the project
cd "$ROOT"

mypy --config-file ./mypy.ini src/
mypy --config-file ./mypy.ini tests/

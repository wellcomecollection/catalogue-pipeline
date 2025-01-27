#!/usr/bin/env bash

set -o errexit
set -o nounset

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

# change working directory to the root of the project
cd "$ROOT"

mypy --config-file ./mypy.ini src/
<<<<<<< HEAD
mypy --config-file ./mypy.ini tests/
=======
mypy --config-file ./mypy.ini tests/
>>>>>>> aa95658 (fix formatting and typechecking)

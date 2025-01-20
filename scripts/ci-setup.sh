#!/usr/bin/env bash

set -o errexit
set -o nounset

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

# check output of python version matches .python-version
if ! python -V | grep -q "$(cat $ROOT/.python-version)"; then
    echo "Python version does not match .python-version"
    exit 1
fi

# install the requirements
pip install -r "$ROOT/src/requirements.txt"
pip install -r "$ROOT/src/dev_requirements.txt"
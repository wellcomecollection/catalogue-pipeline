#!/usr/bin/env bash

set -o errexit
set -o nounset

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

# change working directory to the root of the project
cd "$ROOT"

CHECK=${1:-}

if [ "$CHECK" == "--check" ]; then
    echo "Checking code formatting (run ./scripts/autoformat.sh to fix any issues!)..."
    black --check src/
    isort --profile=black --check src/
else
    echo "Formatting code ..."
    black src/
    isort --profile=black src/
fi

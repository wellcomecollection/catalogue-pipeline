#!/usr/bin/env bash

set -o errexit
set -o nounset

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

# change working directory to the root of the project
cd "$ROOT"

CHECK=${1:-}

if [ "$CHECK" == "--check" ]; then
    echo "Checking code formatting and linting (run ./scripts/autoformat.sh to fix any issues!)..."
    uvx ruff format src/ tests/ --check
    uvx ruff check src/ tests/
else
    echo "Formatting and linting code ..."
    uvx ruff format src/ tests/
    uvx ruff check src/ tests/ --fix
fi

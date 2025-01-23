#!/usr/bin/env bash

set -o errexit
set -o nounset

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

# change working directory to the root of the project
cd "$ROOT"

# Create a temporary directory to hold source code and packages
mkdir -p target/tmp

# get python version from .python-version
PY_VERSION=$(cat .python-version)

cp -r src/* target/tmp
pip install -r src/requirements.txt --platform manylinux2014_x86_64 --target target/tmp --only-binary=:all: --python-version $PY_VERSION

cd target/tmp
zip -r ../build.zip .
cd ../..

# Clean up the temporary build directory
rm -rf target/tmp

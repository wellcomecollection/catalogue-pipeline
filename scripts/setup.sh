#!/usr/bin/env bash

set -o errexit
set -o nounset

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

echo "Setting up the project ..."

# check if pyenv is installed, otherwise exit and echo install from message
if ! command -v pyenv &> /dev/null; then
    echo "pyenv is not installed. Please install it using \"brew install pyenv\""
    exit 1
fi

# get the version specified using pyenv local
PY_VERSION=$(pyenv local)
# check if the version is installed, otherwise install it
if ! pyenv versions | grep -q "$PY_VERSION"; then
    echo "Installing Python version $PY_VERSION ..."
    pyenv install "$PY_VERSION"
fi

# specify the python version to use for pyenv
export PYENV_VERSION="$PY_VERSION"

# check if venv exists otherwise create it
if [ ! -d "$ROOT/venv" ]; then
    echo "Creating virtual environment ..."
    python -m venv "$ROOT/venv"
fi

# activate the virtual environment
source "$ROOT/venv/bin/activate"

# install the requirements
pip install -r "$ROOT/src/requirements.txt"
pip install -r "$ROOT/src/dev_requirements.txt"

# install pip-tools
pip install pip-tools

# echo the setup is complete, and to run "source ./venv/bin/activate"
echo "Setup complete, please run \"source ./venv/bin/activate\" to activate the virtual environment."

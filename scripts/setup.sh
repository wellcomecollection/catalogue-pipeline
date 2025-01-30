#!/usr/bin/env bash

set -o errexit
set -o nounset

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

setup_git_hook() {
  echo "Setting up pre-push hook ..."
  cp "${ROOT}/scripts/pre-push" "${ROOT}/.git/hooks/pre-push"
  chmod +x "${ROOT}/.git/hooks/pre-push"
}

check_install_pyenv() {
    echo "Checking if pyenv is installed ..."
    # check if pyenv is installed, otherwise exit and echo install from message
    if ! command -v pyenv &> /dev/null; then
        echo "pyenv is not installed. Please install it using \"brew install pyenv\""
        exit 1
    fi
}

install_python() {
    echo "Installing Python ..."
    # get the version specified using pyenv local
    PY_VERSION=$(pyenv local)
    # check if the version is installed, otherwise install it
    if ! pyenv versions | grep -q "$PY_VERSION"; then
        echo "Installing Python version $PY_VERSION ..."
        pyenv install "$PY_VERSION"
    else
        echo "Python version $PY_VERSION is already installed."
    fi

    echo $PY_VERSION
}

setup_venv() {
    echo "Setting up virtual environment ..."
    PY_VERSION=$(pyenv local)
    # check if venv exists otherwise create it
    if [ ! -d "$ROOT/venv" ]; then
        echo "Creating virtual environment ..."
        python -m venv "$ROOT/venv"
    fi
}

install_python_deps() {
    echo "Installing Python dependencies ..."

    # install the requirements
    pip install -r "$ROOT/src/requirements.txt"
    pip install -r "$ROOT/src/dev_requirements.txt"

    # install pip-tools
    pip install pip-tools
}

setup_git_hook
check_install_pyenv

export PYENV_VERSION=$(install_python)
source "$ROOT/venv/bin/activate"

install_python_deps

echo "Setup complete :)"
echo "Run \"source ./venv/bin/activate\" to activate the virtual environment."

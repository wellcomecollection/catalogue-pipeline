#!/usr/bin/env bash

set -o errexit
set -o nounset

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

check_install_uv() {
    echo "Checking if UV is installed ..."
    if ! command -v uv &> /dev/null; then
        echo "UV is not installed. Installing UV..."
        pip install uv
    else
        echo "UV is already installed."
    fi
}

install_dependencies() {
    echo "Installing Python dependencies with UV ..."
    uv sync --all-groups
}

check_install_uv
install_dependencies

echo "Setup complete :)"
echo "Use 'uv run <command>' to run commands in the virtual environment."
echo "Or activate with 'source .venv/bin/activate' (UV creates a .venv directory by default)."

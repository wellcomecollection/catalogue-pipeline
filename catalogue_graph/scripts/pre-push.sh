#!/usr/bin/env bash

# Pre-push hook for catalogue_graph project
# 
# This script performs code formatting and type checking to ensure code quality
# before allowing commits to be pushed to the repository.
#
# Usage:
#   - As a git pre-push hook: Automatically runs both formatting and type checks
#   - Interactively: Use --format, --typecheck, or --install flags for specific operations
#   - Run without arguments to perform the full pre-push workflow
#
# Use --help for detailed usage information.

set -o errexit
set -o nounset

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT="$(dirname "$DIR")"

show_help() {
    cat << EOF
Usage: $0 [OPTIONS]

Pre-push hook for catalogue_graph that runs code formatting, type checks, and tests.

Options:
    --format        Run code formatting only
    --typecheck     Run type checks only
    --test          Run tests only
    --install       Install this script as a git pre-push hook
    --help, -h      Show this help message

When run without arguments, performs formatting, type checking, and tests (default pre-push behavior).
EOF
}

install_hook() {
    local git_root
    git_root=$(git rev-parse --show-toplevel)
    local hook_path="$git_root/.git/hooks/pre-push"
    local script_path="$DIR/$(basename "$0")"
    
    echo "Installing pre-push hook..."
    
    # Create hooks directory if it doesn't exist
    mkdir -p "$git_root/.git/hooks"
    
    # Create the hook script
    cat > "$hook_path" << EOF
#!/usr/bin/env bash
# Auto-generated pre-push hook
exec "$script_path"
EOF
    
    chmod +x "$hook_path"
    echo "Pre-push hook installed successfully at $hook_path"
}

run_formatting() {
    echo "Running code formatting..."
    cd "$ROOT"
    uv sync
    uv run ruff format .
    uv run ruff check . --fix
}

run_typecheck() {
    echo "Running type checks..."
    cd "$ROOT"
    uv sync
    uv run mypy .
}

run_tests() {
    echo "Running tests..."
    cd "$ROOT"
    uv sync
    uv run pytest
}

check_uncommitted_changes() {
    if ! git diff --quiet; then
        echo "Uncommitted changes found after formatting. Please commit or stash your changes."
        exit 1
    fi
}

# Parse command line arguments
case "${1:-}" in
    --format)
        run_formatting
        ;;
    --typecheck)
        run_typecheck
        ;;
    --test)
        run_tests
        ;;
    --install)
        install_hook
        ;;
    --help|-h)
        show_help
        ;;
    "")
        # Default behavior (pre-push hook)
        cd "$ROOT"
        uv sync
        
        run_formatting
        check_uncommitted_changes
        run_typecheck
        run_tests
        ;;
    *)
        echo "Unknown option: $1"
        show_help
        exit 1
        ;;
esac

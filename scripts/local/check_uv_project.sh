#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Default values
SKIP_FORMAT_CHECK="false"
SKIP_TYPE_CHECK="false"
SKIP_LINT="false"
SKIP_TEST="false"
PROJECT_FOLDER=""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to display usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS] [PROJECT_FOLDER]

Runs checks (linting, formatting, type checking, tests) on the python code within the given folder.
This mirrors the checks performed by the GitHub python_check action.

ARGUMENTS:
    PROJECT_FOLDER      Path to the uv project to check (default: current directory)

OPTIONS:
    --skip-format-check     Skip ruff format check
    --skip-type-check       Skip mypy type check
    --skip-lint            Skip ruff lint check
    --skip-test            Skip pytest tests
    -h, --help             Display this help message

EXAMPLES:
    # Check current directory
    $0

    # Check specific project
    $0 catalogue_graph

    # Check with some checks skipped
    $0 --skip-test --skip-type-check ebsco_adapter/ebsco_adapter_iceberg

    # Check inferrer project
    $0 pipeline/inferrer/aspect_ratio_inferrer

SUPPORTED PROJECTS:
    - catalogue_graph
    - ebsco_adapter/ebsco_adapter_iceberg
    - pipeline/inferrer/aspect_ratio_inferrer
EOF
}

# Function to print status messages
print_status() {
    local status="$1"
    local message="$2"
    case "$status" in
        "info")
            echo -e "${BLUE}[INFO]${NC} $message"
            ;;
        "success")
            echo -e "${GREEN}[SUCCESS]${NC} $message"
            ;;
        "warning")
            echo -e "${YELLOW}[WARNING]${NC} $message"
            ;;
        "error")
            echo -e "${RED}[ERROR]${NC} $message"
            ;;
        "step")
            echo -e "${BLUE}=== $message ===${NC}"
            ;;
    esac
}

# Function to check prerequisites
check_prerequisites() {
    print_status "step" "Checking prerequisites"
    
    # Check if uv is installed
    if ! command -v uv &> /dev/null; then
        print_status "error" "uv is not installed. Please install it first:"
        echo "  curl -LsSf https://astral.sh/uv/install.sh | sh"
        exit 1
    fi
    
    print_status "success" "uv found: $(uv --version)"
}

# Function to validate project structure
validate_project() {
    local project_path="$1"
    
    print_status "step" "Validating project structure"
    
    if [[ ! -d "$project_path" ]]; then
        print_status "error" "Project directory '$project_path' does not exist"
        return 1
    fi
    
    if [[ ! -f "$project_path/pyproject.toml" ]]; then
        print_status "error" "Project '$project_path' does not contain a pyproject.toml file"
        return 1
    fi
    
    if [[ ! -f "$project_path/.python-version" ]]; then
        print_status "error" "Project '$project_path' does not contain a .python-version file"
        return 1
    fi
    
    print_status "success" "Project structure validated"
    
    # Show project info
    local python_version
    python_version=$(cat "$project_path/.python-version")
    print_status "info" "Python version: $python_version"
    print_status "info" "Project path: $project_path"
}

# Function to setup dependencies
setup_dependencies() {
    local project_path="$1"
    
    print_status "step" "Setting up dependencies"
    
    cd "$project_path"
    
    if ! uv sync --all-groups; then
        print_status "error" "Failed to sync dependencies"
        return 1
    fi
    
    print_status "success" "Dependencies synced successfully"
}

# Function to run lint check
run_lint() {
    local project_path="$1"
    
    if [[ "$SKIP_LINT" == "true" ]]; then
        print_status "warning" "Skipping lint check"
        return 0
    fi
    
    print_status "step" "Running lint check"
    
    if uvx ruff check "$project_path"; then
        print_status "success" "Lint check passed"
        return 0
    else
        print_status "error" "Lint check failed"
        return 1
    fi
}

# Function to run format check
run_format_check() {
    local project_path="$1"
    
    if [[ "$SKIP_FORMAT_CHECK" == "true" ]]; then
        print_status "warning" "Skipping format check"
        return 0
    fi
    
    print_status "step" "Running format check"
    
    if uvx ruff format --check "$project_path"; then
        print_status "success" "Format check passed"
        return 0
    else
        print_status "error" "Format check failed"
        print_status "info" "Run 'uvx ruff format $project_path' to fix formatting"
        return 1
    fi
}

# Function to run type check
run_type_check() {
    local project_path="$1"
    
    if [[ "$SKIP_TYPE_CHECK" == "true" ]]; then
        print_status "warning" "Skipping type check"
        return 0
    fi
    
    print_status "step" "Running type check"
    
    cd "$project_path"
    
    if uv run mypy .; then
        print_status "success" "Type check passed"
        return 0
    else
        print_status "error" "Type check failed"
        return 1
    fi
}

# Function to run tests
run_tests() {
    local project_path="$1"
    
    if [[ "$SKIP_TEST" == "true" ]]; then
        print_status "warning" "Skipping tests"
        return 0
    fi
    
    print_status "step" "Running tests"
    
    cd "$project_path"
    
    # Mirror the GitHub action logic for test exit codes
    set +e
    uv run pytest
    local test_output=$?
    set -e
    
    # 5 = No Tests Run. Ideally, this would be a failure, but we
    # do have some projects with no tests, where it is still beneficial
    # to "run the tests", as that proves that the code can be run.
    if [[ $test_output -eq 5 ]]; then
        print_status "warning" "No tests found, but pytest ran successfully"
        test_output=0
    fi
    
    if [[ $test_output -eq 0 ]]; then
        print_status "success" "Tests passed"
        return 0
    else
        print_status "error" "Tests failed"
        return 1
    fi
}

# Main function
main() {
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-format-check)
                SKIP_FORMAT_CHECK="true"
                shift
                ;;
            --skip-type-check)
                SKIP_TYPE_CHECK="true"
                shift
                ;;
            --skip-lint)
                SKIP_LINT="true"
                shift
                ;;
            --skip-test)
                SKIP_TEST="true"
                shift
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            -*)
                print_status "error" "Unknown option $1"
                usage
                exit 1
                ;;
            *)
                if [[ -z "$PROJECT_FOLDER" ]]; then
                    PROJECT_FOLDER="$1"
                else
                    print_status "error" "Too many arguments"
                    usage
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # Set default project folder if not provided
    if [[ -z "$PROJECT_FOLDER" ]]; then
        PROJECT_FOLDER="."
    fi
    
    # Convert to absolute path if it's relative
    if [[ "$PROJECT_FOLDER" != /* ]]; then
        if [[ "$PROJECT_FOLDER" == "." ]]; then
            PROJECT_FOLDER="$(pwd)"
        else
            PROJECT_FOLDER="$(cd "$PROJECT_FOLDER" 2>/dev/null && pwd)" || {
                print_status "error" "Cannot access project folder: $PROJECT_FOLDER"
                exit 1
            }
        fi
    fi
    
    print_status "step" "Python Project Check"
    print_status "info" "Project: $PROJECT_FOLDER"
    print_status "info" "Skip lint: $SKIP_LINT"
    print_status "info" "Skip format check: $SKIP_FORMAT_CHECK"
    print_status "info" "Skip type check: $SKIP_TYPE_CHECK"
    print_status "info" "Skip test: $SKIP_TEST"
    echo
    
    # Track failures
    local failed_checks=0
    
    # Run all checks
    check_prerequisites
    
    if ! validate_project "$PROJECT_FOLDER"; then
        exit 1
    fi
    
    if ! setup_dependencies "$PROJECT_FOLDER"; then
        exit 1
    fi
    
    if ! run_lint "$PROJECT_FOLDER"; then
        ((failed_checks++))
    fi
    
    if ! run_format_check "$PROJECT_FOLDER"; then
        ((failed_checks++))
    fi
    
    if ! run_type_check "$PROJECT_FOLDER"; then
        ((failed_checks++))
    fi
    
    if ! run_tests "$PROJECT_FOLDER"; then
        ((failed_checks++))
    fi
    
    echo
    print_status "step" "Summary"
    
    if [[ $failed_checks -eq 0 ]]; then
        print_status "success" "All checks passed! ✓"
        exit 0
    else
        print_status "error" "$failed_checks check(s) failed ✗"
        exit 1
    fi
}

# Run main function with all arguments
main "$@"


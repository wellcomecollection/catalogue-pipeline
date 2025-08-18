#!/usr/bin/env bash
# Local deployment script for AWS Lambda functions
# This script builds a Lambda function from a local python project and deploys it to AWS.

set -o errexit
set -o nounset
set -o pipefail

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Default values
DEFAULT_TAG="dev"
S3_BUCKET="wellcomecollection-platform-infra"

# Set default AWS profile if not already set
export AWS_PROFILE="${AWS_PROFILE:-platform-developer}"

# Function to display usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS] PROJECT FUNCTION_NAME

Deploy a Lambda function from a local project build.

ARGUMENTS:
    PROJECT          Project path relative to repository root (e.g., catalogue_graph, ebsco_adapter/ebsco_adapter_iceberg)
    FUNCTION_NAME    AWS Lambda function name to deploy to

OPTIONS:
    -t, --tag TAG           Tag to use for S3 object and deployment (default: $DEFAULT_TAG)
    -b, --bucket BUCKET     S3 bucket for lambda zip storage (default: $S3_BUCKET)
    -r, --role ROLE_ARN     AWS IAM role to assume for deployment
    -h, --help              Display this help message

EXAMPLES:
    # Deploy catalogue graph indexer with dev tag
    $0 catalogue_graph catalogue-graph-indexer

    # Deploy EBSCO adapter trigger with custom tag
    $0 -t my-branch ebsco_adapter/ebsco_adapter_iceberg ebsco-adapter-trigger

    # Deploy with specific role assumption
    $0 -r arn:aws:iam::123456789012:role/MyRole catalogue_graph catalogue-graph-bulk-loader

ENVIRONMENT SETUP:
    This script requires:
    - uv (Python package manager)
    - AWS CLI configured with appropriate permissions
    - AWS credentials for target environment (via ~/.aws, environment, or role assumption)

SUPPORTED PROJECTS:
    - catalogue_graph
    - ebsco_adapter/ebsco_adapter_iceberg
EOF
}

# Function to check prerequisites
check_prerequisites() {
    echo "Checking prerequisites..."
    
    # Check if uv is installed
    if ! command -v uv &> /dev/null; then
        echo "Error: uv is not installed. Please install it first:" >&2
        echo "  curl -LsSf https://astral.sh/uv/install.sh | sh" >&2
        exit 1
    fi
    
    # Check if AWS CLI is installed
    if ! command -v aws &> /dev/null; then
        echo "Error: AWS CLI is not installed. Please install it first." >&2
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        echo "Error: AWS credentials not configured or invalid." >&2
        echo "Please configure AWS credentials using one of:" >&2
        echo "  - aws configure" >&2
        echo "  - Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)" >&2
        echo "  - IAM role if running on EC2/ECS" >&2
        exit 1
    fi
    
    echo "Prerequisites check passed."
}

# Function to validate project path
validate_project() {
    local project_path="$1"
    local full_path="$REPO_ROOT/$project_path"
    
    if [[ ! -d "$full_path" ]]; then
        echo "Error: Project directory '$project_path' does not exist." >&2
        return 1
    fi
    
    if [[ ! -f "$full_path/pyproject.toml" ]]; then
        echo "Error: Project '$project_path' does not contain a pyproject.toml file." >&2
        return 1
    fi
    
    if [[ ! -f "$full_path/.python-version" ]]; then
        echo "Error: Project '$project_path' does not contain a .python-version file." >&2
        return 1
    fi
    
    if [[ ! -d "$full_path/src" ]]; then
        echo "Error: Project '$project_path' does not contain a src directory." >&2
        return 1
    fi
    
    echo "Project validation passed for: $project_path"
}

# Function to assume AWS role if specified
assume_role() {
    local role_arn="$1"
    
    if [[ -n "$role_arn" ]]; then
        echo "Assuming AWS role: $role_arn"
        
        # Generate a unique session name
        local session_name="local-lambda-deploy-$(date +%s)"
        
        # Assume the role and extract credentials
        local temp_creds
        temp_creds=$(aws sts assume-role \
            --role-arn "$role_arn" \
            --role-session-name "$session_name" \
            --output json)
        
        # Export the temporary credentials
        export AWS_ACCESS_KEY_ID=$(echo "$temp_creds" | jq -r '.Credentials.AccessKeyId')
        export AWS_SECRET_ACCESS_KEY=$(echo "$temp_creds" | jq -r '.Credentials.SecretAccessKey')
        export AWS_SESSION_TOKEN=$(echo "$temp_creds" | jq -r '.Credentials.SessionToken')
        
        echo "Successfully assumed role."
    fi
}

# Function to build lambda ZIP
build_lambda() {
    local project_path="$1"
    local full_path="$REPO_ROOT/$project_path"
    
    echo "Building Lambda ZIP for project: $project_path" >&2
    
    cd "$full_path"
    
    # Get Python version from .python-version file
    local python_version
    python_version=$(cat .python-version)
    echo "Using Python version: $python_version" >&2
    
    # Set up build directories
    local target_dir="target"
    local tmp_dir="$target_dir/tmp"
    local zip_name="build.zip"
    local zip_target="$target_dir/$zip_name"
    
    # Clean up previous builds
    echo "Cleaning up previous builds..." >&2
    rm -rf "$tmp_dir"
    rm -f "$zip_target"
    mkdir -p "$tmp_dir"
    
    # Export requirements and install dependencies first
    echo "Installing dependencies..." >&2
    uv export --no-dev --format=requirements-txt > "$target_dir/requirements.txt"
    uv pip install \
        -r "$target_dir/requirements.txt" \
        --python-platform x86_64-manylinux2014 \
        --target "$tmp_dir" \
        --only-binary=:all: \
        --python-version "$python_version"
    
    # Copy source files after dependencies (this ensures our code is not overwritten)
    echo "Copying source files..." >&2
    cp -r src/* "$tmp_dir/"
    
    # Create ZIP file
    echo "Creating ZIP file..." >&2
    pushd "$tmp_dir" > /dev/null
    zip -r -q "../$zip_name" .
    popd > /dev/null
    
    # Clean up temporary directory
    rm -rf "$tmp_dir"
    
    echo "Created ZIP: $zip_target" >&2
    
    # Return the path to the ZIP file (this should be the only output from this function)
    echo "$full_path/$zip_target"
}

# Function to upload to S3
upload_to_s3() {
    local local_file="$1"
    local s3_key="$2"
    local s3_bucket="$3"
    
    echo "Uploading $local_file to s3://$s3_bucket/$s3_key" >&2
    
    if aws s3 cp "$local_file" "s3://$s3_bucket/$s3_key" >&2; then
        echo "Upload completed." >&2
        echo "$s3_key"
        return 0
    else
        echo "Upload failed." >&2
        return 1
    fi
}

# Function to deploy lambda
deploy_lambda() {
    local function_name="$1"
    local s3_bucket="$2"
    local s3_key="$3"
    
    echo "Deploying Lambda function: $function_name"
    echo "From: s3://$s3_bucket/$s3_key"
    
    # Get function ARN to verify it exists
    local function_arn
    function_arn=$(aws lambda get-function-configuration \
        --function-name "$function_name" \
        --query "FunctionArn" \
        --output text)
    
    echo "Updating function: $function_arn"
    
    # Update function code
    local revision_id
    revision_id=$(aws lambda update-function-code \
        --function-name "$function_name" \
        --s3-bucket "$s3_bucket" \
        --s3-key "$s3_key" \
        --query "RevisionId" \
        --output text \
        --publish)
    
    echo "Revision ID: $revision_id"
    
    # Wait for function to be updated
    echo "Waiting for function update to complete..."
    aws lambda wait function-updated \
        --function-name "$function_name"
    
    echo "Lambda deployment completed successfully!"
}

# Main function
main() {
    local project_path=""
    local function_name=""
    local tag="$DEFAULT_TAG"
    local bucket="$S3_BUCKET"
    local role_arn=""
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -t|--tag)
                tag="$2"
                shift 2
                ;;
            -b|--bucket)
                bucket="$2"
                shift 2
                ;;
            -r|--role)
                role_arn="$2"
                shift 2
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            -*)
                echo "Error: Unknown option $1" >&2
                usage
                exit 1
                ;;
            *)
                if [[ -z "$project_path" ]]; then
                    project_path="$1"
                elif [[ -z "$function_name" ]]; then
                    function_name="$1"
                else
                    echo "Error: Too many arguments" >&2
                    usage
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # Validate required arguments
    if [[ -z "$project_path" || -z "$function_name" ]]; then
        echo "Error: Missing required arguments" >&2
        usage
        exit 1
    fi
    
    # Check if jq is available (needed for role assumption)
    if [[ -n "$role_arn" ]] && ! command -v jq &> /dev/null; then
        echo "Error: jq is required for role assumption but not installed." >&2
        echo "Please install jq or remove the --role option." >&2
        exit 1
    fi
    
    echo "=========================================="
    echo "Local Lambda Deployment Script"
    echo "=========================================="
    echo "Project: $project_path"
    echo "Function: $function_name"
    echo "Tag: $tag"
    echo "S3 Bucket: $bucket"
    if [[ -n "$role_arn" ]]; then
        echo "Role: $role_arn"
    fi
    echo "=========================================="
    
    # Execute deployment steps
    check_prerequisites
    validate_project "$project_path"
    assume_role "$role_arn"
    
    local zip_path
    zip_path=$(build_lambda "$project_path")
    
    local s3_key="lambdas/$project_path/lambda-$tag.zip"
    upload_to_s3 "$zip_path" "$s3_key" "$bucket"
    
    deploy_lambda "$function_name" "$bucket" "$s3_key"
    
    echo "=========================================="
    echo "Deployment completed successfully!"
    echo "Function: $function_name"
    echo "Tag: $tag"
    echo "=========================================="
}

# Run main function with all arguments
main "$@"

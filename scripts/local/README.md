# Local Scripts

This directory contains scripts for local development and deployment of catalogue pipeline projects.

## Python Lambda Deployment

This script allows you to build and deploy Lambda functions locally for the catalogue pipeline projects.

### Prerequisites

1. **uv** - Python package manager
   ```bash
   curl -LsSf https://astral.sh/uv/install.sh | sh
   ```

2. **AWS CLI** - For AWS operations
   ```bash
   # macOS
   brew install awscli
   # Or download from https://aws.amazon.com/cli/
   ```

3. **jq** - JSON processor (required only if using role assumption)
   ```bash
   # macOS
   brew install jq
   ```

4. **AWS Credentials** - Configure using one of:
   - `aws configure`
   - Environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
   - IAM role (if running on AWS infrastructure)
   - Role assumption via the `--role` parameter

### Usage

```bash
./scripts/local/deploy_python_lambda.sh [OPTIONS] PROJECT FUNCTION_NAME
```

#### Arguments

- `PROJECT` - Project path relative to repository root
  - `catalogue_graph`
  - `ebsco_adapter/ebsco_adapter_iceberg`
- `FUNCTION_NAME` - AWS Lambda function name to deploy to

#### Options

- `-t, --tag TAG` - Tag for S3 object and deployment (default: "dev")
- `-b, --bucket BUCKET` - S3 bucket for storage (default: "wellcomecollection-platform-infra")
- `-r, --role ROLE_ARN` - AWS IAM role to assume for deployment
- `-h, --help` - Display help message

### Examples

#### Deploy catalogue graph functions

```bash
# Deploy indexer with dev tag
./scripts/local/deploy_python_lambda.sh catalogue_graph catalogue-graph-indexer

# Deploy bulk loader with custom tag
./scripts/local/deploy_python_lambda.sh -t feature-branch catalogue_graph catalogue-graph-bulk-loader

# Deploy with role assumption
./scripts/local/deploy_python_lambda.sh -r arn:aws:iam::123456789012:role/CatalogueGraphRole catalogue_graph catalogue-graph-indexer
```

#### Deploy EBSCO adapter functions

```bash
# Deploy trigger function
./scripts/local/deploy_python_lambda.sh ebsco_adapter/ebsco_adapter_iceberg ebsco-adapter-trigger

# Deploy loader with custom tag
./scripts/local/deploy_python_lambda.sh -t hotfix-v2 ebsco_adapter/ebsco_adapter_iceberg ebsco-adapter-loader
```

### Available Lambda Functions

#### Catalogue Graph
- `catalogue-graph-bulk-loader`
- `catalogue-graph-bulk-load-poller`
- `catalogue-graph-indexer`
- `catalogue-graph-remover`
- `catalogue-graph-ingestor-trigger`
- `catalogue-graph-ingestor-trigger-monitor`
- `catalogue-graph-ingestor-loader`
- `catalogue-graph-ingestor-loader-monitor`
- `catalogue-graph-ingestor-indexer`
- `catalogue-graph-ingestor-indexer-monitor`
- `catalogue-graph-ingestor-deletions`
- `catalogue-graph-ingestor-reporter`

#### EBSCO Adapter
- `ebsco-adapter-trigger`
- `ebsco-adapter-loader`
- `ebsco-adapter-transformer`

### How it Works

1. **Validation** - Checks prerequisites and project structure
2. **Role Assumption** - Optionally assumes AWS IAM role
3. **Build** - Creates Lambda ZIP using uv (same as CI/CD)
   - Copies source files from `src/`
   - Installs dependencies for x86_64-manylinux2014 platform
   - Creates optimized ZIP package
4. **Upload** - Uploads ZIP to S3 with tag-based naming
5. **Deploy** - Updates Lambda function code from S3
6. **Wait** - Waits for deployment to complete

### Error Handling

The script includes comprehensive error handling:
- Prerequisites checking
- Project validation
- AWS credential verification
- Proper cleanup on failure
- Detailed error messages

### Environment Variables

The script respects standard AWS environment variables:
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_SESSION_TOKEN`
- `AWS_REGION` (defaults to eu-west-1)

### Notes

- Uses "dev" tag by default for local deployments
- ZIP files are built with the same process as GitHub Actions
- Python versions are read from each project's `.python-version` file
- Dependencies are installed for AWS Lambda runtime (x86_64-manylinux2014)
- S3 prefixes match the GitHub Actions configuration

## Python Project Check

This script runs local checks (linting, formatting, type checking, tests) on uv-based Python projects, mirroring the checks performed by the GitHub `python_check` action.

### Prerequisites

- **uv** - Python package manager (same as above)

### Usage

```bash
./scripts/local/check_uv_project.sh [OPTIONS] [PROJECT_FOLDER]
```

#### Arguments

- `PROJECT_FOLDER` - Path to the uv project to check (default: current directory)

#### Options

- `--skip-format-check` - Skip ruff format check
- `--skip-type-check` - Skip mypy type check  
- `--skip-lint` - Skip ruff lint check
- `--skip-test` - Skip pytest tests
- `-h, --help` - Display help message

### Examples

```bash
# Check current directory
./scripts/local/check_uv_project.sh

# Check specific project
./scripts/local/check_uv_project.sh catalogue_graph

# Check with some checks skipped  
./scripts/local/check_uv_project.sh --skip-test --skip-type-check ebsco_adapter/ebsco_adapter_iceberg

# Check inferrer project
./scripts/local/check_uv_project.sh pipeline/inferrer/aspect_ratio_inferrer
```

### Supported Projects

- `catalogue_graph`
- `ebsco_adapter/ebsco_adapter_iceberg`  
- `pipeline/inferrer/aspect_ratio_inferrer`

### What it Checks

1. **Prerequisites** - Verifies uv is installed
2. **Project Structure** - Validates `pyproject.toml`, `.python-version`, and `src/` directory
3. **Dependencies** - Runs `uv sync --all-groups` to install dependencies
4. **Linting** - Runs `uvx ruff check` (unless skipped)
5. **Formatting** - Runs `uvx ruff format --check` (unless skipped)  
6. **Type Checking** - Runs `uv run mypy .` (unless skipped)
7. **Tests** - Runs `uv run pytest` (unless skipped)

### Exit Codes

- Exit code 5 from pytest (no tests found) is treated as success
- Any other failures result in exit code 1
- All checks passed results in exit code 0

### Notes

- Matches the exact behavior of the GitHub `python_check` action
- Colorized output for better readability
- Detailed progress reporting and error messages
- Automatically handles different Python versions per project

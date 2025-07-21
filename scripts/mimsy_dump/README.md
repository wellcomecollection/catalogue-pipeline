# MIMSY Database Dump Tool

⚠️ **TEMPORARY TOOL** ⚠️

This script is a temporary utility created to help collection information metadata analysts during the MIMSY migration process. It will be removed once the migration is complete.

## Overview

This CLI tool connects to a MIMSY Oracle database and dumps specified views to CSV files. It can optionally create a ZIP file containing all the dumped files for easy distribution and analysis.

## Prerequisites

- Python 3.10 or higher
- UV package manager
- AWS CLI configured with appropriate credentials
- Access to the MIMSY Oracle database via AWS Secrets Manager

## Installation

1. Ensure you have UV installed:
   ```bash
   curl -LsSf https://astral.sh/uv/install.sh | sh
   ```

2. Navigate to the mimsy_dump directory:
   ```bash
   cd scripts/mimsy_dump
   ```

3. Install the project and dependencies:
   ```bash
   uv sync
   ```

## Configuration

The tool requires access to AWS Secrets Manager to retrieve the database connection string. Ensure you have:

1. AWS CLI configured with the `platform-developer` profile (or specify a different profile)
2. Access to the secret containing the MIMSY database connection string

## Usage

### Basic Usage

Dump all default views with default settings:
```bash
uv run mimsy-dump
```

### Advanced Usage

#### Specify custom parameters:
```bash
uv run mimsy-dump \
  --secret-name "mimsy_dump/connection_string" \
  --profile "platform-developer" \
  --region "eu-west-1" \
  --output-dir "./output"
```

#### Dump specific views only:
```bash
uv run mimsy-dump --views VW_CATALOGUE VW_EXHIBITIONS
```

#### Skip ZIP file creation:
```bash
uv run mimsy-dump --no-zip
```

#### Skip the exhibition items join query:
```bash
uv run mimsy-dump --no-include-join
```

### Command Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `--secret-name` | AWS Secrets Manager secret name for database connection | `mimsy_dump/connection_string` |
| `--profile` | AWS profile name to use | `platform-developer` |
| `--region` | AWS region for Secrets Manager | `eu-west-1` |
| `--output-dir` | Output directory for CSV files and ZIP file | `.` (current directory) |
| `--no-zip` | Skip creating a ZIP file | `False` |
| `--views` | List of views to dump | See default views below |
| `--include-join` | Include the exhibition items join query | `True` |

### Default Views

The tool dumps the following views by default:
- `VW_OTHER_NUMBERS`
- `VW_EXHIBITION_ITEMS`
- `VW_EXHIBITIONS`
- `VW_CATALOGUE`
- `VW_CONSERVATION_STATUS`
- `VW_CONDITION`
- `VW_LOAN_ITEMS`

Plus a custom join query combining exhibition items with catalogue data.

## Output

The tool generates:
1. Individual CSV files for each view (named `mimsy_<viewname>.csv`)
2. A timestamped ZIP file containing all CSV files (optional)

## Examples

### Example 1: Quick dump for analysis
```bash
# Dump all default views and create a ZIP file in current directory
uv run mimsy-dump
```

### Example 2: Specific views only
```bash
# Dump only catalogue and exhibition data
uv run mimsy-dump --views VW_CATALOGUE VW_EXHIBITIONS --output-dir ./catalogue_data
```

### Example 3: Development/testing
```bash
# Dump without ZIP file for quick testing
uv run mimsy-dump --no-zip --output-dir ./test_output
```

## Troubleshooting

### Common Issues

1. **AWS Authentication Error**: Ensure your AWS profile is correctly configured and has access to the Secrets Manager secret.

2. **Oracle Connection Error**: Verify that the connection string in AWS Secrets Manager is correct and the database is accessible.

3. **Permission Errors**: Ensure you have write permissions to the output directory.

### Error Messages

- "Failed to retrieve database connection string": Check your AWS credentials and secret name
- "Error connecting to database": Verify the Oracle connection string and network access
- "Error dumping query": Check that the specified views exist and are accessible

## Development

### Running Tests
```bash
uv run pytest
```

### Code Formatting
```bash
uv run ruff check .
uv run ruff format .
```

### Type Checking
```bash
uv run mypy mimsy_dump.py
```

## Support

This is a temporary tool for the MIMSY migration project. For issues or questions, contact the Wellcome Collection digital team.

## License

This project is licensed under the MIT License - see the main catalogue-pipeline repository for details.

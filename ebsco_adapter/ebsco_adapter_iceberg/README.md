# EBSCO Adapter for Iceberg

Download and extract EBSCO data, populating an Iceberg table with it.

EBSCO data is in MARC XML format, provided as a set of `<record>` elements inside a `<collection>` documentElement.

For each record, resulting Iceberg table contains the XML, stored in the `content` field, and the identifier, of the
record,
derived from the `<controlfield tag="001">` element

Subsequent updates will look for matching records using that same id and:

* Insert any new records not found in the existing table
* Delete (blank-out) any records not found in the incoming data
* Change any records that are different between the two.

Each update can be identified by its changeset identifier, which is returned from this process.

## Running Locally

The EBSCO adapter consists of a series of steps that can be run locally or in a pipeline. 

### Prerequisites

Ensure you have [uv](https://docs.astral.sh/uv/) installed and are in the project root directory.

### Running the Steps

The steps can be run using the `uv` command, which allows you to run Python modules with dependencies managed by `uv`.

Example usage:
```bash
cd src
uv run src/steps/trigger.py --job-id my-job-123 --local
```

### Development

The project uses a `src/` layout with proper Python packaging. 

To run the tests, you can use `uv` as well:

```bash
uv run pytest
```

## Deployment

### Manual deployment

For manual deployment:

* **Lambda functions**: Use the local deployment script from the repository root:
  ```shell
  ./scripts/local/deploy_python_lambda.sh ebsco_adapter/ebsco_adapter_iceberg <function-name>
  ```

## Lake Formation permissions (manual step)

Managing the Lake Formation permissions for the S3 Tables Catalog resources via Terraform is currently unreliable due to a provider limitation. Until this is resolved, apply the required permissions with the helper script in this repo:

- Script: `ebsco_adapter/ebsco_adapter_iceberg/scripts/create_permissions.sh`
- Requirements: AWS CLI and `jq`; ensure you are authenticated (e.g., via `aws sso login`).
- Idempotent: safe to re-run; apply after stack creation and whenever principals change.

Run locally:

```bash
bash ebsco_adapter/ebsco_adapter_iceberg/scripts/create_permissions.sh
```

Background and tracking:

- Internal discussion: https://wellcome.slack.com/archives/C02ANCYL90E/p1756830340467749
- Potential fix in provider: https://github.com/hashicorp/terraform-provider-aws/pull/43931

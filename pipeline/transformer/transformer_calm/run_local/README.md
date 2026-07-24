# Local CALM transformer runner

This folder contains scripts to run `transformer_calm` locally against:

- LocalStack for SQS/SNS (hardcoded local queue/topic)
- AWS platform account for reading CALM source rows from DynamoDB and ES connection secrets
- Local Docker Elasticsearch for safe test indexing (default)
- Optional deployed Elasticsearch (public host) for real pipeline index writes

It supports a fire-and-forget workflow from a line-separated ID file, with batching.
The implementation is a Python CLI (`local_transformer.py`).

## What the CLI commands do

- `local_transformer.py start`
  - Fetches Elasticsearch env vars from AWS Secrets Manager for a pipeline date (see `fetch-es-env`)
  - Uses an explicit `--index-name` (no index derived from pipeline date)
  - Builds/stages `transformer_calm`
  - Starts Docker Compose services (`localstack`, queue/topic setup, transformer)
  - When using local ES (default), waits for ES health and creates the target index if missing
  - Follows `calm-transformer` logs by default

- `local_transformer.py fetch-es-env`
  - For `--es-host local` (default), writes:
    - `es_host=elasticsearch`, `es_port=9200`, `es_protocol=http`, empty `es_apikey`
  - For `--es-host public`, reads:
    - `elasticsearch/pipeline_storage_<pipeline_date>/public_host`
    - `elasticsearch/pipeline_storage_<pipeline_date>/port`
    - `elasticsearch/pipeline_storage_<pipeline_date>/protocol`
    - `elasticsearch/pipeline_storage_<pipeline_date>/transformer/api_key`
  - Writes `.env` containing:
    - `es_host`, `es_port`, `es_protocol`, `es_apikey`, `es_index`
    - resolved AWS session credentials for the container (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`)

- `local_transformer.py enqueue`
  - Reads line-separated CALM IDs from a file
  - For each ID, fetches `payload.bucket`, `payload.key`, `version`, `isDeleted` from DynamoDB (`vhs-calm-adapter` by default)
  - Sends correctly-shaped messages to local SQS queue
  - Processes IDs in batches (default 100)

- `local_transformer.py verify-completion`
  - Waits until local input queue is drained (`visible=0` and `in_flight=0`)
  - Checks transformer logs for errors
  - Verifies every input ID has a corresponding ES document ID in the format:
    - `Work[calm-record-id/<id>]`

## Prerequisites

- Docker + Docker Compose
- Python 3
- AWS CLI authenticated for platform account access
- Repo root checked out locally
- ECR login for pulling the sbt wrapper image:

```bash
aws ecr get-login-password --region eu-west-1 --profile platform-developer | \
docker login --username AWS --password-stdin 760097843905.dkr.ecr.eu-west-1.amazonaws.com
```

Optional env vars:

- `AWS_PROFILE` (defaults to `platform-developer` for the transformer container)
- `AWS_REGION` (defaults to `eu-west-1`)
- `CALM_TABLE_NAME` (defaults to `vhs-calm-adapter`)

## CLI usage

```bash
run_local/local_transformer.py --help
run_local/local_transformer.py start --help
run_local/local_transformer.py enqueue --help
run_local/local_transformer.py verify-completion --help
```

## Steps

From `pipeline/transformer/transformer_calm`:

1. Start local services and transformer:

```bash
run_local/local_transformer.py start <pipeline_date> --index-name <index_name>
```

Example:

```bash
run_local/local_transformer.py start 2026-07-03 --index-name works-source-2026-07-03
```

This command tails `calm-transformer` logs. Press `Ctrl+C` to stop log streaming; containers continue running.

This default uses local Docker Elasticsearch (`--es-host local`), so writes go to a local test index.

To write to the deployed public Elasticsearch instead, pass:

```bash
run_local/local_transformer.py start 2026-07-03 --index-name works-source-2026-07-03 --es-host public
```

2. Enqueue line-separated CALM IDs:

```bash
run_local/local_transformer.py enqueue /path/to/calm_ids.txt
```

3. Optional custom batch size:

```bash
run_local/local_transformer.py enqueue /path/to/calm_ids.txt 250
```

4. Verify completion:

```bash
run_local/local_transformer.py verify-completion /path/to/calm_ids.txt
```

Optional timeout/polling controls:

```bash
run_local/local_transformer.py verify-completion /path/to/calm_ids.txt 900 15
```

## Notes

- The queue URL is fixed to:
  `http://localhost:4566/000000000000/calm-transformer-queue`
- Missing IDs are skipped and counted.
- This workflow enqueues messages; it does not wait for full downstream completion. Check downstream completion with `run_local/local_transformer.py verify-completion`

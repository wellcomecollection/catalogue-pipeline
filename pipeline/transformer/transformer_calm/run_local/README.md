# Local CALM transformer runner

This folder contains scripts to run `transformer_calm` locally against:

- LocalStack for SQS/SNS (hardcoded local queue/topic)
- Real AWS for reading CALM source rows from DynamoDB and ES connection secrets

It supports a fire-and-forget workflow from a line-separated ID file, with batching.

## What each script does

- `start_local_transformer.sh`
  - Fetches Elasticsearch env vars from AWS Secrets Manager for a pipeline date (see `fetch_es_env.sh`)
  - Uses an explicit `--index-name` (no index derived from pipeline date)
  - Builds/stages `transformer_calm`
  - Starts Docker Compose services (`localstack`, queue/topic setup, transformer)
  - Follows `calm-transformer` logs by default

- `fetch_es_env.sh`
  - Reads:
    - `elasticsearch/pipeline_storage_<pipeline_date>/private_host`
    - `elasticsearch/pipeline_storage_<pipeline_date>/port`
    - `elasticsearch/pipeline_storage_<pipeline_date>/protocol`
    - `elasticsearch/pipeline_storage_<pipeline_date>/transformer/api_key`
  - Writes `.env.local` containing:
    - `es_host`, `es_port`, `es_protocol`, `es_apikey`, `es_index`

- `enqueue_calm_ids.sh`
  - Reads line-separated CALM IDs from a file
  - For each ID, fetches `payload.bucket`, `payload.key`, `version`, `isDeleted` from DynamoDB (`vhs-calm-adapter` by default)
  - Sends correctly-shaped CALM messages to local SQS queue
  - Processes IDs in batches (default 100)

- `verify_completion.sh`
  - Waits until local input queue is drained (`visible=0` and `in_flight=0`)
  - Checks transformer logs for errors
  - Verifies every input ID has a corresponding ES document ID in the format:
    - `Work[calm-record-id/<id>]`

## Prerequisites

- Docker + Docker Compose
- AWS CLI authenticated for platform account access
- Repo root checked out locally

Optional env vars:

- `AWS_PROFILE` (if you want to force a named profile)
- `AWS_REGION` (defaults to `eu-west-1`)
- `CALM_TABLE_NAME` (defaults to `vhs-calm-adapter`)

## Steps

From `pipeline/transformer/transformer_calm`:

1. Start local services and transformer:

```bash
run_local/start_local_transformer.sh <pipeline_date> --index-name <index_name>
```

Example:

```bash
run_local/start_local_transformer.sh 2026-07-03 --index-name works-source-2026-07-03
```

This command tails `calm-transformer` logs. Press `Ctrl+C` to stop log streaming; containers continue running.

2. Enqueue line-separated CALM IDs:

```bash
run_local/enqueue_calm_ids.sh /path/to/calm_ids.txt
```

3. Optional custom batch size:

```bash
run_local/enqueue_calm_ids.sh /path/to/calm_ids.txt 250
```

4. Verify completion:

```bash
run_local/verify_completion.sh /path/to/calm_ids.txt
```

Optional timeout/polling controls:

```bash
run_local/verify_completion.sh /path/to/calm_ids.txt 900 15
```

## Notes

- The queue URL is fixed to:
  `http://localhost:4566/000000000000/calm-transformer-queue`
- Missing IDs are skipped and counted.
- This workflow enqueues messages; it does not wait for full downstream completion. Check downstream completion with `run_local/verify_completion.sh`

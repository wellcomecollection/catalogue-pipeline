# ID Minter

Python port of the Scala id_minter service. Assigns stable canonical identifiers to catalogue works by maintaining a mapping of source identifiers to canonical IDs in a MySQL database, following the design in [RFC 083](https://github.com/wellcomecollection/docs/tree/main/rfcs/083-stable_identifiers).

## Structure

```
src/id_minter/
├── __init__.py
├── config.py                  # Runtime configuration (RDS, ES, SNS)
├── database.py                # pymysql connection + yoyo migration runner
├── migrations/                # SQL schema migrations
│   ├── 0001_create_identifiers_schema.sql
│   └── 0001_create_identifiers_schema.rollback.sql
├── models/
│   └── step_events.py         # StepFunctionMintingRequest/Response
└── steps/
    ├── id_minter.py           # Lambda handler + CLI entry point
    └── id_generator.py       # ID pre-generation Lambda + CLI entry point
```

## Running locally

All commands run from `catalogue_graph/`.

### Start the local MySQL database

```bash
docker compose -f mysql.docker-compose.yml up -d
```

This starts a MySQL 8.0 container with an `identifiers` database and an `id_minter` user.  
It can be used to locally run the id_minter and id_generator, see below

#### 1. Run the id_minter

When run locally the id_minter reads from the **public** Elasticsearch API and writes to a **local** ES container. This means you don't need VPC access to fetch works-source documents.

The id_minter supports three modes:

- **IDs mode**: supply `--source-identifiers` for targeted minting of specific works.
- **Window mode**: supply `--end-time` (and optionally `--start-time`, which defaults to `end_time - 15 min`) to process recently indexed works.
- **Full reprocess mode**: omit both to process the entire source index.

Supplying both `--source-identifiers` and a time window is invalid.

##### IDs mode

```bash
uv run python -m id_minter.steps.id_minter \
    --source-identifiers 'Work[sierra-system-number/b1000001]'
```

##### Using the local MySQL resolver

```bash
uv run python -m id_minter.steps.id_minter \
    --source-identifiers 'Work[sierra-system-number/b1000001]' \
    --resolver local \
    --apply-migrations
```

This connects to a local MySQL instance (see docker-compose setup above).

##### Multiple identifiers with a custom job ID

```bash
uv run python -m id_minter.steps.id_minter \
    --source-identifiers \
      'Work[sierra-system-number/b1000001]' \
      'Work[sierra-system-number/b1000002]' \
    --job-id my-test-job \
    --apply-migrations
```

##### Window mode

Process works indexed within a time window:

```bash
uv run python -m id_minter.steps.id_minter \
    --end-time '2025-03-25T15:00:00' \
    --resolver local \
    --apply-migrations
```

With an explicit start time:

```bash
uv run python -m id_minter.steps.id_minter \
    --start-time '2025-03-25T14:45:00' \
    --end-time '2025-03-25T15:00:00'
```

##### Full reprocess mode

Process all documents in the source index:

```bash
uv run python -m id_minter.steps.id_minter \
    --resolver local \
    --apply-migrations
```

##### Writing to the public Elasticsearch cluster

By default the CLI reads from the public ES cluster and writes to a local one.
To write to the public identified index instead:

```bash
uv run python -m id_minter.steps.id_minter \
    --source-identifiers 'Work[sierra-system-number/b1000001]' \
    --pipeline-date 2025-10-02 \
    --target-es-mode public
```

##### CLI flags

| Flag | Description |
|---|---|
| `--source-identifiers` | One or more source identifiers to process (IDs mode). |
| ` --window-end` | End of the time window, ISO 8601 (window mode). |
| ` --window-start` | Start of the time window, ISO 8601. Defaults to `end_time - 15 minutes`. |
| `--job-id` | Optional job ID — defaults to the current timestamp. |
| `--resolver` | ID resolver backend: `local` (pymysql to local MySQL) or `data-api` (AWS RDS Data API, default). |
| `--source-index-prefix` | Override the upstream ES index name prefix. |
| `--target-index-prefix` | Override the downstream ES index name prefix. |
| `--source-es-mode` | Elasticsearch mode for reading source documents: `public` (default), `private`, or `local`. |
| `--target-es-mode` | Elasticsearch mode for writing indexed documents: `local` (default), `public`, or `private`. |
| `--pipeline-date` | Override the pipeline date (used for ES secrets and as default for index suffixes). Defaults to `dev`. |
| `--source-index-date-suffix` | Override the date suffix for the source index. Defaults to `--pipeline-date`. |
| `--target-index-date-suffix` | Override the date suffix for the target index. Defaults to `--pipeline-date`. |
| `--apply-migrations` | Apply database schema migrations on startup. Only needed on first run or after adding new migrations. |
| `--dry-run` | Log the resolved configuration and exit without running. |

#### 2. Verify the database schema

```bash
docker exec id-minter-mysql mysql -u id_minter -pid_minter identifiers \
  -e "SHOW TABLES; DESCRIBE canonical_ids; DESCRIBE identifiers;"
```

#### 3. Clean up

```bash
docker compose -f mysql.docker-compose.yml down -v
```

#### 1. Run the id_generator

The ID Generator pre-generates canonical IDs to maintain a pool of free IDs for the id_minter.

```bash
uv run python -m id_minter.steps.id_generator --apply-migrations --desired-free-ids-count 10
```

Environment variables are set as default in `config.py`. The generator will:
- Apply migrations (creates the `canonical_ids` table if it doesn't exist)
- Generate IDs until the count reaches `--desired-free-ids-count`. If omitted, defaults to  `ID_GENERATOR_DESIRED_FREE_IDS_COUNT` in config

#### 2. Verify the ID pool

```bash
docker exec id-minter-mysql mysql -u id_minter -pid_minter identifiers \
  -e "SELECT COUNT(*) FROM canonical_ids WHERE Status='free';"
```

#### 3. Clean up

```bash
docker compose -f mysql.docker-compose.yml down -v
```

## Lambda handlers

### id_minter
The Lambda entry point is `id_minter.steps.id_minter.lambda_handler`. It expects a `StepFunctionMintingRequest` payload in one of three modes:

**Window mode** (scheduled runs):

```json
{
  "endTime": "2026-02-10T15:00:00",
  "jobId": "20260210T1500"
}
```

An optional `startTime` can be provided; if omitted it defaults to `endTime - 15 minutes`.

**IDs mode** (targeted minting):

```json
{
  "sourceIdentifiers": ["Work[sierra-system-number/b1000001]"],
  "jobId": "targeted-mint"
}
```

**Full reprocess mode** (process entire index):

```json
{
  "jobId": "full-reprocess-20260210"
}
```

Supplying both `sourceIdentifiers` and a time window is invalid.

### id_generator

The Lambda entry point is `id_minter.steps.id_generator.lambda_handler`. It takes an empty event payload and returns:

```json
{
  "status": "success"
}
```

The generator runs on a schedule (Mon-Fri at 3am UTC) to maintain the ID pool.

## Configuration

All settings are sourced from environment variables with sensible defaults for local development. See `config.py` for the full list. Key variables:

| Variable | Default | Description |
|---|---|---|
| `RDS_PRIMARY_HOST` | `localhost` | MySQL host |
| `RDS_PORT` | `3306` | MySQL port |
| `RDS_USERNAME` | `id_minter` | Database user |
| `RDS_PASSWORD` | _(empty)_ | Database password |
| `IDENTIFIERS_DATABASE` | `identifiers` | Database name |
| `APPLY_MIGRATIONS` | `false` | Apply yoyo migrations on startup |
| `ES_SOURCE_INDEX_PREFIX` | `works-source` | Upstream ES index prefix |
| `ES_TARGET_INDEX_PREFIX` | `works-identified` | Downstream ES index prefix |
| `ES_SOURCE_INDEX_DATE_SUFFIX` | _(unset — uses `PIPELINE_DATE`)_ | Date suffix for the source index |
| `ES_TARGET_INDEX_DATE_SUFFIX` | _(unset — uses `PIPELINE_DATE`)_ | Date suffix for the target index |

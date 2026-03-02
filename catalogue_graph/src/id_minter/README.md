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
    └── id_minter.py           # Lambda handler + CLI entry point
```

## Running locally

All commands run from `catalogue_graph/`.

### Start the local MySQL database

```bash
docker compose -f mysql.docker-compose.yml up -d
```

This starts a MySQL 8.0 container with an `identifiers` database and an `id_minter` user.
It can be used to locally run the id_minter and ids_generator, see below

#### 1. Run the id_minter

```bash
RDS_USERNAME=id_minter RDS_PASSWORD=id_minter \
  uv run python -m id_minter.steps.id_minter \
    --source-identifiers id1 id2 id3 \
    --job-id my-test-job \
    --apply-migrations
```

- `--apply-migrations` applies the database schema on startup (creates the `canonical_ids` and `identifiers` tables). Only needed on first run or after adding new migrations.
- `--source-identifiers` takes one or more source identifiers to mint.
- `--job-id` is optional — defaults to the current timestamp if omitted.
- `--source-index` / `--target-index` optionally override the upstream/downstream ES index names.

#### 2. Verify the database schema

```bash
docker exec id-minter-mysql mysql -u id_minter -pid_minter identifiers \
  -e "SHOW TABLES; DESCRIBE canonical_ids; DESCRIBE identifiers;"
```

#### 3. Clean up

```bash
docker compose -f mysql.docker-compose.yml down -v
```

#### 1. Run the ids_generator

The ID Generator pre-generates canonical IDs to maintain a pool of free IDs for the id_minter.

```bash
uv run python -m id_minter.ids_generator --apply-migrations
```

Environment variables are set as default in `config.py`. The generator will:
- Apply migrations (creates the `canonical_ids` table if it doesn't exist)
- Generate IDs until there are 1000 free IDs available (configurable via `IDS_GENERATOR_DESIRED_FREE_IDS_COUNT`)

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
The Lambda entry point is `id_minter.steps.id_minter.lambda_handler`. It expects a `StepFunctionMintingRequest` payload:

```json
{
  "source_identifiers": ["id1", "id2"],
  "job_id": "20260210T1500"
}
```

### ids_generator

The Lambda entry point is `id_minter.ids_generator.lambda_handler`. It takes an empty event payload and returns:

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
| `ES_SOURCE_INDEX` | `works-source` | Upstream ES index |
| `ES_TARGET_INDEX` | `works-identified` | Downstream ES index |

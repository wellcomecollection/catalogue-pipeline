# OAI-PMH Adapter Framework

This module provides the shared infrastructure for OAI-PMH-based adapters. Individual adapters (Axiell, FOLIO) extend this framework with their specific configuration.

## Architecture overview

```
EventBridge → Trigger → Loader → Transformer → Elasticsearch
                   ↑            ↓
            Window status   Iceberg record table
```

All OAI-PMH adapters follow this pattern:

1. **Trigger** inspects the window status table to determine the next harvesting window, enforces lag thresholds, and emits a loader request event with a fresh `job_id`.
2. **Loader** harvests the requested window via OAI-PMH, writes raw XML documents into the Iceberg record table, updates the window status table, and returns the Iceberg `changeset_id` values.
3. **Transformer** fetches the Iceberg rows referenced by each changeset, transforms them, and indexes into Elasticsearch.

## Step details

### Trigger (`steps/trigger.py`)

* Reads window execution history from the **window status table**.
* Computes the next `[window_start, window_end)` range using the most recent successful entry, defaulting to a configurable look-back if no history exists.
* Generates a canonical `job_id` (UTC `YYYYMMDDTHHMM`), embeds OAI metadata parameters, and publishes a loader event.
* Optionally enforces the maximum lag window before allowing the run to proceed.
* CLI flags: `--window-minutes`, `--lookback-days`, `--job-id`, `--at`, `--enforce-lag`.

### Loader (`steps/loader.py`)

* Receives the loader event and spins up a `WindowHarvestManager` with an OAI client plus a per-window `WindowRecordWriter` callback.
* For each harvested record, serialises the XML payload into the **Iceberg record table** under the adapter namespace and associates it with the current `job_id`.
* Updates the window status table with `pending/success/failed` states and attaches tags for `job_id`, `window_key`, and every Iceberg `changeset_id` produced.
* Returns a `LoaderResponse` containing window results and `changeset_ids`.

### Reloader (`steps/reloader.py`)

* Analyzes window coverage within a specified time range.
* Identifies any coverage gaps (missing or failed windows).
* Invokes the loader handler sequentially for each gap.
* Supports `--dry-run` mode to preview gaps without processing.

### State propagation summary

| Step | Inputs | Outputs | Persistent state |
| --- | --- | --- | --- |
| Trigger | Window status table, config | Loader event (job + window info) | Reads status only |
| Loader | Loader event, OAI feed | `LoaderResponse` + `changeset_ids` | Writes to Iceberg + window status |
| Transformer | `changeset_ids`, Iceberg rows | `TransformResult` + ES documents | Writes to Elasticsearch |

*`job_id`* threads through every payload so logs, metrics, and manifests can be correlated across steps.

## Running adapter steps locally

All commands run from `catalogue_graph/` using UV. Replace `{adapter}` with `axiell` or `folio`.

### 1. Trigger → produce a loader event

```bash
uv run python -m adapters.{adapter}.steps.trigger \
  --at 2025-11-17T12:15:00Z \
  --enforce-lag \
  > /tmp/{adapter}_loader_event.json
```

#### Backfilling large gaps

```bash
uv run python -m adapters.{adapter}.steps.trigger \
  --at 2025-11-22T09:00:00Z \
  --window-minutes 120 \
  --lookback-days 5 \
  --job-id backfill-{adapter}-20251122 \
  > /tmp/{adapter}_backfill_event.json
```

### 2. Loader → harvest records & emit changesets

```bash
uv run python -m adapters.{adapter}.steps.loader \
  --event /tmp/{adapter}_loader_event.json \
  > /tmp/{adapter}_loader_output.json
```

### 3. Transformer → index the new documents

```bash
uv run python -m adapters.{adapter}.steps.transformer \
  --changeset-id <changeset_id_from_loader> \
  --es-mode private
```

### 4. Reloader → fill coverage gaps

```bash
uv run python -m adapters.{adapter}.steps.reloader \
  --job-id gap-reload-20251202 \
  --window-start 2025-12-01T00:00:00Z \
  --window-end 2025-12-02T00:00:00Z \
  --use-rest-api-table
```

#### Dry-run mode

```bash
uv run python -m adapters.{adapter}.steps.reloader \
  --job-id check-gaps \
  --window-start 2025-12-01T00:00:00Z \
  --window-end 2025-12-02T00:00:00Z \
  --use-rest-api-table \
  --dry-run
```

### Common CLI flags

| Flag | Description |
| --- | --- |
| `--use-rest-api-table` | Use S3 Tables catalog instead of local SQLite |
| `--at` | Override the "current time" for window calculation |
| `--job-id` | Override the auto-generated job ID |
| `--window-minutes` | Duration of each harvesting window |
| `--lookback-days` | How far back to start if no history exists |
| `--enforce-lag` | Fail if lag exceeds threshold |
| `--dry-run` | (Reloader only) Preview gaps without processing |

## Environment prerequisites

* UV-managed virtual environment with the catalogue graph project synced
* AWS credentials for S3 Tables catalog (or use local fallback)
* Access to SSM parameters for OAI endpoint and token (adapter-specific)

## Available adapters

| Adapter | Metadata prefix | Set spec | Auth header | SSM token path |
| --- | --- | --- | --- | --- |
| [Axiell](../axiell/README.md) | `oai_marcxml` | `collect` | `Token` | `/catalogue_pipeline/axiell/oai_api_token` |
| [FOLIO](../folio/README.md) | `marc21_withholdings` | None | `Authorization` | `/catalogue_pipeline/folio/oai_api_token` |

See individual adapter READMEs for adapter-specific configuration and details.

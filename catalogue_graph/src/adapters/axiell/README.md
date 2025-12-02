# Axiell OAI-PMH Adapter

This adapter ingests records from the Axiell Collections OAI-PMH feed in 15-minute windows, persists raw payloads in Iceberg, and emits transformed records into the catalogue graph search indices.

## Workflow overview

```
EventBridge → Trigger → Loader → Transformer → Elasticsearch
                   ↑            ↓
            Window status   Iceberg record table
```

1. **Trigger** (`steps/trigger.py`) inspects the window status table to determine the next harvesting window, enforces lag thresholds, and emits a loader request event with a fresh `job_id`.
2. **Loader** (`steps/loader.py`) harvests the requested window via OAI-PMH, writes raw XML documents into the Iceberg record table, updates the window status table, and returns the Iceberg `changeset_id` values that contain the newly persisted records.
3. **Transformer** (`steps/transformer.py`) fetches the Iceberg rows referenced by each changeset, performs a dummy transform, and indexes the documents into Elasticsearch.

## Step details & state hand-offs

### Trigger

* Reads window execution history from the **window status table** (via `IcebergWindowStore`).
* Computes the next `[window_start, window_end)` range using the most recent successful entry, defaulting to a configurable look-back if no history exists.
* Generates a canonical `job_id` (UTC `YYYYMMDDTHHMM`), embeds OAI metadata parameters, and publishes an `AxiellAdapterLoaderEvent` via EventBridge.
* Optionally enforces the maximum lag window before allowing the run to proceed.
* For local runs, the trigger CLI exposes `--window-minutes` (window duration) and `--lookback-days` (fallback range when no successful windows exist). These default to `config.WINDOW_MINUTES` / `config.WINDOW_LOOKBACK_DAYS` but can be increased to sweep large gaps.

### Loader

* Receives the loader event and spins up a `WindowHarvestManager` with an OAI client plus a per-window `WindowRecordWriter` callback.
* For each harvested record, serialises the XML payload into the **Iceberg record table** under the `axiell` namespace and associates it with the current `job_id`.
* Updates the window status table with `pending/success/failed` states and attaches tags for `job_id`, `window_key`, and every Iceberg `changeset_id` produced during the run.
* Returns a `LoaderResponse` containing:
  * One `WindowLoadResult` per harvested window (state, attempt count, record IDs, and errors if any).
  * A deduplicated list of `changeset_ids` that the transformer must process next.

### Transformer

* Takes an `AxiellAdapterTransformerEvent` containing the `changeset_ids` emitted by the loader.
* Uses `AdapterSourceDataStore.get_records_by_changeset` to materialise rows for each changeset and converts them into search documents (dummy title, namespace, raw content).
* Bulk indexes the documents into the Elasticsearch index resolved by `config.ES_INDEX_NAME/PIPELINE_DATE` and records any per-document failures.
* Returns a `TransformResult` summarising the number of indexed documents, any errors, and the originating `job_id`.

### State propagation summary

| Step | Inputs | Outputs | Persistent state |
| --- | --- | --- | --- |
| Trigger | Window status table, config | `AxiellAdapterLoaderEvent` (job + window info) | Updates nothing; reads status only |
| Loader | Loader event, OAI feed, Iceberg table | `LoaderResponse` (`WindowLoadResult` + `changeset_ids`) | Writes raw records to Iceberg + updates window status |
| Transformer | Loader `changeset_ids`, Iceberg rows | `TransformResult` + Elasticsearch documents | Writes to Elasticsearch |

*`job_id`* threads through every payload so logs, metrics, and manifests can be correlated across lambdas.

## Running the steps locally

These commands run inside `catalogue_graph/` using UV (after `uv sync`). Each step accepts `--use-rest-api-table` to talk to the shared S3 Tables catalog; omit it to use the local Iceberg warehouse under `catalogue_graph/src/adapters/axiell/.local/`.

### 1. Trigger → produce a loader event

```bash
uv run python -m adapters.axiell.steps.trigger \
  --at 2025-11-17T12:15:00Z \
  --enforce-lag \
  > /tmp/axiell_loader_event.json
```

Inspect `/tmp/axiell_loader_event.json` to confirm the window, metadata prefix, and generated `job_id`.

#### Rerunning large or missed windows

If windows were skipped for several days (for example, during an outage) you can ask the trigger to produce a much larger catch-up request:

```bash
uv run python -m adapters.axiell.steps.trigger \
  --at 2025-11-22T09:00:00Z \
  --window-minutes 120 \
  --lookback-days 5 \
  --job-id backfill-axiell-20251122 \
  > /tmp/axiell_backfill_event.json
```

* `--window-minutes` controls how wide each harvesting window should be. The value is copied onto the loader event so the loader harvester uses the same duration.
* `--lookback-days` sets how far back to start if the window store has no successes; bump this to cover long gaps.

These overrides are only required when you want to deviate from the defaults baked into `config.py`. They are also useful when intentionally reloading historical data for diagnostics: point the loader at the emitted event file and it will replay the requested range without touching your production configuration.

### 2. Loader → harvest records & emit changesets

```bash
uv run python -m adapters.axiell.steps.loader \
  --event /tmp/axiell_loader_event.json \
  > /tmp/axiell_loader_output.json
```

The output file contains human-readable window summaries plus the `changeset_ids` array consumed by the transformer.

### 3. Transformer → index the new documents

```bash
uv run python -m adapters.axiell.steps.transformer \
  --changeset-id <changeset_id_from_loader> \
  --es-mode private
```

Repeat `--changeset-id` for multiple IDs. Provide `--job-id` to override the inherited value or `--use-rest-api-table` to switch Iceberg catalogs.

### 4. Reloader → fill coverage gaps

The reloader step analyzes window coverage within a specified time range and re-harvests any missing windows. This is primarily intended for local troubleshooting when windows have failed or been skipped.

```bash
uv run python -m adapters.axiell.steps.reloader \
  --job-id gap-reload-20251202 \
  --window-start 2025-12-01T00:00:00Z \
  --window-end 2025-12-02T00:00:00Z \
  --use-rest-api-table
```

The reloader will:
1. Query the window status table for the specified range
2. Identify any coverage gaps (missing or failed windows)
3. Invoke the loader handler sequentially for each gap
4. Return a summary of processed gaps with their loader responses

#### Dry-run mode

To preview which gaps would be reloaded without actually processing them:

```bash
uv run python -m adapters.axiell.steps.reloader \
  --job-id gap-preview-20251202 \
  --window-start 2025-12-01T00:00:00Z \
  --window-end 2025-12-02T00:00:00Z \
  --use-rest-api-table \
  --dry-run
```

#### Integration with WindowNotifier

When the trigger step detects coverage gaps, it sends a notification via AWS Chatbot (if configured). These notifications include a "Next Steps" section with a ready-to-run reloader command that spans from the first gap's start to the last gap's end. For example:

```bash
uv run python -m adapters.axiell.steps.reloader \
  --job-id 20251202T1200 \
  --window-start 2025-12-01T14:30:00Z \
  --window-end 2025-12-01T18:45:00Z \
  --use-rest-api-table
```

You can copy this command directly from the Slack notification to remediate the detected gaps.

#### Common scenarios

**Single gap from a failed window:**
```bash
uv run python -m adapters.axiell.steps.reloader \
  --job-id repair-failed-window \
  --window-start 2025-12-01T10:00:00Z \
  --window-end 2025-12-01T10:15:00Z \
  --use-rest-api-table
```

**Multiple gaps after a service outage:**
```bash
uv run python -m adapters.axiell.steps.reloader \
  --job-id backfill-outage-20251202 \
  --window-start 2025-12-01T08:00:00Z \
  --window-end 2025-12-01T20:00:00Z \
  --use-rest-api-table
```

**Preview gaps before reloading:**
```bash
# First, check what would be reloaded
uv run python -m adapters.axiell.steps.reloader \
  --job-id check-gaps \
  --window-start 2025-12-01T00:00:00Z \
  --window-end 2025-12-02T00:00:00Z \
  --use-rest-api-table \
  --dry-run

# Then run without --dry-run to actually reload
```

### Environment prerequisites

* UV-managed virtual environment with the catalogue graph project synced.
* Access to the required AWS Secrets Manager / SSM parameters for the OAI endpoint and API token if you are hitting the live feed.
* AWS credentials capable of reading/writing the target S3 Tables catalog (or rely on the local fallback).

When iterating locally it is common to:

1. Run the trigger once to capture a loader payload.
2. Re-run the loader multiple times while adjusting harvester behaviour (the window store prevents double-processing unless you opt into retries).
3. Point the transformer at the resulting `changeset_ids` to smoke-test the ES indexing flow.


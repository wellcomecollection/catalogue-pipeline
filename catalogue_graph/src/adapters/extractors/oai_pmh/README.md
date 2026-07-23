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
3. **Reconcile** (Axiell only, `steps/reconcile.py`) diffs each changeset's guid mappings against the reconciler store and records deletion facts before the completed event publishes. See [Axiell deletion reconciliation](../../transformers/README.md#axiell-deletion-reconciliation).
4. **Transformer** fetches the Iceberg rows referenced by each changeset, transforms them, and indexes into Elasticsearch.

## Step details

### Trigger (`steps/trigger.py`)

- Reads window execution history from the **window status table**.
- Computes the next `[window_start, window_end)` range using the most recent successful entry, defaulting to a configurable look-back if no history exists.
- Generates a canonical `job_id` (UTC `YYYYMMDDTHHMM`), embeds OAI metadata parameters, and publishes a loader event.
- Optionally enforces the maximum lag window before allowing the run to proceed.
- CLI flags: `--window-minutes`, `--lookback-days`, `--job-id`, `--at`, `--enforce-lag`.

### Loader (`steps/loader.py`)

- Receives the loader event and spins up a `WindowHarvestManager` with an OAI client plus a `RecordWriter` callback.
- For each harvested record, serialises the XML payload into the **Iceberg record table** under the adapter namespace and associates it with the current `job_id`.
- Updates the window status table with `pending/success/failed` states and attaches tags for `job_id`, `window_key`, and every Iceberg `changeset_id` produced.
- Returns a `LoaderResponse` containing window results and `changeset_ids`.
- Local CLI runs additionally support `--reprocess-successful-windows` and `--flush-every N` for backfills (see [Backfilling large gaps](#backfilling-large-gaps)).

### Reloader (`steps/reloader.py`)

- Analyzes window coverage within a specified time range.
- Identifies any coverage gaps (missing or failed windows).
- Invokes the loader handler sequentially for each gap.
- Supports `--dry-run` mode to preview gaps without processing.

### State propagation summary

| Step        | Inputs                        | Outputs                            | Persistent state                  |
| ----------- | ----------------------------- | ---------------------------------- | --------------------------------- |
| Trigger     | Window status table, config   | Loader event (job + window info)   | Reads status only                 |
| Loader      | Loader event, OAI feed        | `LoaderResponse` + `changeset_ids` | Writes to Iceberg + window status |
| Transformer | `changeset_ids`, Iceberg rows | `TransformResult` + ES documents   | Writes to Elasticsearch           |

_`job_id`_ threads through every payload so logs, metrics, and manifests can be correlated across steps.

## Running adapter steps locally

All commands run from `catalogue_graph/` using UV. Use `--adapter-type` to select the adapter.

### 1. Trigger → produce a loader event

```bash
uv run python -m adapters.steps.oai_pmh.trigger --adapter-type {axiell,folio} \
  --at 2025-11-17T12:15:00Z \
  --enforce-lag \
  > /tmp/{adapter}_loader_event.json
```

#### Backfilling large gaps

Use the trigger to build an event covering the gap:

```bash
uv run python -m adapters.steps.oai_pmh.trigger --adapter-type {axiell,folio} \
  --at 2025-11-22T09:00:00Z \
  --window-minutes 120 \
  --lookback-days 5 \
  --job-id backfill-20251122 \
  > /tmp/{adapter}_backfill_event.json
```

Then run the loader with the backfill options:

```bash
uv run python -m adapters.steps.oai_pmh.loader --use-cli --adapter-type {axiell,folio} \
  --event /tmp/{adapter}_backfill_event.json \
  --reprocess-successful-windows \
  --flush-every 50 \
  > /tmp/{adapter}_backfill_output.json
```

- `--reprocess-successful-windows` re-harvests windows already marked success. Use it when committed windows have gone stale, for example after a bulk load with backdated datestamps changed records inside windows that already succeeded.
- `--flush-every N` batches Iceberg commits: records and window statuses are committed once per N windows instead of once per window. Per-window commits on S3 Tables dominate backfill wall-clock time, so batching speeds large backfills up severalfold. The trade-off is crash recovery: a crash loses up to N windows of uncommitted fetch work, which is re-fetched on the next run. This is safe because records are committed before window statuses, so an interrupted flush never marks unharvested windows as successful.

Leave both flags off for normal incremental operation: default per-window commits give the tightest durability, and skipping already-successful windows is what keeps scheduled runs idempotent.

### 2. Loader → harvest records & emit changesets

```bash
uv run python -m adapters.steps.oai_pmh.loader --adapter-type {axiell,folio} \
  --event /tmp/{adapter}_loader_event.json \
  > /tmp/{adapter}_loader_output.json
```

### 3. Transformer → index the new documents

```bash
uv run python -m adapters.steps.transformer --transformer-type {axiell,folio} \
  --changeset-id <changeset_id_from_loader> \
  --es-mode private
```

### 4. Reloader → fill coverage gaps

```bash
uv run python -m adapters.steps.oai_pmh.reloader --adapter-type {axiell,folio} \
  --job-id gap-reload-20251202 \
  --window-start 2025-12-01T00:00:00Z \
  --window-end 2025-12-02T00:00:00Z \
  --use-rest-api-table
```

#### Dry-run mode

```bash
uv run python -m adapters.steps.oai_pmh.reloader --adapter-type {axiell,folio} \
  --job-id check-gaps \
  --window-start 2025-12-01T00:00:00Z \
  --window-end 2025-12-02T00:00:00Z \
  --use-rest-api-table \
  --dry-run
```

### 5. Loader (id mode) → fetch specific records by id

The loader runs in one of two modes. Window mode (step 2) harvests a time range.
Id mode takes an explicit list of record ids instead, for records the source
holds but the store is missing, typically because they were written with
datestamps inside windows that had already been harvested. Each id is fetched
via OAI `GetRecord` and written to the adapter store, committing in batches.

```bash
uv run python -m adapters.steps.oai_pmh.loader --use-cli \
  --adapter-type {axiell,folio} \
  --ids-file missing_ids.txt \
  --use-rest-api-table
```

In the pipeline, start the adapter state machine with `ids` in the input and it
routes to id mode automatically:

```json
{ "adapter_type": "axiell", "ids": ["collect:123", "collect:456"] }
```

Both modes emit changeset ids, so an id-mode run publishes the same
`adapter.completed` event as a harvest and the transformer picks the records up
without anyone having to find changeset ids by hand. For Axiell this also
triggers the reconciler, which is intended: recovered records need their
id-to-GUID mappings rebuilt.

Each id is classified as recovered, removed (the source reports
`idDoesNotExist`), or unfetchable (neither returned nor reported gone, after the
client's retries). Unfetchable ids are left absent from the store, never
backfilled with stale content. The report carries the full removed and
unfetchable id lists; the response carries only counts plus the report's S3
URI, matching the transformer and id minter responses.

A CLI run writes its report to a local file (`--report`, defaulting to
`<job-id>_report.json`) and emits no CloudWatch metrics, so an ad hoc recovery
leaves the production report bucket and dashboards alone. A pipeline run writes
the report to S3 and emits metrics.

Two deliberate differences from window mode:

- **Removed ids are reported, not tombstoned.** Window mode writes a `deleted`
  row when the repository reports `status="deleted"`. `idDoesNotExist` is a
  weaker claim that also fires for a typo in an id list or an id from another
  set, so acting on it would propagate a delete downstream on an unreliable
  signal.
- **No window state is written**, so a re-run re-fetches everything. Recording
  synthetic windows would shift the trigger's resume cursor onto a range that
  was never harvested. Because there is no progress state to resume from, the
  state machine does not retry an id run automatically: re-run it against the
  unfetchable ids from the report instead.

`commit_every` defaults to 10,000 rather than a small batch. Every changeset id
published costs the transformer roughly a full materialisation of the bib store,
so committing rarely keeps a large recovery down to a handful of changesets.
Runs are capped at 50,000 ids, and an empty id list is rejected rather than
treated as a no-op.

### Common CLI flags

| Flag                             | Description                                                    |
| -------------------------------- | -------------------------------------------------------------- |
| `--use-rest-api-table`           | Use S3 Tables catalog instead of local SQLite                  |
| `--at`                           | Override the "current time" for window calculation             |
| `--job-id`                       | Override the auto-generated job ID                             |
| `--window-minutes`               | Duration of each harvesting window                             |
| `--lookback-days`                | How far back to start if no history exists                     |
| `--enforce-lag`                  | Fail if lag exceeds threshold                                  |
| `--dry-run`                      | (Reloader only) Preview gaps without processing                |
| `--reprocess-successful-windows` | (Loader only) Re-harvest windows already marked success        |
| `--flush-every`                  | (Loader only) Commit records and statuses every N windows      |
| `--ids` / `--ids-file`           | (Loader id mode) Record ids to fetch, inline or one per line   |
| `--commit-every`                 | (Loader id mode) Records buffered before committing a batch    |
| `--report`                       | (Loader id mode) Path for the run report (full id lists)       |

## Environment prerequisites

- UV-managed virtual environment with the catalogue graph project synced
- AWS credentials for S3 Tables catalog (or use local fallback)
- Access to SSM parameters for OAI endpoint and token (adapter-specific)

## Available adapters

| Adapter                       | Metadata prefix       | Set spec  | Auth header     | SSM token path                             |
| ----------------------------- | --------------------- | --------- | --------------- | ------------------------------------------ |
| [Axiell](../axiell/README.md) | `oai_marcxml`         | `collect` | `Token`         | `/catalogue_pipeline/axiell/oai_api_token` |
| [FOLIO](../folio/README.md)   | `marc21_withholdings` | None      | `Authorization` | `/catalogue_pipeline/folio/oai_api_token`  |

See individual adapter READMEs for adapter-specific configuration and details.

## Running steps locally

All OAI-PMH adapter steps are invoked through the unified entrypoints with `--adapter-type`:

```bash
# Trigger — compute the next harvesting window
uv run python -m adapters.steps.oai_pmh.trigger --adapter-type {axiell,folio} [--at TIMESTAMP]

# Loader — harvest records for a window
uv run python -m adapters.steps.oai_pmh.loader --adapter-type {axiell,folio} --event EVENT_FILE

# Reloader — fill coverage gaps
uv run python -m adapters.steps.oai_pmh.reloader --adapter-type {axiell,folio} \
  --job-id JOB_ID --window-start START --window-end END [--dry-run]
```

See each adapter's README for adapter-specific configuration (auth, SSM params, env vars).

# Axiell to FOLIO sync

This step exports changed Axiell records to FOLIO Inventory. It is the FOLIO *outbound* (write) path: it reads changed
records from the Axiell Iceberg adapter table, maps their MARCXML to FOLIO Inventory payloads, and upserts them
(Instance → Holdings → Item) via the OKAPI REST API.

It is distinct from the [FOLIO adapter](../../extractors/oai_pmh/folio/README.md), which reads FOLIO records *into*
the pipeline, and from the FOLIO enrichment step (`steps/oai_pmh/folio_enrich.py`). Beyond the name "FOLIO" they share
nothing.

The design, record-selection rules and tombstone semantics are specified in
[RFC 090: Axiell to FOLIO sync](https://github.com/wellcomecollection/docs/tree/main/rfcs/090-axiell-folio-sync).

## How it works

1. The Axiell adapter finishes a run and emits an `axiell.adapter.completed` EventBridge event.
2. An EventBridge rule starts the sync Step Function, which invokes the Lambda
   (entrypoint `adapters.steps.axiell_folio_sync.axiell_folio_sync.lambda_handler`).
3. The step reads the changed rows for the event's `changeset_ids` through `AxiellChangesetReader` (using the same
   `AXIELL_CONFIG` as the Axiell adapter, so S3 Tables in Lambda and a local sqlite catalog for local runs). The
   reader also exposes the adapter's deletion facts via `iter_deletions()`, consumed by the reconciliation pass below.
4. **Pass 1 — upsert.** Each record is selected, mapped, and upserted:
   * **Selection** (`is_selected_for_sync`): a record is synced only if it carries the harvest flag (MARC `980 $a`
     present) and is item-level (MARC `351 $c` == `ITEM`). Everything else is skipped.
   * **Mapping** (`mapping.py`): MARCXML → typed Pydantic payloads for Instance, Holdings and Item. FOLIO reference
     UUIDs (locations, material types, loan types, …) are resolved via `RefCache`, loaded once per invocation from the
     FOLIO tenant and reused across warm Lambda starts.
   * **Upsert** (`upsert.py`): writes in Instance → Holdings → Item order via OKAPI, with best-effort rollback if a
     later entity write fails.
5. **Pass 2 — reconciliation deletes** (see [Reconciliation deletes](#reconciliation-deletes) below): the deletion
   facts from `iter_deletions()` are actioned against FOLIO *after* the upsert pass.
6. Results are published via a single `PipelineReport` (`report.py`): one JSON run report to S3 plus CloudWatch
   metrics (namespace `catalogue_adapters`; metrics are suppressed on dry runs, the S3 report is not).

## Reconciliation deletes

Two distinct delete signals reach this step; only one is authoritative.

**Loader tombstones (`deleted=true`) are advisory only and ignored.** The loader's `deleted` flag is unreliable, so we
record and metric the signal but never suppress or remove a FOLIO record based on it.

**Authoritative deletes come from the adapter-side reconcile step's deletion facts.** The reconcile step writes
superseded-GUID facts to Iceberg *before* `axiell.adapter.completed` fires, so the sync consumes them in the same
invocation — no separate event or Lambda (see RFC 090 and
[Axiell deletion reconciliation](../../transformers/README.md#axiell-deletion-reconciliation)). `iter_deletions()`
re-checks each fact against the current reconciler mappings and drops any GUID reclaimed by a live record, so a
revert/handoff never suppresses the wrong record. The remaining facts are actioned in Pass 2, keyed by
`AxC-{entity}-{guid}` (the same HRID scheme the upsert path writes), child-first **item → holdings → instance**:

- **Soft-suppress (default, reversible).** `suppress_by_guid` sets `discoverySuppress` on all three entities and
  `staffSuppress` on the instance only — instances are the sole FOLIO inventory entity with a `staffSuppress` field
  (holdings-storage rejects it with a 422; items silently drop it). Idempotent under redelivery.
- **Hard-delete (opt-in, irreversible).** `delete_by_guid`, selected by the event's `hard_delete` field or the
  `HARD_DELETE` env var. The child-first order is mandatory (FOLIO enforces referential integrity) and the cascade
  aborts before the parent if a child delete fails, so a parent is never orphaned. A 404 is treated as a no-op, so
  redelivered facts and races are safe.

A per-GUID failure is recorded as an error entry and does not abort the run.

## Module layout

| Module                 | Responsibility                                                       |
| ---------------------- | -------------------------------------------------------------------- |
| `axiell_folio_sync.py` | Lambda/ECS/CLI entrypoints; builds real dependencies                  |
| `sync_to_folio.py`     | OKAPI credential resolution and the select → map → upsert loop       |
| `mapper.py`            | MARCXML extraction primitives and the `CanonicalRecord` model        |
| `mapping.py`           | MARC → FOLIO payload mapping and record selection (single source of truth) |
| `ref_cache.py`         | Cache of FOLIO tenant reference-data UUIDs                           |
| `upsert.py`            | FOLIO Inventory write orchestration and rollback                     |
| `folio_callables.py`   | `FolioInventoryOps` protocol decoupling this package from the client |
| `models.py` / `report.py` | Step event/response/report-entry models; the S3 + CloudWatch run report |

The OKAPI HTTP client itself lives in [`clients/folio_client`](../../../clients/folio_client/).

## Dry run

The step is dry-run by default: payloads are built and upsert actions are planned and logged, but nothing is written
to FOLIO. Note that dry runs still *read* from FOLIO (reference data and existence lookups), so valid credentials are
required either way.

The default comes from the `DRY_RUN` env var (Terraform `dry_run_default`, currently `true`); an explicit `dry_run`
field on the event overrides it per run. Flip `dry_run_default` to `false` in Terraform only after validating a dry
run's manifest.

## Configuration

Environment variables (injected by Terraform in Lambda):

| Env var              | Description                                                          |
| -------------------- | -------------------------------------------------------------------- |
| `OKAPI_SECRET_PARAM` | SSM path to the OKAPI credentials SecureString                       |
| `MANIFEST_S3_BUCKET` | Bucket for JSON run reports                                          |
| `DRY_RUN`            | Default dry-run behaviour (`true` unless overridden by the event)    |

For local runs, `OKAPI_URL` / `OKAPI_TENANT` / `OKAPI_USERNAME` / `OKAPI_PASSWORD` override the corresponding SSM
fields (and skip SSM entirely if all four are set).

## SSM Parameters

| Parameter                                              | Description                                        |
| ------------------------------------------------------ | -------------------------------------------------- |
| `/catalogue_pipeline/axiell-folio-sync/okapi_credentials` | SecureString JSON: `url`, `tenant`, `username`, `password` |

The parameter is seeded with placeholders by Terraform; real values are set out-of-band.

## Run reports

Each run writes a single JSON report to the manifest bucket (`wellcomecollection-axiell-folio-sync-manifests`,
expiring after `manifest_retention_days`, default 90):

* `manifests/<job_id>.json` — counts, the successfully synced records with per-entity actions (`successful`), and
  per-record errors with the failing stage (`errors`)

The report is written on dry runs too, so a dry run can be validated before flipping `dry_run_default`. This uses the
shared `PipelineReport` machinery (`utils/reporting.py`), following the consolidated run-artefact convention
introduced for the transformers and ID minter in
[#3468](https://github.com/wellcomecollection/catalogue-pipeline/pull/3468).

## Running locally

```bash
# Dry run against 5 sample records from the production S3 Tables catalog
AWS_PROFILE=platform-developer uv run python -m adapters.steps.axiell_folio_sync.axiell_folio_sync \
  --use-cli --job-id local-test-1 --sample-limit 5 --use-rest-api-table

# Specific changesets; pass --live to disable dry run and write to FOLIO
AWS_PROFILE=platform-developer uv run python -m adapters.steps.axiell_folio_sync.axiell_folio_sync \
  --use-cli --job-id my-job-123 --changeset-ids 456429b2-6f0e-11f1-afea-525a8567ce81 --use-rest-api-table
```

Set the `OKAPI_*` environment variables first (see Configuration above).

## Infrastructure

Terraform lives in [`infra/adapters/modules/axiell_folio_sync`](../../../../infra/adapters/modules/axiell_folio_sync/):
the Lambda (running the shared `unified_pipeline_lambda` image), the Step Function, the EventBridge rule, the manifest
bucket, the SSM parameter, and IAM. Deploy code changes with:

```bash
./scripts/deploy_lambda.sh axiell-folio-sync-adapter-lambda
```

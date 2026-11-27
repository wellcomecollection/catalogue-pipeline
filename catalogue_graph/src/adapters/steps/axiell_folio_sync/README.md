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
   reader also exposes the adapter's deletion facts via `iter_deletions()`, and the read step derives the GUIDs of
   the batch's tombstones; both are consumed by the reconciliation pass below.
4. **Pass 1 — upsert.** Each record is selected, mapped, and upserted:
   * **Selection** (`is_selected_for_sync`): a record is synced only if it carries the harvest flag (MARC `980 $a`
     present) and is item-level (MARC `351 $c` == `ITEM`). An item-level record without the flag has its FOLIO
     records suppressed (`unflagged_guid`, then `suppress_by_guid`), never hard deleted, so unticking Harvest takes
     it out of FOLIO and ticking it again restores it: every upsert sends the suppression flags cleared. A record
     that is not item-level is skipped.
   * **Withheld records** (`_is_withheld`): a selected record that is not published to the web (MARC `981 $a` is
     `no`) or is ticked for deletion (MARC `985 $a`) is still synced, with `discoverySuppress` set on all three
     FOLIO records and `staffSuppress` left clear, so staff can work with it and the public catalogue does not show
     it. The two checkbox rules are the Axiell transformer's own (`transformers/axiell/publish_to_web.py`).
     Publishing the record clears the flag on the next run. These records are counted in `withheld`.
   * **Records that fail to map** (`unmapped_guid`): a selected record with a mapping error is not written, so FOLIO
     would keep the last good sync, which may no longer be true: the record may have been closed or withheld in the
     same edit. When a mandatory field is missing (title, material type or normal location), or the record is
     withheld, its existing FOLIO records are hidden from discovery (`hide_by_guid`) and stay visible to staff. The
     record has to be corrected in Collections, and the run that next maps it clears the flag. It is counted in
     `failed`, and in `unmapped_hidden` when something was hidden, and listed in the manifest's deletions with the
     reason `unmapped`. Other mapping errors hide nothing, because a value missing from a mapping table or from the
     FOLIO tenant may be a fault in the table, the tenant or a deploy, and could hide every synced record.
     A harvest that lost a mandatory field would fail every record this way, so a run hides at most 50 published
     records for a missing field (`MAX_MISSING_FIELD_HIDES`). The rest are left as they were, counted in
     `unmapped_hide_capped` and logged as errors. Records an earlier attempt already hid count towards the 50, so
     a retried run does not hide more. Withheld records are not capped.
   * **Where the sync and the Axiell transformer differ.** A record with no recognisable `981 $a` fails with a
     mapping error and its FOLIO records are left as they were: the stylesheet emits the marker on every record, so
     its absence is a harvest fault, and hiding on it could hide every synced record. The transformer suppresses
     such a record. The transformer also suppresses `AMSG` resource guides, which the sync does not check for.
   * **Mapping** (`mapping.py`): MARCXML → typed Pydantic payloads for Instance, Holdings and Item. FOLIO reference
     UUIDs (locations, material types, loan types, …) are resolved via `RefCache`, loaded once per invocation from the
     FOLIO tenant and reused across warm Lambda starts.
   * **Upsert** (`upsert.py`): writes in Instance → Holdings → Item order via OKAPI, with best-effort rollback if a
     later entity write fails. Each record's FOLIO id comes from its AxC GUID (MARC `001`, itself a UUID): the
     instance takes the GUID as its id, and the holdings and item take a UUIDv5 in the GUID. The id is sent on
     create, and the sync finds the record again by it on every later run. The sync sends no hrid, so FOLIO
     generates it. The GUID is not written anywhere else: staff reach it through the instance, whose id it is. The
     instance carries the `Synched by Axiell` instance status. On update, `identifiers` and `statisticalCodeIds` are
     merged, so entries added in FOLIO are kept.
5. **Pass 2 — reconciliation deletes** (see [Reconciliation deletes](#reconciliation-deletes) below): the deletion
   facts from `iter_deletions()` and the tombstoned GUIDs are actioned against FOLIO *after* the upsert pass.
6. Results are published via a single `PipelineReport` (`report.py`): one JSON run report to S3 plus CloudWatch
   metrics (namespace `catalogue_adapters`; metrics are suppressed on dry runs, the S3 report is not).

## Reconciliation deletes

A record hard deleted in Collections reaches this step in one of two ways, and both are actioned in Pass 2
(wellcomecollection/platform#6833).

**A tombstone, when the collectId is not reused.** The adapter writes `deleted=true` only when the OAI-PMH server
reports the record deleted, and keeps its last content. The read step (`tombstoned_guids`) takes the GUID from that
content's `001`, with the reconcile step's own `compute_guid`, and drops any GUID that another record still holds in
the reconciler store (`guids_held_by_other_records`), since acting on it would delete that record's FOLIO records. A
tombstone with no content cannot have been synced and is passed over. The tombstone row itself is never upserted.

**A deletion fact, when the collectId is reused for a new record.** The reconcile step writes
superseded-GUID facts to Iceberg *before* `axiell.adapter.completed` fires, so the sync consumes them in the same
invocation — no separate event or Lambda (see RFC 090 and
[Axiell deletion reconciliation](../../transformers/README.md#axiell-deletion-reconciliation)). `iter_deletions()`
re-checks each fact against the current reconciler mappings and drops any GUID reclaimed by a live record, so a
revert/handoff never suppresses the wrong record. The remaining facts, then the tombstoned GUIDs not already among
them, are actioned in Pass 2, keyed by
the FOLIO ids derived from the GUID (the same derivation the upsert path uses), child-first
**item → holdings → instance**:

- **Soft-suppress (code default, reversible).** `suppress_by_guid` sets `discoverySuppress` on all three entities and
  `staffSuppress` on the instance only — instances are the sole FOLIO inventory entity with a `staffSuppress` field
  (holdings-storage rejects it with a 422; items silently drop it). An entity whose flags are already set is reported
  as `already_suppressed` and not written, so a redelivered fact or a repeat untick does not bump `updatedDate`.
- **Hard-delete (irreversible, set in Terraform).** `delete_by_guid`, selected by the event's `hard_delete` field or
  the `HARD_DELETE` env var, which Terraform sets to `true`. The child-first order is mandatory (FOLIO enforces referential integrity) and the cascade
  aborts before the parent if a child delete fails, so a parent is never orphaned. A 404 is treated as a no-op, so
  redelivered facts and races are safe.

A per-GUID failure is recorded as an error entry and does not abort the run. Each manifest deletion entry carries a
`reason`, `superseded` or `tombstone`, and `tombstone_deletions` counts the tombstoned GUIDs within `deletions`.
Records suppressed in Pass 1 for want of the harvest flag appear in the same list with the reason `unflagged`, and
are counted in `unflagged` rather than `deletions`. Their suppressed entities are counted in `suppressed` along with
the second pass's, and also in `unflagged_suppressed`, so the two causes can be told apart. A cascade that only found
records already suppressed is left out of the manifest; in Pass 1 it is counted in `unflagged_already_suppressed` and
`skipped`.

## Module layout

| Module                 | Responsibility                                                       |
| ---------------------- | -------------------------------------------------------------------- |
| `axiell_folio_sync.py` | Lambda/ECS/CLI entrypoints; builds real dependencies          |
| `axiell_adapter_read.py` | Adapter-store read (changed rows + superseded-GUID deletions)      |
| `run_axiell_folio_sync.py` | The select → map → upsert loop over adapter rows                 |
| `mapping/`             | MARC → FOLIO payload mapping and record selection (single source of truth): `marc` (MARCXML primitives + `CanonicalRecord`), `config` (field table), `builders`, `payloads` |
| `folio/`               | FOLIO client seam: `okapi` (OKAPI credential resolution), `callables` (`FolioInventoryOps` protocol), `ref_cache` (tenant reference-data UUIDs) |
| `upsert/`              | FOLIO Inventory write layer: `entities` (single-entity ops), `writer` (create/update), `reconcile` (suppress/delete cascade) |
| `results.py`           | Upsert outcome models (`UpsertResult` / `GuidCascadeResult` …)       |
| `models.py` / `report.py` | Step event/response/report-entry models; the S3 + CloudWatch run report |

The OKAPI HTTP client itself lives in [`clients/folio_client`](../../../clients/folio_client/).

## Dry run

The step is dry-run by default: payloads are built and upsert actions are planned and logged, but nothing is written
to FOLIO. Note that dry runs still *read* from FOLIO (reference data and existence lookups), so valid credentials are
required either way.

The default comes from the `DRY_RUN` env var (Terraform `dry_run_default`, currently `false`, so scheduled runs write to the test environment); an explicit `dry_run`
field on the event overrides it per run. A run that targets `prod` is always a dry run, whatever the event or `DRY_RUN`
says, until production writes are switched on. Flip `dry_run_default` to `false` in Terraform only after validating a dry
run's manifest.

## Configuration

Environment variables (injected by Terraform in Lambda):

| Env var              | Description                                                          |
| -------------------- | -------------------------------------------------------------------- |
| `OKAPI_SECRET_PARAM` | SSM path to the OKAPI credentials SecureString                       |
| `OKAPI_TEST_SECRET_PARAM` | The same, for the EBSCO FOLIO test environment                  |
| `OKAPI_DEV_SECRET_PARAM` | The same, for the FOLIO dev sandbox (set only when it is enabled in Terraform) |
| `FOLIO_TARGET`       | `prod`, `test` or `dev` when the event names none; `test` in code and in Terraform |
| `MANIFEST_S3_BUCKET` | Bucket for JSON run reports                                          |
| `DRY_RUN`            | Default dry-run behaviour (`true` unless overridden by the event)    |
| `HARD_DELETE`        | Delete rather than suppress in Pass 2 (`false` in code, `true` in Terraform; the event overrides) |

For local runs, `OKAPI_URL` / `OKAPI_TENANT` / `OKAPI_USERNAME` / `OKAPI_PASSWORD` override the corresponding SSM
fields (and skip SSM entirely if all four are set).

## SSM Parameters

| Parameter                                              | Description                                        |
| ------------------------------------------------------ | -------------------------------------------------- |
| `/catalogue_pipeline/axiell-folio-sync/okapi_credentials` | SecureString JSON: `url`, `tenant`, `username`, `password` |
| `/catalogue_pipeline/axiell-folio-sync/okapi_credentials_test` | The same, for the test environment |
| `/catalogue_pipeline/axiell-folio-sync/okapi_credentials_dev` | The same, for the dev sandbox |

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
  --use-cli --job-id my-job-123 --changeset-ids <changeset-id> --use-rest-api-table
```

Set the `OKAPI_*` environment variables first (see Configuration above).

## Infrastructure

Terraform lives in [`infra/adapters/modules/axiell_folio_sync`](../../../../infra/adapters/modules/axiell_folio_sync/):
the Lambda (running the shared `unified_pipeline_lambda` image), the Step Function, the EventBridge rule, the manifest
bucket, the SSM parameter, and IAM.

Code changes reach the Lambda through CI: merging to main builds the image, and the catalogue pipeline deploy workflow
points the function at the `prod` tag along with the other adapter lambdas. To try unmerged code, push a `:dev` image
with `./scripts/deploy_lambda.sh axiell-folio-sync-adapter-lambda`; the next deploy from main replaces it.

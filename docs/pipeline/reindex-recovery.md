# Recovering a reindex

What to do when parts of a reindex fail or go missing. Everything here was learned during the migration-testing reindexes (wellcomecollection/platform#6445, wellcomecollection/platform#6505); the mechanisms are general.

## How work reaches the index

Works flow through works-source, works-identified (id-minter), matcher, merger and works-denormalised, then ingest windows carry them into works-indexed. The id-minter and the graph ingest are windowed: a scheduled tick covers a recent time slice (`indexed_at` in works-source for the id-minter, `state.mergedTime` in works-denormalised for ingest). Windows only ever cover the recent past, so a document whose timestamp falls in a span the ticks have already passed (a late transform, a redrive, a quiesce period) is never picked up, and nothing fails or alarms because no stage saw an error. Compare stage counts (source vs identified, denormalised vs indexed) to find gaps; do not rely on alarms.

## Rules that hold everywhere

- **Replay with a fresh `StartExecution`, never Step Functions redrive.** Redrive resumes a stale execution with stale inputs and interacts badly with `waitForTaskToken` steps.
- **Un-quiescing does not backfill.** If the pipeline was quiesced while live transformers kept writing works-source, those works keep their original `indexed_at` and no window will ever cover the quiesce span. After re-enabling, run an explicit id-minter window replay across the whole quiesce period as a standard step, then confirm works-source equals works-identified.
- **App logs are not in CloudWatch.** The transformer, graph extractor and ingestor log groups hold only the fluentbit sidecar; per-record errors go to the shared logging cluster (`service-logs-*`, secrets under `shared/logging/`). An empty CloudWatch filter is a false negative.

## Resuming a paused OAI harvest

The OAI trigger has a lag breaker: it refuses to run when the last successful window ended more than the configured max lag ago (default 360 minutes; `MAX_LAG_MINUTES` for Axiell, `FOLIO_MAX_LAG_MINUTES` for Folio). A harvest paused for longer than that trips the breaker on its first scheduled run after resuming. The trigger emits one window from the cursor to now, so a single successful run catches up in full; the options, in order of preference: resume within the breaker's limit of the last harvest activity so it never trips; or bump the relevant `*_MAX_LAG_MINUTES` env var on the trigger lambda's environment for one scheduled tick and revert it. The lambda event cannot disable the check; `--enforce-lag` is only a local CLI option.

## Replaying id-minter windows

The id-minter state machine accepts an explicit window and job id:

```json
{"job_id": "replay-s01", "window": {"start_time": "...", "end_time": "..."}}
```

Pass an explicit `job_id`: the generated one is minute-granular, and concurrent replays collide on their S3 report names. Windows filter on `indexed_at`, so slices can be cut to arbitrary timestamps. Size slices by estimated identifier count rather than work count (throughput is identifier-bound, roughly 7,500 ids a second, and identifier density varies from ~2 per work for Axiell to over 100 for dense archives); ~250k identifiers per invocation is a good slice, and concurrency 6 worked best. A driver that halves and re-queues a timed-out slice recovers without loss.

## Failed graph ingest windows: check before replaying

A FAILED ingest execution has usually already written its batch, because the bulk write happens before version-conflict errors raise. Sample ids from works-denormalised within the window's `mergedTime` range and count them in works-indexed before replaying anything; in the 2026-07-31 recovery, 14 of 16 failed windows turned out to be complete.

## Redriven merges miss ingest windows

works-denormalised documents carry the `mergedTime` of their original matcher batch, not the redrive time. So after a merger DLQ redrive, the ingest windows that would cover those documents have already run, and nothing later covers them. The fix is to re-match rather than re-ingest: re-inject the affected work ids on the id-minter output topic, which feeds the matcher (`pipeline_inject_messages.py`), producing a fresh merge with a current `mergedTime` that the scheduled windows then pick up.

## Searchability traps when verifying

- works-identified, works-denormalised and works-indexed are keyed by canonical work id; works-source is keyed like `Work[<scheme>/<value>]`. An mget by source id against the downstream indices always misses.
- `state.sourceIdentifier` and `otherIdentifiers` are `_source`-only in the pipeline indices; per-scheme counts need a full source scroll, not a query.
- Always run a positive control alongside any zero-result query; most "confirmed absent" results in the testing rounds came from querying unsearchable fields.

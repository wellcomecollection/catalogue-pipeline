# Scripts

## Rebuilding an adapter store (`rebuild_adapter.py`)

Rebuilds the Iceberg stores for an OAI-PMH adapter (Axiell or FOLIO) from a
fresh full snapshot. Use it to remove outdated or incorrect data that the
incremental harvest cannot: the windowed loader only adds and updates, so
records the source has stopped serving stay in the store forever.

The rebuild emits no downstream deletions. Records that vanished from the
source leave the store, but their works remain in any populated index and in
matcher state, so run it against a clean pipeline or wipe the pipeline's
downstream state first.

`--use-rest-api-table` targets the production S3 Tables catalog and needs the
platform-developer profile. Without it the script requires
`--skip-publish-event`, because a local rebuild would otherwise publish real
events carrying changeset ids that exist only on your machine.

### Steps

1. **Disable the adapter's trigger schedule in AWS**, so a windowed harvest
   cannot run concurrently and write conflicting data.

2. **For Axiell, decide what the Axiell→FOLIO sync should see.** The
   `axiell-folio-sync-adapter-axiell-completed` rule also matches
   `axiell.adapter.completed`, so publishing the rebuild changesets runs the
   outbound sync over the whole Axiell set. Disable it unless you want that.

3. **Run the script.** In order, it will:

   - Wipe the window status table and write a synthetic window row stamped
     with the current time. This is the cursor the trigger resumes from. It is
     written before the download, so changes arriving mid-rebuild are missing
     from the snapshot but fall inside the next trigger window.

   - Download every record to a local snapshot file, logging progress against
     the record count the endpoint reports. This takes hours (around 4 for
     Axiell). The file is moved into place only on success, so an interrupted
     download leaves nothing for a later run to trust, and has to start again.

   - *(FOLIO only)* Download items for non-deleted bibs to a second snapshot.

   - Wipe the adapter store and load the snapshot back in batches, each
     producing its own changeset id. Batching bounds the memory a single update
     needs, which would otherwise reach 50+ GB for FOLIO. It does not bound the
     downstream cost: every changeset read scans most of a just-rebuilt table,
     and each published event triggers a transformer run per wired pipeline.

   - *(Axiell only)* Wipe the reconciler store and the deletion facts store,
     then reconcile the batch changesets, rebuilding the GUID mapping baseline.
     Facts are read by changeset id, and the rebuild replaces every id, so
     facts written before it can never be delivered.

   - *(FOLIO only)* Wipe and repopulate the items store.

   - Publish an `adapter.completed` event per changeset, triggering transformer
     runs in every pipeline whose adapter trigger is enabled.
     `--publish-interval-seconds` paces that fan-out. A transformer run that
     times out is terminal for its changeset, so re-publish failed ones by hand.

4. **Re-enable the adapter trigger** and the sync rule. If the rebuild outran
   the adapter's lag threshold (6 hours by default), the first run trips the lag
   circuit breaker and keeps failing, since only a successful run advances the
   cursor. Raise `MAX_LAG_MINUTES` (`FOLIO_MAX_LAG_MINUTES`) for one run, then
   revert.

### Resuming

Snapshots are reused if they already exist, so re-running the same command
after a failure skips the downloads. The wipe-and-load phases always re-run,
minting fresh changeset ids, so a run that died anywhere after the downloads
recovers with a plain re-run. Two things to watch:

- A snapshot only reflects the source as of when it was taken, so judge for
  yourself whether an old one is still safe to rebuild from, or delete it to
  re-download.
- If a rebuild is abandoned after the download began, the window history was
  reset but the stores were not rebuilt, leaving the gap between disabling the
  trigger and the synthetic cursor uncovered. Re-cover it with the reloader.

### Wipe only

`--wipe-only` wipes the same stores the rebuild would (adapter store, plus
reconciler and deletion facts for Axiell, plus items for FOLIO) and stops:
no download, reload, reconcile or events. Use it when the clear and the
rebuild have to happen at different times, for example clearing ahead of a
bulk load in the source system and rebuilding once the load has landed.

It leaves the window store and harvest cursor alone, so disable the harvest
schedule first and keep it disabled until the rebuild: an incremental harvest
against a wiped store repopulates it with whatever the source serves. The
pre-wipe Iceberg snapshot ids are logged for time-travel rollback.

### Refresh in place

When the source's serialisation changes without moving record datestamps (a
stylesheet change, for example), the store only picks it up from a full
download, but a normal rebuild pauses harvesting for the whole download. Split
it instead, so the pause covers only the load:

1. `--download-only` writes the snapshot and touches nothing else: no store,
   window store or cursor. The adapter keeps harvesting meanwhile.
2. Disable the harvest schedule, back up the store if you want a rollback copy
   beyond Iceberg's snapshot retention, then run with `--merge-with-store`.
   Each record keeps whichever of the snapshot and the live store has the newer
   `last_modified` (the snapshot wins a tie), records only in the store are
   kept, and the merged rows replace the store. If a snapshot record appears
   twice, its newest copy is used, and a deletion taken from the snapshot keeps
   the live record's content, so the transformer can still emit it. For Axiell
   the reconciler and deletion facts are kept and the load is reconciled
   against them, so a guid change on a newer datestamp becomes a deletion
   fact (a tied datestamp keeps the old mapping, as a harvest would); with
   `--skip-publish-event` the script warns with the changesets that got facts,
   since nothing will deliver them. The window store and cursor are untouched,
   so re-enable the schedule afterwards with no lag override.

The merge is written to `<snapshot>.merged.parquet`. A re-run after a failed
load reuses it rather than merging against a half-loaded store, and a merge
older than the snapshot is refused. Once loaded it is renamed to `.loaded`, so
a later refresh merges afresh. If a run fails after the load, in reconcile or
while publishing, a re-run reloads under new changeset ids: any deletion facts
the failed run wrote stay tagged with its changesets, so deliver those by hand.

With `--skip-publish-event`, records edited after their last harvest but before
the pause come from the snapshot with the same datestamp a later harvest would
bring, which the store rejects as not newer. Transform those by id.

### Usage

```bash
# Axiell
uv run python scripts/rebuild_adapter.py \
  --adapter-type axiell \
  --use-rest-api-table \
  --snapshot-path /tmp/axiell.parquet

# FOLIO
uv run python scripts/rebuild_adapter.py \
  --adapter-type folio \
  --use-rest-api-table \
  --snapshot-path /tmp/folio.parquet \
  --folio-items-snapshot-path /tmp/folio_items.parquet

# Wipe without rebuilding
uv run python scripts/rebuild_adapter.py \
  --adapter-type axiell \
  --use-rest-api-table \
  --wipe-only

# Refresh in place: download while harvesting, then pause and merge
uv run python scripts/rebuild_adapter.py \
  --adapter-type axiell \
  --use-rest-api-table \
  --snapshot-path /tmp/axiell.parquet \
  --download-only
uv run python scripts/rebuild_adapter.py \
  --adapter-type axiell \
  --use-rest-api-table \
  --snapshot-path /tmp/axiell.parquet \
  --merge-with-store \
  --skip-publish-event
```

Pass `--skip-publish-event` to load the stores without triggering downstream
transformer runs, for testing or dry-run validation.

# Scripts

## Rebuilding an adapter store (`rebuild_adapter.py`)

Use this script to do a full rebuild of the Iceberg stores for an OAI-PMH adapter
(Axiell or FOLIO) from a fresh snapshot. You would typically do this to remove 
outdated or incorrect data which cannot be removed via the incremental harvest
path.

### Steps

1. **Disable the adapter trigger in AWS.** This stops the normal windowed harvest
   from running concurrently and writing data that could conflict with the rebuild.

2. **Run the script.** It will perform the following in order:

   - Wipe the window status table and write a synthetic window row stamped with the
     current time. This acts as a cursor: when the trigger is re-enabled it will
     pick up from this point and harvest anything that changed during the rebuild
     window. The row is written *before* downloading so that the timestamp is
     anchored to the start of the rebuild — any OAI-PMH changes that arrive after
     this point will be absent from the snapshot but will fall inside the next
     trigger window.

   - Download all records from the OAI-PMH endpoint and save them to a local
     snapshot file. The download can take a while (currently around 3 hours for
     Axiell). If the rebuild fails at a later step, the snapshot can be reused to
     resume without re-downloading. When resuming, the window wipe and cursor-reset
     are skipped — they ran during the original attempt and the cursor is already
     anchored to the correct time.

   - *(FOLIO only)* Download all items from the FOLIO API and save them to a
     separate local snapshot file.

   - Wipe the adapter store.

   - Load records back into the store in batches using incremental updates. Each
     batch produces a separate changeset ID. Loading in batches avoids the large
     memory footprint of a single all-at-once update (50+ GB with current FOLIO
     data), and means each transformer run stays within the 15-minute AWS Lambda
     time limit.

   - *(Axiell only)* Wipe the reconciler store and run the reconcile step across
     all batch changesets to populate it with new GUID mappings, used as a
     baseline for future runs.

   - *(FOLIO only)* Wipe the items store and populate it with new items in batches.

   - Publish an `adapter.completed` EventBridge event for each batch, triggering
     transformer runs in parallel.

3. **Re-enable the adapter trigger.** It will see the synthetic cursor and start
   harvesting from the time the rebuild began.

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
```

If the snapshot file already exists at `--snapshot-path`, the download is skipped
and the existing file is reused. The same applies to `--folio-items-snapshot-path`.
This makes it safe to re-run the script after a failure without starting over.

Pass `--skip-publish-event` to load the stores without triggering any downstream
transformer runs — useful for testing or dry-run validation.

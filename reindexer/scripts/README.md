# Reindexer scripts

Utilities for starting, monitoring, and operating catalogue reindexing and related pipeline tasks.

Python: 3.10 (see .python-version). Dependencies are defined in pyproject.toml and pinned in uv.lock.

Quick start with uv
- Install uv (macOS):
  - With Homebrew: brew install uv
  - Or script: curl -LsSf https://astral.sh/uv/install.sh | sh
- From this folder:
  - Install deps: uv sync
  - See CLI help: uv run <script.py> --help
  - Run a script: uv run <script.py> [args]

Common scripts
- start_reindex.py — start a reindex job
- get_reindex_status.py — show status of reindex operations
- pipeline_inject_messages.py — inject messages into the pipeline
- pipeline_storage_diff.py — compare items in pipeline storage
- fix_dangling_merges.py — fix or report dangling merges
- eventbridge.py — EventBridge-related helpers/operations
- concurrently.py — concurrency helper utilities

Notes
- uv will create/use a local .venv by default in this folder.
- Use --help on any script for available options.

## Usage

### start_reindex.py
Publishes reindex job messages to the reindexer's SNS topic, then scales up the
reindexer ECS service so it starts processing immediately. Supports interactive
prompts and flags.

Examples
- Interactive (prompts for source, destination, mode):
  - uv run start_reindex.py
- Complete reindex of Sierra into the catalogue pipeline:
  - uv run start_reindex.py --src sierra --dst catalogue --mode complete
- Partial reindex (you will be prompted for a record count):
  - uv run start_reindex.py --src sierra --dst catalogue --mode partial
- Specific records from a file (one ID per line, blank lines ignored):
  - uv run start_reindex.py --src sierra --dst catalogue --mode specific --input-file ./ids.txt
- Specific records typed in interactively:
  - uv run start_reindex.py --src sierra --dst catalogue --mode specific
- Reindex every source (and EventBridge targets) completely. calm is never
  fully scanned (see below), so --calm-input-file is required whenever the
  reindex touches it:
  - uv run start_reindex.py --src all --dst catalogue --mode complete --calm-input-file ./third_party_archives.txt
- Same, but skip Miro (see the Miro note below):
  - uv run start_reindex.py --src notmiro --dst catalogue --mode complete --calm-input-file ./third_party_archives.txt

Notes
- Valid sources: all, notmiro, ebsco, axiell, miro, sierra, mets, calm, tei.
  - `all` reindexes every source in `SOURCES` plus any EventBridge targets
    (`ebsco` and `axiell`).
  - `ebsco` and `axiell` are adapter sources: rather than scanning a table,
    they publish a `weco.pipeline.reindex.requested` event that re-runs the
    adapter transformer over the whole adapter store. The event carries a
    `job_id`, printed when it is sent, which is how the resulting transformer
    run is traced.
  - `notmiro` is the same as `all` but skips Miro. Miro is normally run
    separately, last, once everything else has gone through the
    matcher/merger -- running it at the same time as other sources risks
    creating spurious Image records (the script warns and asks for
    confirmation if you pick `all`).
- Modes: complete, partial, specific.
  - complete: reindexes every record for the source (DynamoDB segment scan).
  - partial: reindexes a sample; you'll be prompted for how many records.
  - specific: reindexes an explicit list of IDs, either typed in
    interactively or supplied via `--input-file`.
- calm never runs a full DynamoDB scan, regardless of mode or how it's
  invoked (directly with `--src calm`, or via the `--src all` / `--src
  notmiro` fan-out). Instead, `--mode complete` for calm always reads IDs
  from `--calm-input-file` and sends them as a specific-records reindex.
  The script fails fast (before touching any source) if `--calm-input-file`
  is missing, doesn't exist, or contains no valid IDs.
- `--input-file` (generic) vs `--calm-input-file` (calm-only): use
  `--input-file` for an ordinary `--mode specific` reindex of any source;
  use `--calm-input-file` specifically to satisfy calm's mandatory
  file-based reindex during a `--mode complete` run.
- If nothing is currently subscribed to the destination's reindexer output
  topic, the script warns and asks for confirmation -- it's easy to publish
  a reindex into the void otherwise.
- The script assumes the AWS role arn:aws:iam::760097843905:role/platform-developer.

`third_party_archives.txt` is the calm ID list used with `--calm-input-file`
above.
Some third-party archive records are not migrated from Calm to Axiell but still need to be available in the public catalogue. 
A list of these records was provided by Collection Information in July 2026, to be ingested into the pipeline alongside other data sources 
See https://github.com/wellcomecollection/platform/issues/6448

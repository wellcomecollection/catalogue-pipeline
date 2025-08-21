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
- reingest_for_display.py — trigger reingest for display pipeline
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
Trigger a reindex request to the reindexer. Supports interactive prompts and flags.

Examples
- Interactive (prompts for source, destination, mode):
  - uv run start_reindex.py
- Complete reindex of Sierra into the catalogue pipeline:
  - uv run start_reindex.py --src sierra --dst catalogue --mode complete
- Partial reindex (you will be prompted for a record count):
  - uv run start_reindex.py --src sierra --dst catalogue --mode partial
- Specific records from a file (one ID per line):
  - uv run start_reindex.py --src calm --dst catalogue --input-file ./ids.txt
- Run all sources (and EventBridge targets) completely:
  - uv run start_reindex.py --src all --dst catalogue --mode complete

Notes
- Valid sources include: all, notmiro, ebsco, miro, sierra, mets, calm, tei.
- Modes: complete, partial, specific. For specific you can supply IDs interactively or via --input-file.
- The script assumes the AWS role arn:aws:iam::760097843905:role/platform-developer.

### reingest_for_display.py
Reingest only the documents required for display (e.g. visible works/images) through the final stage of the pipeline.

Examples
- Reingest visible works already in the API index for a given pipeline date:
  - uv run reingest_for_display.py YYYY-MM-DD --type works --source api
- Reingest from the last pipeline stage (useful if API ingest/index has issues):
  - uv run reingest_for_display.py YYYY-MM-DD --type works --source last_stage
- Test a single image document by ID:
  - uv run reingest_for_display.py YYYY-MM-DD --type images --test-doc-id abcd1234

Flags
- --type: works | images (default: works)
- --source: api | last_stage (default: api)
- --test-doc-id: Reingest a single document ID instead of all matching documents

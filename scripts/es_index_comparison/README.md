# es_index_comparison

A Python CLI (uv project) to fetch documents from two Elasticsearch indices, materialize them locally (NDJSON gzip + Parquet shards), and compute deep, field-level diffs with flexible ignore patterns. Multiple analyses can be run side-by-side—each with its own YAML config and output namespace.

## Features
- Fetch only (read-only) from Elasticsearch using cloud_id + api_key (never writes back).
- Optional filter query to restrict scanned documents.
- Chunked Parquet materialization (polars + pyarrow) for efficient re-use.
- Deep recursive diff (dicts, lists, scalars, numpy arrays) with null-vs-missing equivalence.
- Ignore field patterns with wildcards (`*`, `**`), list index wildcards (`[]`), and descendant matching.
- Export artifacts: JSONL diffs, CSV summary, field frequency JSON, and diff metadata.
- Sampled human-readable diff display & per-document diff inspection.
- Multiple independent analyses distinguished by namespace (auto or user-provided).

## Installation (using uv)

```bash
cd es_index_comparison
uv sync  
uv run es-index-compare --help
```

## Quick Start
```bash
# 1. Set secrets (never stored in config)
# You can create an API key in Elastic Cloud console
export ES_CLOUD_ID="<your-cloud-id>"
export ES_API_KEY="<your-api-key>"

# 2. Run full pipeline with example config
uv run es-index-compare run-all --config es_index_comparison/configs/example_analysis.yaml

# 3. Inspect a single document's diffs
uv run es-index-compare show-diff --config es_index_comparison/configs/example_analysis.yaml \
  --id "Work[ebsco-alt-lookup/ebs28842402e]"

# 4. Change ignore fields or add patterns in YAML then re-run only compare
uv run es-index-compare compare --config es_index_comparison/configs/example_analysis.yaml
```

## Environment Variables
Set secrets via env (recommended):
- `ES_CLOUD_ID` – Elastic Cloud deployment ID (no default)
- `ES_API_KEY` – Elastic API key (no default)

CLI flags can override these, but values are never stored in config artifacts.

## YAML Config Schema
Example `configs/my_analysis.yaml`:
```yaml
# REQUIRED: exactly two indices
indexes:
  - works-source-2025-10-02
  - works-source-2025-10-06

# OPTIONAL: ES query DSL (object). Omit for match_all
filter_query:
  query:
    term:
      state.sourceIdentifier.identifierType.id: ebsco-alt-lookup

# OPTIONAL: patterns for paths to ignore in diffs
ignore_fields:
  - version
  - state.modifiedTime
  - state.sourceModifiedTime
  - data.production[].dates[].range.from
  - data.production[].dates[].range.to

# OPTIONAL settings
sample_size: 10              # number of docs to sample when printing diffs
loading_chunk_size: 100000   # rows per Parquet shard flush
namespace: pipeline-comparison-ebsco # If omitted, auto: <basename>-<YYYYMMDD-HHMMSS>
output_dir: data             # base output root (default: data)
```

## Ignore Pattern Language
Token examples:
- `state.version` exact
- `state.*.modifiedTime` one segment wildcard
- `state.**.modifiedTime` multi-depth wildcard
- `data.production[].dates[].range.from` list index insensitive
Any match also ignores deeper descendants beneath that path.

## Generated Directory Layout
```
<output_dir>/<namespace>/
  raw/
    <index>.ndjson.gz
  parquet/
    <index>/
      0.parquet
      100000.parquet
      final.parquet
  diffs/
    diffs.jsonl
    diff_summary.csv
    field_frequency.json
    diff_meta.parquet
```

## Commands
All commands require a config file. Use `run-all` for the full pipeline.

```bash
es-index-compare run-all --config configs/my_analysis.yaml

# Individual stages
es-index-compare fetch --config configs/my_analysis.yaml
es-index-compare convert --config configs/my_analysis.yaml
es-index-compare compare --config configs/my_analysis.yaml

# Show diff for a single doc id (after compare)
es-index-compare show-diff --config configs/my_analysis.yaml --id "Work[ebsco-alt-lookup/ebs28842402e]"
```

### Flags (common)
- `--config PATH` (required)
- `--cloud-id` / `--api-key` (override env)
- `--namespace` (override YAML / auto)
- `--output-dir` (override YAML)

## Safety & Read-Only Assurance
The tool only calls Elasticsearch `GET`/`_search` via scan/scroll helpers; it performs no writes, index creations, or updates.

## Performance Tips
- Increase `loading_chunk_size` for very large indices if memory permits.
- Polars operations are columnar; ensure adequate disk space for Parquet.
- Re-run `compare` without `fetch`/`convert` after adjusting ignore patterns.

## Exit Codes
- `0` success
- `1` configuration / validation error
- `2` runtime/IO error

## License
Apache-2.0

## Future Enhancements (Ideas)
- Optional bloom/fast sets for very large ID comparisons.
- Parallel shard fetch.
- Output HTML diff report.

---
Happy comparing! 🧪

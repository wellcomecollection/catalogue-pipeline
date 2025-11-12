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
# 1. Copy the example source configuration and fill in secrets locally
cp es_index_comparison/configs/source_configration.example.yaml \
  es_index_comparison/configs/source_configuration.yaml
#   Edit the new file with cluster cloud IDs and API keys (never committed)

# 2. Run full pipeline with the default analysis config (configs/analysis.yaml)
uv run es-index-compare run-all

# 3. Inspect a single document's diffs
uv run es-index-compare show-diff --id "Work[ebsco-alt-lookup/ebs28842402e]"

# 4. Change ignore fields or add patterns in YAML then re-run only compare
uv run es-index-compare compare

# If fetch/convert already generated a namespace and you want to reuse it:
uv run es-index-compare compare --namespace analysis-20250115-103000

# Or set a custom namespace up front so every stage shares the same directory:
uv run es-index-compare fetch --namespace pipeline-reindex-audit
uv run es-index-compare convert --namespace pipeline-reindex-audit
uv run es-index-compare compare --namespace pipeline-reindex-audit
```

## Source Configuration
All cluster credentials now live in `configs/source_configuration.yaml`, which is deliberately
ignored by Git. The example file `source_configration.example.yaml` shows the structure:

```yaml
clusters:
  production:
    cloud_id: "<prod-cloud-id>"
    api_key: "<prod-api-key>"

index_sources:
  prod-works-source:
    cluster: production
    index: works-source-2025-10-02
```

Analysis configs reference the identifiers (`prod-works-source`) instead of raw index names. The
CLI will resolve credentials by looking up the cluster definition for each index source. Use
`--config` to point at a different analysis file (defaults to `configs/analysis.yaml`). Use
`--source-config` to pass a non-default credentials file; otherwise the tool expects the file next
to the analysis config.

## YAML Config Schema
Example `configs/my_analysis.yaml`:
```yaml
# REQUIRED: exactly two index sources (see source_configuration.yaml)
index_sources:
  - prod-works-source
  - stage-works-source

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
es-index-compare run-all  # uses configs/analysis.yaml and sibling source_configuration.yaml by default

# Individual stages
es-index-compare fetch
es-index-compare convert
es-index-compare compare

# Reuse artifacts from a previous fetch/convert run
es-index-compare compare --namespace analysis-20250115-103000

# Show diff for a single doc id (after compare)
es-index-compare show-diff --id "Work[ebsco-alt-lookup/ebs28842402e]"
```

### Flags (common)
- `--config PATH` (optional; defaults to `configs/analysis.yaml` located beside the CLI project)
- `--source-config PATH` (optional override; defaults to `<config-dir>/source_configuration.yaml`)
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

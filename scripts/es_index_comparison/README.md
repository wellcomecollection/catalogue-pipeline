# es_index_comparison

A Python CLI (uv project) to fetch documents from two Elasticsearch indices, materialize them locally (NDJSON gzip + Parquet shards), and compute deep, field-level diffs with flexible ignore patterns. Multiple analyses can be run side by side, each with its own YAML config and output namespace.

## Features
- Fetch only (read-only) from Elasticsearch using an api_key with a cloud_id, or a host URL for Serverless projects (never writes back).
- Optional filter query to restrict scanned documents.
- Hash-bucketed Parquet files (polars), so the comparison loads one bucket at a time and single buckets can be rerun.
- Deep recursive diff (dicts, lists, scalars) with null-vs-missing equivalence.
- Order-insensitive comparison for chosen lists (`ignore_list_order`).
- Ignore field patterns with wildcards (`*`, `**`), list index wildcards (`[]`), and descendant matching.
- Output files: every diff as JSONL, a count of differing documents per top-level field, and a Markdown sample of differing documents.
- Per-document diff inspection with `show-diff`.
- One progress log line per bucket during compare.
- Multiple independent analyses distinguished by namespace (auto or user-provided).

## Installation (using uv)

```bash
cd scripts/es_index_comparison
uv sync
uv run es-index-compare --help
```

## Quick Start
```bash
# 1. Copy the example source configuration and fill in secrets locally
cp configs/source_configuration.example.yaml configs/source_configuration.yaml
#   Edit the new file with cluster cloud IDs or host URLs, and API keys (never committed)

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
All cluster credentials live in `configs/source_configuration.yaml`, which is deliberately
ignored by Git. The example file `source_configuration.example.yaml` shows the structure:

```yaml
clusters:
  production:
    cloud_id: "<prod-cloud-id>"
    api_key: "<prod-api-key>"
  serverless:
    host: "https://<project>.es.<region>.aws.elastic.cloud:443"
    api_key: "<serverless-api-key>"

index_sources:
  prod-works-source:
    cluster: production
    index: works-source-2026-09-30
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

# OPTIONAL: per-side queries keyed by index source, overriding filter_query
# for that side. Use when the two sides need different filters, e.g. comparing
# works that changed source system but kept their document ids.
filter_queries:
  prod-works-source:
    query:
      term:
        state.sourceIdentifier.identifierType.id: calm-record-id
  stage-works-source:
    query:
      term:
        state.sourceIdentifier.identifierType.id: axiell-guid

# OPTIONAL: restrict both sides to an explicit id population, one id per line
# (blank lines and # comments skipped), instead of embedding ids in this file.
# Relative paths resolve against this config's directory. ids_format wraps
# each line into a document id. ANDed with filter_query/filter_queries.
ids_file: ../../../reindexer/scripts/third_party_archives.txt
ids_format: "Work[calm-record-id/{}]"

# OPTIONAL: patterns for paths to ignore in diffs
ignore_fields:
  - version
  - state.modifiedTime
  - state.sourceModifiedTime
  - data.production[].dates[].range.from
  - data.production[].dates[].range.to

# OPTIONAL: lists to compare as unordered, in the same pattern language as
# ignore_fields. A matching list, and every list nested inside it, is sorted on
# both sides before the diff. Use "**" for every list.
ignore_list_order:
  - data.contributors
  - data.subjects[].concepts

# OPTIONAL settings
sample_size: 10                # number of differing docs written to sample_diffs.md
loading_chunk_size: 100000     # docs read into memory at a time during convert
hash_bucket_count: 6           # number of hash buckets the Parquet files are split into
namespace: pipeline-comparison-ebsco # If omitted, auto: <basename>-<YYYYMMDD-HHMMSS>
output_dir: data               # base output root (default: data)
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
    <index source>.ndjson.gz
  parquet/
    <index source>/
      bucket_00000/
        part-00000.parquet
      bucket_00001/
        part-00000.parquet
      manifest.json
  diffs/
    diffs.jsonl
    field_frequency.json
    sample_diffs.md
```

## Commands
Every command reads a config file, `configs/analysis.yaml` unless `--config` says otherwise. Use `run-all` for the full pipeline.

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

# Convert/compare only specific hash buckets (speeds up targeted reruns)
es-index-compare convert --bucket 3
es-index-compare compare --bucket 3 --bucket 4
```

### Flags (common)
- `--config PATH` (optional; defaults to `configs/analysis.yaml` located beside the CLI project)
- `--source-config PATH` (optional override; defaults to `<config-dir>/source_configuration.yaml`)
- `--namespace` (override YAML / auto)
- `--output-dir` (override YAML)
- `--hash-buckets` (override YAML `hash_bucket_count` when running fetch/convert/compare)
- `-b/--bucket` (repeatable; restrict convert/compare/run-all to one or more bucket IDs)

## Safety & Read-Only Assurance
The tool only calls Elasticsearch `GET`/`_search` via scan/scroll helpers; it performs no writes, index creations, or updates.

## Performance Tips
- `loading_chunk_size` controls peak memory during convert: smaller values use less memory and write more files.
- `hash_bucket_count` controls peak memory during compare: more buckets mean fewer documents loaded at a time. The default is 6.
- Use `-b/--bucket` to rerun only the partitions that matter (ideal for debugging or partial rechecks).
- Re-run `compare` without `fetch`/`convert` after adjusting ignore patterns.

## Methodology notes from the migration testing rounds

Practice from the round 1 and round 2 comparisons (wellcomecollection/platform#6464, wellcomecollection/platform#6507). The committed `6464_*` and `6507_*` configs are working examples.

The comparison that matters is the Axiell one (`*_axiell_calm_identified_full`), because it checks works that changed source system, from Calm records to Axiell records, against the production versions they replace. The METS, Miro, TEI and EBSCO configs compare the same source data through the same transformer code on both sides; they are optional safety checks for regressions, and matching per-source index counts between the pipelines is normally enough to skip them.

### Always run a positive control

Before trusting any zero-result query, run a query you know matches against the same index and field. Most "confirmed absent" results during the testing rounds came from querying unsearchable fields.

- `state.sourceIdentifier` and `otherIdentifiers` are `_source`-only in the pipeline indices.
- works-source is searchable via `query_string`; the downstream indices only via their `query.*` fields.
- Source-filtered search responses return dotted keys (`_source["query"]["identifiers.value"]`), not nested objects.

### Know the index keys

works-identified, works-denormalised and works-indexed are keyed by canonical work id; works-source by `Work[<scheme>/<value>]`. An mget by source id against a downstream index always misses. Canonical ids for works absent from production re-mint on every id-minter respin, so pin populations with an `ids_file` (resolved relative to the config directory; see `generate_6507_ids.py` for reproducing one from a source config) rather than baking canonical ids into configs.

### Expected noise fields

Every cross-cluster run needs these in `ignore_fields`, or every document diffs: `indexed_at` (per-run ingest stamp), `version` (processing counter), `state.modifiedTime` (per-run transform stamp). Two more appear as diffs but are model differences rather than data: `state.removedInternalWorkStubs` exists only on pipelines built after the field was added (null vs `[]` or missing-key diffs on every document), and `state.sourceModifiedTime` can differ in serialization precision (microseconds vs milliseconds) between transformer generations. Post-filter these from `diffs.jsonl` rather than editing committed configs whose ignore lists document a past round.

### Deleted works legitimately differ

A work that is `Deleted` in both clusters can still diff on `state.internalWorkStubs`: a long-lived pipeline retains pre-deletion stubs (the merger needs them to delete already-minted inner works) while a freshly reindexed pipeline never minted those inner works and holds none, so the diff reflects representation rather than lost data.

### Size buckets for the corpus

The compare loads one hash bucket per side into memory. On the METS works-source corpus (~365k docs of full pipeline `_source`) the default `hash_bucket_count: 6` repeatedly exhausted memory and stalled for hours; raise the bucket count for large corpora so each bucket fits comfortably. Membership counts (`only_in_a`/`only_in_b`) stream in the progress log per bucket, so a killed run still yields a usable membership verdict.

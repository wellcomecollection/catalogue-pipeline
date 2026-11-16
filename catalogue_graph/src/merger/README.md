# Merger

An experiment in resolving the matcher graph as a scheduled batch step. It runs alongside
the Scala matcher and merger, which continue to be authoritative in production. Every run
reads the graph columns of every row in the `works_identified` Iceberg table (the
append-only table the id minter writes to, defined in `id_minter/schemata.py`), computes
connected components over merge-candidate links, and compares them with the components
the previous run stored in S3.

Only the matching half exists. The step writes which works it would re-merge and keeps
its own baseline, so the output can be checked against what the Scala merger re-merged
over the same period. It does not write to a downstream index.

* `matcher.py` loads the graph columns, keeps the current row of every work (highest
  `version`, then latest `last_modified`) and labels every work with its component id, 
  the smallest id among the works it is linked to. Links through Deleted works are ignored.
* `components.py` reads and writes `components.parquet` (id, component_id, last_modified)
  and finds the changed components: any component whose members, or the times the id
  minter last wrote them, differ from the previous run's component with the same id.
  Write time rather than work version, because a re-transform at the same version
  still has to reach the denormalised index.
* `steps/merger.py` is the Lambda and CLI entry point. It writes the changed components to
  `graph-{graph_date}/pipeline-{pipeline_date}/merger/full/job-{job_id}/changed_components.parquet`,
  then replaces `graph-{graph_date}/pipeline-{pipeline_date}/merger/components.parquet`
  with the full resolved graph. The first run has no baseline and reports every work as
  changed. Overlapping runs can report a work more than once, as each reads the baseline
  at the start and writes it at the end, but never miss one.

## Running locally

```bash
uv run python -m merger.steps.merger --pipeline-date dev --graph-date dev
```

This reads the id minter's local Iceberg table `matcher.works_identified` and writes to
S3 under the dev prefix. Pass `--use-rest-api-table` to read the S3 Tables table instead;
its name is set per pipeline with `WORKS_IDENTIFIED_TABLE_NAME`.

# Merger

Replaces the Scala matcher and merger with one scheduled batch step. Every run reads the
whole `works_identified` Iceberg table (written by the id minter), computes connected
components over merge-candidate links, and compares them with the components the previous
run left in S3 to find the ones that need merging again.

* `matcher.py` loads the graph columns and labels every work with its component id, the
  smallest id among the works it is linked to. Links through Deleted works are ignored.
* `components.py` reads and writes `components.parquet` (id, component_id, last_modified)
  and finds the changed components: any component whose members, or the times the id
  minter last wrote them, differ from the previous run's component with the same id.
  Write time rather than work version, because a re-transform at the same version
  still has to reach the denormalised index.
* `steps/merger.py` is the Lambda and CLI entry point. It writes the changed components to
  `graph-{graph_date}/pipeline-{pipeline_date}/merger/full/job-{job_id}/changed_components.parquet`.

Merging the changed components with the ported rules, writing the denormalised index and
updating the stored components are the next piece. With no previous components every
component is changed, which is also how a full reindex runs.

## Running locally

```bash
uv run python -m merger.steps.merger --pipeline-date dev --graph-date dev
```

This reads the local Iceberg table `matcher.works_identified` in `.local/matcher_catalog.db`
(built by `notebooks/matcher_identified_to_iceberg.ipynb`) and writes to S3 under the dev
prefix. Pass `--use-rest-api-table` to read the S3 Tables table instead.

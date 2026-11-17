# Merger

An experiment in matching and merging works as a scheduled batch step. It runs alongside
the Scala matcher and merger, which continue to be authoritative in production. Every run
reads the graph columns of every row in the `works_identified` Iceberg table (the
append-only table the id minter writes to, defined in `id_minter/schemata.py`), computes
connected components over merge-candidate links, and compares them with the components
the previous run stored in S3. Every component that changed is merged again, and the
merged works and initial images are written to indices of their own,
`works-denormalised-python-{date}` and `images-initial-python-{date}`, so they can be
compared with what the Scala merger wrote over the same period.

* `matcher.py` loads the graph columns, keeps the current row of every work (highest
  `version`, then latest `last_modified`) and labels every work with its component id,
  the smallest id among the works it is linked to. Links through Deleted works are ignored.
* `components.py` reads `components.parquet` (id, component_id, last_modified), finds the
  changed components (any component whose members, or the times the id minter last wrote
  them, differ from the previous run's component with the same id) and splits them into
  groups of about 50,000 works. Write time rather than work version, because a
  re-transform at the same version still has to reach the denormalised index.
* `documents.py` reads the documents of a group's works in one pass over the table.
* `merge.py` merges one component. It first splits off bibs linked only through a shared
  Miro work, and audiovisual e-bibs with the works linked to them, then chooses a target
  in each part and merges every field into it with the rules in `rules/`. Sources a rule
  claimed are redirected to the target. A TEI work's internal works are emitted beside
  it, and deleted when their stub goes.

The batch merger state machine runs these steps after every id-minter run:

1. `steps/match.py` (Match) writes the changed components, with their group, to
   `graph-{graph_date}/pipeline-{pipeline_date}/merger/full/job-{job_id}/changed_components.parquet`
   and this run's components to `components.parquet` beside it. It returns the number
   of groups, and the Map state builds each group's event from the run's input, the job
   id and the snapshot id.
2. `steps/merge_group.py` (MergeGroup, one invocation per group) merges the group's
   components and writes the results. Every document is versioned by the commit time of
   the snapshot the run read, with `external_gte`, so a run never overwrites one at a
   later snapshot. A component that cannot be read, merged or indexed fails on its own
   and the rest of the group carries on; the failed ones are listed in
   `failed_components-{group}.parquet` in the job folder.
3. `steps/commit.py` (Commit) replaces
   `graph-{graph_date}/pipeline-{pipeline_date}/merger/components.parquet`, the baseline
   the next run compares with, with the run's components less the failed ones, and
   deletes the run's copy. A failed component is then missing from the baseline, so the
   next run sees it as changed and merges it again. If any failed, the execution fails
   after the commit, so the alarm reports them while the rest of the catalogue moves on.
   An error that is not about one component, such as Elasticsearch being unreachable,
   fails the run before the commit, and the next run merges everything again.

The first run has no baseline and merges every component. Overlapping runs can merge a
component more than once, as each reads the baseline at the start, but never miss one.

## Running locally

```bash
uv run python -m merger --pipeline-date dev --graph-date dev
```

`merger/__main__.py` runs the same steps in order. It reads the id minter's local Iceberg table `matcher.works_identified`, merges every
changed group into a local Elasticsearch, and replaces the baseline under the dev prefix
in S3. Pass `--match-only` to stop after finding the changed components, `--es-mode
public` to write to the pipeline cluster, and `--use-rest-api-table` to read the S3
Tables table instead; its name is set per pipeline with `WORKS_IDENTIFIED_TABLE_NAME`.

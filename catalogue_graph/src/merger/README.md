# Merger

An experiment in matching and merging works as a scheduled batch step. It runs alongside
the Scala matcher and merger, which continue to be authoritative in production. Every run
reads the graph columns of every row in the `works_identified` Iceberg table (the
append-only table the id minter writes to, defined in `id_minter/schemata.py`), computes
connected components over merge-candidate links, and compares them with the components
the previous run stored in S3. Every component that changed is merged again, and the
merged works and initial images are written to dated `works-denormalised` and
`images-initial` indices of their own, so they can be compared with what the Scala merger
wrote over the same period. The pipeline passes their date in `index_dates`, and the
batch merger's API key can write to those two indices only.

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
  in each part and merges every field into it with the rules in `rules/`. Source works claimed
  by a rule are redirected to the target. A TEI work's internal works are output
  alongside it, and deleted when the TEI work is deleted or no longer lists them.

The batch merger state machine runs these steps after every id-minter run:

1. `steps/match.py` (Match) writes the changed components, with their group numbers, to
   `graph-{graph_date}/pipeline-{pipeline_date}/merger/full/job-{job_id}/changed_components.parquet`
   and this run's components to `components.parquet` beside it. It returns the number
   of groups, and the Map state builds each group's event from the run's input, the job
   id and the snapshot id.
2. `steps/merge_group.py` (MergeGroup, one invocation per group) merges the group's
   components and writes the results. Every document is versioned by the commit time of
   the snapshot the run read, with `external_gte`, so a run never overwrites one at a
   later snapshot. A component that cannot be read, merged or indexed fails on its own
   and the rest of the group carries on. The failed ones are listed in
   `failed_components-{group}.parquet` in the job folder.
3. `steps/commit.py` (Commit) replaces
   `graph-{graph_date}/pipeline-{pipeline_date}/merger/components.parquet`, the baseline
   read by the next run, with the run's components (excluding failed ones), and
   deletes the run's copy. A failed component ends up missing from the baseline, so the
   next run sees it as changed and merges it again. If any failed, the execution fails
   after the commit, so the alarm reports them while the rest of the catalogue moves on.
   A group that fails as a whole, for example because it timed out or Elasticsearch
   was unreachable, writes no `failed_components` file, so all of its components count
   as failed.

A pipeline without a baseline merges every component on its first run. Delete the
baseline to merge everything again. A run that starts while another is still going
is skipped, and the next run picks up whatever changed in the meantime.

## Running locally

```bash
uv run python -m merger.run_local --pipeline-date dev --graph-date dev
```

`merger/run_local.py` runs the same steps in order. It reads the id minter's local Iceberg
table `matcher.works_identified`, merges every changed group into a local Elasticsearch,
and replaces the baseline under the dev prefix in S3. Pass `--match-only` to stop after
finding the changed components.

The groups run one after another, and each one reads the table again. Set
`MERGER_GROUP_SIZE` (default 50,000 works) to merge in fewer, larger groups, as long
as one group's documents fit in memory.

To run against a pipeline instead, pass its dates with `--use-rest-api-table`,
`--es-mode public` and the batch merger's index dates (`--index-date-merged`,
`--index-date-initial`), so the run reads that pipeline's table (named with
`WORKS_IDENTIFIED_TABLE_NAME`), writes to its cluster and replaces its baseline. With
`--use-rest-api-table` alone, the pipeline's works are merged into a local Elasticsearch
and the baseline stays under the dev prefix.

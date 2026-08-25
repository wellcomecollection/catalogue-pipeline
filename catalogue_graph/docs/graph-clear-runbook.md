# Clearing catalogue entities from a Neptune graph

How to clear the catalogue-derived population from a dated Neptune cluster (`catalogue-graph-<graph_date>`) while keeping the ontology, so a pipeline can be rebuilt without reloading the authority data.

How long it takes: measured throughput is roughly 45k nodes a minute (see the measured runs at the end). Count the current population first, which you should do anyway for verification, and scale accordingly. Edge-heavy nodes delete slower, so treat the estimate as a floor.

## What gets deleted and what stays

Each dated cluster holds two populations:

- Catalogue-derived nodes, written by the graph pipeline from works: `Work`, `Concept`, `Image`, `PathIdentifier`. These are what the clear removes.
- The ontology, loaded by the monthly authority job: `SourceConcept`, `SourceLocation`, `SourceName` and the authority edges (`SAME_AS`, `NARROWER_THAN`, `RELATED_TO`, `HAS_FIELD_OF_WORK`, `HAS_FOUNDER`, and the ontology share of `HAS_PARENT`). These stay.

The split works because every `HAS_SOURCE_CONCEPT` edge originates on the `Concept` side, so `DETACH DELETE` on the catalogue labels severs the boundary while leaving every ontology node standing.

## Access

Runs locally, no tunnel needed: the clusters accept direct connections with IAM database auth. `platform-developer` carries `neptune-db:DeleteDataViaQuery` on the dated clusters. If in doubt, verify with `iam simulate-principal-policy` against the cluster's `neptune-db` resource id (not the RDS-style ARN).

```python
# AWS_PROFILE=platform-developer, from catalogue_graph/ with PYTHONPATH=src
from clients.neptune_client import NeptuneClient

graph_date = "2026-07-03"
assert graph_date not in ("", "prod")
client = NeptuneClient(graph_date)
```

The guard matters: an empty or `prod` graph date selects the legacy production cluster (see `NeptuneClient.namespace`), and client-setup patterns copied from notebooks arrive with the date unset.

## Count before deleting

Count the edges each doomed label touches, so the post-clear check is an exact prediction instead of an impression:

```
MATCH (n:Work)-[e]->() RETURN type(e), COUNT(*)
```

and the inbound equivalent, for each of the four labels. The trap this catches: `HAS_PARENT` carries both the `PathIdentifier` archive hierarchy and ontology parentage, so its count legitimately drops during a catalogue clear, by exactly the number of PathIdentifier-to-PathIdentifier edges counted beforehand. Without the pre-count that drop reads as ontology damage.

## The clear

Use `delete_all_nodes_with_label` (batched `DETACH DELETE`, 10k per batch):

```python
for label in ["Work", "Concept", "Image", "PathIdentifier"]:
    client.delete_all_nodes_with_label(label)
```

`_reset_database` wipes the ontology too and is disabled behind `ALLOW_DATABASE_RESET = False` in `neptune_client.py`, so using it is a deliberate code change rather than an option of this procedure.

## Alternative: reset and reload

A full reset followed by re-running the bulk loaders over the existing monthly files (the `graph-bulk-loaders-monthly-<graph_date>` state machine, loading from the `graph_bulk_loader` S3 prefix) rebuilds the ontology in around 30 minutes, without the incident-edge analysis this procedure needs. At large catalogue populations it can be faster than label-scoped deletion. The trade-off is that it requires flipping the safety constant above. Authority edges need no extra step under either method: edges from catalogue nodes to SourceConcepts are re-emitted by the incremental pipeline during the post-clear reindex from the persisted monthly outputs, and authority-to-authority edges reload from those same files. Prefer the label-scoped clear when the catalogue population is small; prefer reset-and-reload when it dwarfs the deletion budget.

## Verification

A full-graph census (`MATCH (n) RETURN labels(n), COUNT(*)`) fails with `TimeLimitExceededException` on a populated cluster, as does the all-edges equivalent. Verify with label-scoped and type-scoped counts, which return in seconds:

- The four catalogue labels count 0, as do `HAS_CONCEPT`, `HAS_SOURCE_CONCEPT`, `HAS_PATH_IDENTIFIER` and `HAS_IMAGE`.
- The ontology counts match the pre-clear counts minus the predicted drops.

## Measured runs

- 2026-07-30, `catalogue-graph-2026-07-03`, round 1 clear (wellcomecollection/platform#6461 phase 4): ~183k catalogue nodes in about 4 minutes. `HAS_PARENT` dropped 1,980,673 to 1,979,571, exactly the 1,102 PathIdentifier-to-PathIdentifier edges predicted. Post-clear ontology counts: `SourceConcept` 547,083, `SourceLocation` 306,090, `SourceName` 13,584,104; `NARROWER_THAN` 630,371, `SAME_AS` 3,549,966, `RELATED_TO` 254,497, `HAS_FIELD_OF_WORK` 497,754, `HAS_FOUNDER` 15,168, `HAS_PARENT` 1,979,571.
- Round 2 clear ran the same procedure (wellcomecollection/platform#6503 phase 4).
- 2026-08-25 census of the same cluster for planning the round 3 clear: 2,169,565 catalogue nodes (Work 1,171,074, Concept 617,395, Image 123,457, PathIdentifier 257,639), about 45 to 50 minutes at measured throughput.

# Elasticsearch versioning for the works ingestor

Ingestor runs overlap by design: a reindex runs alongside the incremental pipeline on its
15-minute schedule. Both write the same documents, so writes go out with
`version_type=external_gte` and the external version decides which one survives. The
stale write arrives with a lower version and bounces as a version conflict, which the
indexer already counts as benign.

These tests pin down two things a unit test cannot: what Elasticsearch actually accepts
as a version, and that the shipped encoding orders writes correctly whatever order they
arrive in.

## Running them

They need Docker, so they are marked `elasticsearch` and deselected by default.

```sh
uv run pytest -m elasticsearch
```

The fixture starts Elasticsearch through `elasticsearch.docker-compose.yml`, which is
pinned to the version the deployed `pipeline_storage` clusters run. It leaves the
container up so a re-run does not pay the startup cost again:

```sh
docker compose -f elasticsearch.docker-compose.yml down -v
```

The index is created from `index_config/mappings.works_indexed.2026-08-20.json` and its
analysis file, which is what `works-indexed-2026-08-20` (the index the API serves) is
built from. Documents come from `tests/fixtures/ingestor/works/mock_es_inputs.json`, so
they satisfy the `dynamic: strict` root mapping. Both of those move on when the served
index does; `conftest.py` says where to look.

## What they establish

The shipped encoding is imported from `ingestor.models.indexable.version` rather than
restated, so these tests cannot drift from what the ingestor writes. `version_encodings.py`
holds the rest: the two encodings we have shipped, the two readings of the original
suggestion, and the bit-packed alternative that was considered and not shipped.

**A version is a non-negative int64, which drives the whole encoding.** A decimal is
rejected outright, with no rounding to fall back on, and writing both epochs end to end
gives 23 digits against the 19 of `Long.MAX`. A negative version is refused, which rules
out any encoding that could go negative for a pre-1970 source time.

**Ordering is lexicographic on (sourceModifiedTime, mergedTime).** Seven scenarios cover
the cases where source order and merge order disagree, and the real test is stronger: all
24 arrival orders of four states converge on the same document. Each of the two encodings
we have shipped leaves the wrong work in place for 12 of those 24, which is the case for
the change stated as a test rather than an argument.

**The live index can take the new scheme in place, one way.** A packed version beats the
epoch-millis version already stored, so no reindex is needed. Reverting is refused,
because every version would drop, so a rollback means rebuilding the index.

**Records with a sourceModifiedTime at the Unix epoch need the source field floored.**
Packed unfloored they land below the epoch-millis version those same documents already
hold, and every later write to them is refused. Both halves are tested: the floored
version is accepted and the unfloored one bounces.

**The version survives the write path the ingestor uses.** A 19-digit version goes
through `elasticsearch.helpers.bulk` and comes back exactly, which is worth pinning down
because it is past the 2^53 a double holds. A stale bulk write bounces as
`version_conflict_engine_exception`, the type the indexer treats as benign.

## Known limits

Second precision on the source field is what makes room for the merge field, so two
source modifications inside one wall-clock second tie and the merge time decides between
them. `source-changes-within-one-second-earlier-merge-loses` is that case: an older
source can still win inside that window.

Field headroom, which `version_encodings.headroom()` prints as dates: the merge field
saturates in 2051 and the source field overflows in 2262. The merge base is a frozen
constant, since moving it later would shift every version down and freeze every document.

See wellcomecollection/platform#6686 and PR #3649 for the history.

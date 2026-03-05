# Test cases for `IDMinter.lookup_ids()` and `IDMinter.mint_ids()`

Derived from [RFC 083 — Stable identifiers following mass record migration](../../docs/RFC-stable-ids.md), specifically the **Batch minting flow** section.

## `lookup_ids`

| # | Test | Description |
|---|------|-------------|
| 1 | Returns existing mappings | All source IDs found in DB are returned |
| 2 | Returns partial matches | Some source IDs found, some missing — missing IDs excluded from result |
| 3 | Returns empty dict for no matches | None of the requested source IDs exist |
| 4 | Returns empty dict for empty input | No source IDs provided |
| 5 | Handles mixed ontology types | e.g. `Work` and `Image` source IDs in the same batch |

## `mint_ids` — Happy paths

| # | Test | Description |
|---|------|-------------|
| 6 | Empty input returns empty | No source IDs provided — returns empty dict |
| 7 | All source IDs already exist | Returns existing canonical IDs, no free IDs claimed |
| 8 | All source IDs are new, no predecessors | Claims free IDs from pool, inserts mappings, marks as assigned |
| 9 | Mixed existing and new | Reuses existing canonical IDs; claims from pool only for new ones |

## `mint_ids` — Predecessor inheritance

| # | Test | Description |
|---|------|-------------|
| 10 | Predecessor found, new source ID not found | Inherits predecessor's canonical ID |
| 11 | Multiple predecessors in same batch | Each new source ID inherits from its own predecessor |
| 12 | Cross-type predecessor | e.g. `Image` source ID inherits from a `Work` predecessor |
| 13 | Re-processing without predecessor | Source ID already has canonical ID from previous predecessor-based mint — returns same ID (idempotent) |

## `mint_ids` — Idempotency

| # | Test | Description |
|---|------|-------------|
| 14 | Identical request processed twice | Same result both times, no extra IDs claimed |
| 15 | Duplicate source IDs in same batch | Deduplicated — single canonical ID per unique source ID |

## `mint_ids` — Error cases

| # | Test | Description |
|---|------|-------------|
| 16 | Predecessor specified but not found in DB | Raises `ValueError`, nothing committed |
| 17 | Free ID pool exhausted | Needs N free IDs but 0 available — raises `RuntimeError`, nothing committed |
| 18 | Free ID pool partially exhausted | Needs N free IDs but fewer than N available — raises `RuntimeError`, nothing committed |

## `mint_ids` — Race conditions

| # | Test | Description |
|---|------|-------------|
| 19 | Another process inserts same source ID between lookup and insert | Detects race via `FOR SHARE` re-read, adopts winner's canonical ID |
| 20 | Race loser returns unused claimed ID to pool | Unused ID remains `free` — pool is not depleted by lost races |
| 21 | Race with multiple source IDs — partial wins | Some IDs won, some lost — only won IDs marked `assigned` |

## `mint_ids` — Transaction atomicity

| # | Test | Description |
|---|------|-------------|
| 22 | Failure mid-batch rolls back everything | e.g. predecessor error after some inserts — no partial commits |
| 23 | Pool exhaustion rolls back successor mapping | Successor inherits predecessor's canonical ID, but pool exhaustion for other items rolls back the entire transaction |

## `mint_ids` — Pool management

| # | Test | Description |
|---|------|-------------|
| 24 | Claimed IDs are marked as `assigned` | Status updated in `canonical_ids` table after successful mint |
| 25 | Only used IDs are marked `assigned` | If batch claims 5 but race means only 3 used, the 2 unused stay `free` |

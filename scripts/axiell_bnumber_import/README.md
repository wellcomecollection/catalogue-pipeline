# Axiell Collections b number import

Generates the CSV for importing Sierra b numbers into Axiell Collections
archive records, so digitised archive works keep their METS content when the
CALM-synced Sierra bibs retire without FOLIO successors.

Background: RFC 092 ([wellcomecollection/docs#164](https://github.com/wellcomecollection/docs/pull/164)),
tracked in [wellcomecollection/platform#6525](https://github.com/wellcomecollection/platform/issues/6525).

## The two steps

1. `extract_pairs.py` reads the production pipeline's `works-source` index and
   emits `pairs.csv`: one row per Sierra bib carrying a `calm-record-id` merge
   candidate (the bib's 035, the same link the merger uses). The candidates
   are not indexed, so the script scrolls all Sierra source works and filters
   client side.

   ```
   AWS_PROFILE=platform-developer uv run --project catalogue_graph \
       python scripts/axiell_bnumber_import/extract_pairs.py
   ```

2. `build_import_csv.py` joins `pairs.csv` against the live Axiell adapter
   store, keyed on the CALM RecordID each AxC record carries in MARC 907, and
   writes `axiell_bnumber_import.csv` plus `conflicts.csv`, `unmatched.csv`
   and `report.md`.

   ```
   AWS_PROFILE=platform-read_only uv run --project catalogue_graph \
       python scripts/axiell_bnumber_import/build_import_csv.py
   ```

`extract_pairs.py` needs `platform-developer` because the pipeline ES
credentials live in Secrets Manager; the store scan works with
`platform-read_only`.

## The import CSV

Columns are `RecordID,Bnumber,RefNo`: the CALM RecordID to match the AxC
record on, the b number to write, and the AxC record's RefNo as a human
cross-check. These headers are a working guess pending agreement with
collections staff on the Collections Import tool's expectations; only the
headers and column order should need changing.

RecordIDs with several bibs produce one row each (035 is repeatable).
Conflicts, where AxC already cites a different b number, are excluded from
the import CSV and reported for review. Pairs with no AxC record are expected
for manuscripts moving to TEI and for returned PSY material.

## Re-running

Both steps are read only and deterministic (sorted output), so re-run them
any time; digitisation continues, so the mapping needs re-cutting at least
once near cutover. `--since <previous import csv>` emits only rows not
already in an earlier CSV, for incremental imports.

After an import lands and a harvest completes, re-running step 2 should show
the imported rows move from "to import" to "already present", which is the
verification step on platform#6525.

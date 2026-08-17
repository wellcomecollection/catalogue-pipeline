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

Columns follow the format agreed with collections staff on 2026-08-17:

```
object_number,alternative_number,alternative_number.type
WT/D/1/20/1/35/95,b33174192,Bibliographic Number
```

`object_number` is the public reference (the AltRefNo) the import matches
AxC records on, `alternative_number` is the b number to write, and
`alternative_number.type` is the constant `Bibliographic Number`. Because the
import matches on the public reference rather than the CALM RecordID, the
build step uses the 907 join to derive each record's AltRefNo and withholds
rows where several AxC records share one (`ambiguous_refs.csv`) or where the
matched record has no AltRefNo at all (`no_public_ref.csv`).

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

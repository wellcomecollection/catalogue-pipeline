# Axiell Collections b number import

Generates the CSV for importing Sierra b numbers into Axiell Collections
archive records, so digitised archive works keep their METS content when the
CALM-synced Sierra bibs retire without FOLIO successors.

Background: RFC 092 ([wellcomecollection/docs#164](https://github.com/wellcomecollection/docs/pull/164)),
tracked in [wellcomecollection/platform#6525](https://github.com/wellcomecollection/platform/issues/6525).

## The steps

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

3. Optionally, `check_bnumbers.py` resolves every `035 (Bibliographic
   Number)` already in the store against the Sierra source works and writes
   `bnumber_status.csv` (live, deleted, or absent, with the Sierra record's
   format and title). Passing that file to step 2 as `--bnumber-status`
   imports conflict rows whose existing value is dead in Sierra, since
   nothing usable is lost whether the import appends or replaces, and leaves
   only live-valued conflicts withheld for review.

   ```
   AWS_PROFILE=platform-developer uv run --project catalogue_graph \
       python scripts/axiell_bnumber_import/check_bnumbers.py
   ```

`extract_pairs.py` and `check_bnumbers.py` need `platform-developer` because
the pipeline ES credentials live in Secrets Manager; the store scan alone
works with `platform-read_only`.

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

Only records at Item level are imported. Axiell refuses to save a record
above Item with "A location may not be set for this type of record", even
though the import sets no location: the 2026-09-10 run failed on 516 rows
this way, every one of them above Item. Withheld rows go to
`wrong_level.csv`, and `report.md` counts them by level; `--all-levels`
imports them anyway, which is only useful for inspecting the full set.

The level is the first non-empty MARC 351 `$c`, matched case-insensitively.
351 is repeatable and its casing varies across the migrated data, so this
follows what the transformer does in
`adapters/transformers/axiell/organisation_and_arrangement.py`. The match on
Item is exact, so "Item part", CALM's old "piece" level, is withheld too.
Whether Axiell saves a record at that level is unresolved: the 2026-09-10 run
attempted every level, so if it carried Item part rows and none of them are
among the 516 failures, they belong in the import. The per-level counts in
`report.md` say how many rows that is worth.

The adapter store holds each record as serialised MARC XML, so subfield
values are XML-unescaped before use. Without that, the 53 records whose
AltRefNo contains an ampersand go into the CSV as `MSS.1055-1061 &amp; 7126`
and Axiell reports them as having no match.

RecordIDs with several bibs produce one row each (035 is repeatable).
Conflicts, where AxC already cites a different live b number, are reported
with each existing value's Sierra status, format and title. By default they
are excluded from the import CSV; with `--include-live-conflicts` our value
is imported anyway, per the collections decision of 2026-08-19 that the
harvest b number wins and the duplicate cataloguing is resolved in Axiell. Pairs with no AxC record are expected
for manuscripts moving to TEI and for returned PSY material.

## Re-running

Both steps are read only and deterministic (sorted output), so re-run them
any time; digitisation continues, so the mapping needs re-cutting at least
once near cutover. `--since <previous import csv>` emits only rows not
already in an earlier CSV, for incremental imports.

After an import lands and a harvest completes, re-running step 2 should show
the imported rows move from "to import" to "already present", which is the
verification step on platform#6525.

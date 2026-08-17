# Axiell Collections publish_to_web import

Generates the CSV for populating the `publish_to_web` (wp) checkbox on Axiell
Collections archive records. The round 2 migration load left the checkbox
empty on every record in `collect`, which the OAI stylesheet renders as
981 $a = `no` and the Axiell transformer treats as suppression, so a reindex
of the load as delivered would suppress the entire Axiell catalogue.

Tracked under [wellcomecollection/platform#6503](https://github.com/wellcomecollection/platform/issues/6503).

## The tick rule

Per the agreed mapping (Calm2Collections workbook, sheet "Archive catalogue",
CatalogueStatus row), the checkbox is ticked when the record's CALM
CatalogueStatus is `Catalogued`, `Partially catalogued` or `Not yet
available`, and left unticked otherwise.

The rule must be derived from the CALM side: `Closed description` records
(catalogued but not allowed online, suppressed in production today) carry the
same `record_progress = CHECKED` as ordinary catalogued records in AxC, so
the AxC data alone cannot distinguish them. Measured 2026-08-17: 482 closed
description and 87 for-deletion records sit in the round 2 load as plain
"catalogued"; ticking by AxC status would newly publish all 569.

## The two steps

1. `extract_calm_statuses.py` scans the CALM VHS (`vhs-calm-adapter` DynamoDB
   table plus its S3 payloads, ~270k live records, about 5 minutes at the
   default 48 workers) and writes `calm_statuses.csv`.

   ```
   AWS_PROFILE=platform-read_only uv run --project catalogue_graph \
       python scripts/axiell_publish_to_web_import/extract_calm_statuses.py
   ```

2. `build_publish_csv.py` joins the statuses against the Axiell adapter store
   (live REST table by default; pass `--snapshot-path` to reuse a rebuild
   snapshot parquet), keyed on the CALM RecordID in MARC 907, and writes
   `axiell_publish_to_web_import.csv`, `sample.csv` (10 rows for a trial
   import), `withheld.csv`, `excluded_suppressed.csv` and `report.md`.

   ```
   AWS_PROFILE=platform-read_only uv run --project catalogue_graph \
       python scripts/axiell_publish_to_web_import/build_publish_csv.py
   ```

## The import CSV

```
object_number,publish_to_web
PP/AMI/A/23,x
```

`object_number` is the public reference (the AltRefNo) the Collections import
tool matches records on, following the format agreed for the b number import
(`scripts/axiell_bnumber_import/`). The `publish_to_web` header and the `x`
tick value are the expected checkbox representation but are not yet confirmed
with collections staff; `--tick-value` adjusts the value if needed. Records
with no CALM link (born-AxC), no AltRefNo or a shared AltRefNo are withheld
and reported rather than imported.

Regeneration is safe and deterministic: both steps re-read their sources in
full, and the import is idempotent (it sets the same value on the same
records). After a corrected migration load from Axiell this tooling is
redundant; it exists to unblock round 2 testing if that fix is slow.

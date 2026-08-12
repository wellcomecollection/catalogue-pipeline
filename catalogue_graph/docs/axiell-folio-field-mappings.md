# Axiell to FOLIO field mappings

This describes what the Axiell Collections (AxC) to FOLIO sync writes to FOLIO,
and where each value comes from. For every AxC item-level record, the sync
creates or updates a linked set of three records in FOLIO: an Instance, a
Holdings record, and an Item. It does this through the FOLIO Inventory API.

## Where this lives in the code

| Concern | File |
| --- | --- |
| Mapping table and constants | `src/adapters/steps/axiell_folio_sync/mapping/config.py` |
| Builders (the logic) | `src/adapters/steps/axiell_folio_sync/mapping/builders.py` |
| MARC extraction primitive | `src/adapters/steps/axiell_folio_sync/mapping/marc.py` |
| Payload contracts | `src/adapters/steps/axiell_folio_sync/mapping/payloads.py` |

Mapping version: **2.6.0** (`config.VERSION`). This is stamped into every
payload's `meta` block so you can tell which rules produced a given record.

## What gets synced

A record is synced only if it is item-level, meaning MARC `351 $c` equals `ITEM`
(case-insensitive). Anything else is skipped completely: it is never created,
updated, or suppressed.

There is also a harvest-flag gate in the config (`980 $a`), but it is currently
turned off so the sync runs for all item-level records. Selection is item-level
only for now.

## How MARC fields are read

Each field's inbound source is written as a short spec string in `config.FIELDS`,
which `marc.extract()` reads. There are three forms:

| Spec | Meaning |
| --- | --- |
| `TAG` | A control field, such as `001`. |
| `TAG$sub` | The first non-empty subfield, such as `245$a`. |
| `TAG$sub(Prefix)` | The subfield whose value uses the given `(Prefix)value` namespace, returned with the prefix removed. |

The last form matters for `035 $a`. The XSLT prefixes every `035 $a` it emits
with its identifier scheme, for example `(AltRefNo)value`, `(accession number)value`,
or `(Bibliographic Number)value` for a Sierra bib number. Adlib serialises these
in no particular order, so a plain "first non-empty" read could pick up the wrong
one. The spec `035$a(AltRefNo)` picks out the object_number specifically and
strips the `(AltRefNo)` prefix, using the same prefix parsing as
`transformers.marc.other_identifiers.format_field`. A record with no `(AltRefNo)`
035 gets no Local identifier at all, rather than being given the wrong one.

## Shared HRIDs

Each of the three records gets a predictable HRID built from the AxC GUID (MARC
`001`). This is what links the three records together and lets deletion facts
find the right FOLIO records later.

| Record | HRID pattern | Example |
| --- | --- | --- |
| Instance | `AxC-instance-<001>` | `AxC-instance-guid-001` |
| Holdings | `AxC-holding-<001>` | `AxC-holding-guid-001` |
| Item | `AxC-item-<001>` | `AxC-item-guid-001` |

## Instance

Built by `build_instance` in `builders.py`, against the `payloads.Instance`
contract. The `source` is `FOLIO` because the instance is created natively in
FOLIO, with no linked SRS MARC record.

| FOLIO field | Value | Source | Notes |
| --- | --- | --- | --- |
| `hrid` | `AxC-instance-<001>` | MARC `001` (GUID) | Required. |
| `title` | Title text | MARC `245 $a` | Required. A mapping error is raised if it is missing. |
| `source` | `FOLIO` | Constant | |
| `instanceTypeId` | FOLIO instance-type UUID | Constant, via `RefCache.instance_type_id()` | |
| `identifiers[].identifierTypeId` | `Local identifier` UUID | Constant, via `resolve_identifier_type` | The whole `identifiers` list is left out when there is no object_number. |
| `identifiers[].value` | The object_number, with its prefix removed | MARC `035 $a(AltRefNo)` | For example `(AltRefNo)SA/BSI` becomes `SA/BSI`. |

## Holdings

Built by `build_holdings` in `builders.py`, against the `payloads.Holdings`
contract.

| FOLIO field | Value | Source | Notes |
| --- | --- | --- | --- |
| `hrid` | `AxC-holding-<001>` | MARC `001` | Required. |
| `instanceId` | Parent instance UUID | Injected by the upsert orchestrator | Not set when the payload is built. |
| `sourceId` | Holdings-source UUID | Constant, via `resolve_holdings_source` (default `MARC`) | |
| `permanentLocationId` | FOLIO location UUID | MARC `852 $b`, via `resolve_location` (default `History of Medicine`) | Location prefix overrides apply, see below. |

## Item

Built by `build_item` in `builders.py`, against the `payloads.Item` contract.

| FOLIO field | Value | Source | Notes |
| --- | --- | --- | --- |
| `hrid` | `AxC-item-<001>` | MARC `001` | Required. |
| `holdingsRecordId` | Parent holdings UUID | Injected by the upsert orchestrator | Not set when the payload is built. |
| `status.name` | `Available` | Constant | |
| `materialType.id` | Material-type UUID | MARC `655 $a`, via `resolve_material_type` (default `book`) | Uses the normalization table below. |
| `permanentLoanType.id` | Loan-type UUID | MARC `949 $l`, via `resolve_loan_type` (default `Can circulate`) | |
| `permanentLocation.id` | FOLIO location UUID | MARC `852 $b`, via `resolve_location` (default `History of Medicine`) | Same source as the holdings location. |
| `barcode` | Barcode string | MARC `949 $a` | Passed through as-is, optional. |
| `notes[]` | `{note, noteType: "Axiell location", staffOnly: false}` | MARC `852 $b`, or `unknown` when absent | Keeps the raw AxC current location as a note. |

## How values are resolved to FOLIO UUIDs

For any field that needs a FOLIO tenant UUID, `_resolve` in `builders.py` runs
the raw AxC value through these steps in order:

1. Start with the raw AxC value.
2. Apply the location prefix overrides (location fields only).
3. Apply the normalization table (if the field has one).
4. Fall back to the default if the value is now empty.
5. Look the resulting name up through the matching `RefCache` resolver to get a UUID.

If the resolved name is unknown to the FOLIO tenant, the sync raises a
`MappingError` instead of sending a payload that FOLIO would reject with a 422.

### Material type

AxC `object_category` (`655 $a`) maps to a FOLIO material-type name. Matching is
case-insensitive.

| AxC object_category (`655 $a`) | FOLIO material type |
| --- | --- |
| Archives - Non Digital / Non-digital | `archive` |
| Moving Image - Non Digital / Non-digital | `film` |
| Sound - Non Digital / Non-digital | `audio format requestable` |
| Visual Material - Non Digital / Non-digital | `non-projected graphic` |
| Anything else, or absent | `book` (default) |

### Location prefix overrides

If the AxC current location (`852 $b`) starts with certain digits, it maps to a
fixed FOLIO location before the normal lookup runs. The first match wins.

| `852 $b` starts with | FOLIO location |
| --- | --- |
| `215` or `183` | `hicon` |

### Defaults

Used when the record has no value for a resolved field.

| Field | Default |
| --- | --- |
| Material type | `book` |
| Loan type | `Can circulate` |
| Location | `History of Medicine` |
| Holdings source | `MARC` |
| Identifier type | `Local identifier` |
| Item note type | `Axiell location` |

## Full inbound MARC field map

Taken from `config.FIELDS`.

| CanonicalRecord field | MARC spec | Feeds |
| --- | --- | --- |
| `source_id` | `001` | All HRIDs, and `meta.source_id` |
| `title` | `245$a` | `instance.title` |
| `object_number` | `035$a(AltRefNo)` | `instance.identifiers[].value` (Local identifier) |
| `object_category` | `655$a` | `item.materialType` |
| `current_location` | `852$b` | Holdings and item location, plus the Axiell location note |
| `barcode` | `949$a` | `item.barcode` |
| `loan_type_code` | `949$l` | `item.permanentLoanType` |
| Record selection | `351$c` | Must be `ITEM` for the record to sync |
| Harvest flag | `980$a` | Opt-in gate, currently disabled |

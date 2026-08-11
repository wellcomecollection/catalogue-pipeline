# Axiell → FOLIO field mappings

What the Axiell Collections (AxC) → FOLIO sync writes to FOLIO, and where each
value comes from. The sync creates/updates a linked **Instance → Holdings → Item**
triple per AxC item-level record via the FOLIO Inventory API.

**Sources of truth in code**

- Mapping table & constants: `src/adapters/steps/axiell_folio_sync/mapping/config.py`
- Builders (the logic): `src/adapters/steps/axiell_folio_sync/mapping/builders.py`
- MARC extraction primitive: `src/adapters/steps/axiell_folio_sync/mapping/marc.py`
- Payload contracts: `src/adapters/steps/axiell_folio_sync/mapping/payloads.py`

Mapping version: **2.6.0** (`config.VERSION`, stamped into every payload's `meta`).

---

## Record selection (what gets synced at all)

A record is synced only if it is **item-level**: MARC `351 $c` == `ITEM`
(case-insensitive). Everything else is skipped — never created, updated, or
suppressed.

> The `980 $a` harvest-flag gate exists in config (`HARVEST_FLAG_SPEC = "980$a"`)
> but is currently disabled ("run for all"); selection is item-level only.

---

## Extraction spec syntax

The inbound MARC source for each field is declared as a spec string in
`config.FIELDS`, read by `marc.extract()`:

| Spec form            | Meaning                                                                 |
| -------------------- | ----------------------------------------------------------------------- |
| `TAG`                | control field (e.g. `001`)                                              |
| `TAG$sub`            | first non-empty subfield (e.g. `245$a`)                                 |
| `TAG$sub(Prefix)`    | the subfield whose value carries the MARC-035 `(Prefix)value` namespace, returned **with the prefix stripped** |

The `(Prefix)` form matters for `035$a`: the XSLT prefixes **every** `035 $a` it
emits with its identifier scheme — `(AltRefNo)value`, `(accession number)value`,
`(Bibliographic Number)value` (Sierra bib), etc. — and Adlib serialises them in
arbitrary order. `035$a(AltRefNo)` selects the object_number specifically and
strips the `(AltRefNo)` prefix (same prefix parsing as
`transformers.marc.other_identifiers.format_field`). Records with no `(AltRefNo)`
035 get **no** Local identifier rather than the wrong one.

---

## Common HRIDs

Each payload gets a deterministic HRID derived from the AxC GUID (MARC `001`),
which links the triple and lets deletion facts target the right FOLIO records:

| Payload  | HRID pattern                | Example                 |
| -------- | --------------------------- | ----------------------- |
| Instance | `AxC-instance-<001>`        | `AxC-instance-guid-001` |
| Holdings | `AxC-holding-<001>`         | `AxC-holding-guid-001`  |
| Item     | `AxC-item-<001>`            | `AxC-item-guid-001`     |

---

## Instance

`build_instance` (`builders.py`). Contract: `payloads.Instance`.
`source="FOLIO"` because the instance is created FOLIO-native (no linked SRS MARC
record).

| FOLIO field                       | Value                                            | AxC / MARC source                          | Notes |
| --------------------------------- | ------------------------------------------------ | ------------------------------------------ | ----- |
| `hrid`                            | `AxC-instance-<001>`                              | MARC `001` (GUID)                          | Required. |
| `title`                           | title text                                       | MARC `245 $a`                              | Required — mapping error if absent. |
| `source`                          | `"FOLIO"`                                         | constant                                   | |
| `instanceTypeId`                  | FOLIO instance-type UUID                          | constant → `RefCache.instance_type_id()`   | |
| `identifiers[].identifierTypeId`  | `"Local identifier"` UUID                         | constant → `resolve_identifier_type`       | Omitted entirely when there is no object_number. |
| `identifiers[].value`             | object_number (bare, prefix stripped)             | MARC `035 $a(AltRefNo)`                    | e.g. `(AltRefNo)SA/BSI` → `SA/BSI`. |

---

## Holdings

`build_holdings` (`builders.py`). Contract: `payloads.Holdings`.

| FOLIO field            | Value                              | AxC / MARC source                                | Notes |
| ---------------------- | ---------------------------------- | ------------------------------------------------ | ----- |
| `hrid`                 | `AxC-holding-<001>`                | MARC `001`                                       | Required. |
| `instanceId`           | parent instance UUID              | injected by the upsert orchestrator              | Not set at build time. |
| `sourceId`             | holdings-source UUID              | constant → `resolve_holdings_source` (default `MARC`) | |
| `permanentLocationId`  | FOLIO location UUID               | MARC `852 $b` → `resolve_location` (default `History of Medicine`) | Prefix overrides apply (see below). |

---

## Item

`build_item` (`builders.py`). Contract: `payloads.Item`.

| FOLIO field           | Value                              | AxC / MARC source                                    | Notes |
| --------------------- | ---------------------------------- | ---------------------------------------------------- | ----- |
| `hrid`                | `AxC-item-<001>`                   | MARC `001`                                           | Required. |
| `holdingsRecordId`    | parent holdings UUID              | injected by the upsert orchestrator                  | Not set at build time. |
| `status.name`         | `"Available"`                     | constant                                             | |
| `materialType.id`     | material-type UUID                | MARC `655 $a` → `resolve_material_type` (default `book`) | Via normalization table (below). |
| `permanentLoanType.id`| loan-type UUID                    | MARC `949 $l` → `resolve_loan_type` (default `Can circulate`) | |
| `permanentLocation.id`| FOLIO location UUID               | MARC `852 $b` → `resolve_location` (default `History of Medicine`) | Same source as holdings location. |
| `barcode`             | barcode string                    | MARC `949 $a`                                        | Passthrough, optional. |
| `notes[]`             | `{note, noteType:"Axiell location", staffOnly:false}` | MARC `852 $b` (or `"unknown"` when absent) | Preserves the raw AxC current_location. |

---

## Resolution chain

For fields resolved to a FOLIO tenant UUID, `_resolve` (`builders.py`) applies:

```
raw AxC value → (location prefix overrides) → (normalization table) → default (if empty) → RefCache resolver → UUID
```

A name unknown to the FOLIO tenant raises a `MappingError` rather than sending a
payload that FOLIO would 422.

### Normalization tables & defaults (`config.py`)

**Material type** — AxC `object_category` (`655 $a`) → FOLIO material-type name
(case-insensitive):

| AxC object_category (`655 $a`)   | FOLIO material type          |
| -------------------------------- | ---------------------------- |
| Archives - Non Digital / Non-digital | `archive`                |
| Moving Image - Non Digital / Non-digital | `film`               |
| Sound - Non Digital / Non-digital | `audio format requestable`  |
| Visual Material - Non Digital / Non-digital | `non-projected graphic` |
| *(anything else / absent)*        | `book` (default)            |

**Location prefix overrides** — AxC `current_location` (`852 $b`) whose leading
digits match are mapped to a fixed FOLIO location before the normal lookup
(first match wins):

| `852 $b` starts with | FOLIO location |
| -------------------- | -------------- |
| `215` or `183`       | `hicon`        |

**Defaults** used when the record carries no value for a resolved field:

| Field          | Default              |
| -------------- | -------------------- |
| Material type  | `book`               |
| Loan type      | `Can circulate`      |
| Location       | `History of Medicine`|
| Holdings source| `MARC`               |
| Identifier type| `Local identifier`   |
| Item note type | `Axiell location`    |

---

## Full inbound MARC field map

Derived from `config.FIELDS`:

| CanonicalRecord field | MARC spec         | Feeds                                             |
| --------------------- | ----------------- | ------------------------------------------------- |
| `source_id`           | `001`             | all HRIDs; `meta.source_id`                       |
| `title`               | `245$a`           | `instance.title`                                  |
| `object_number`       | `035$a(AltRefNo)` | `instance.identifiers[].value` (Local identifier) |
| `object_category`     | `655$a`           | `item.materialType`                               |
| `current_location`    | `852$b`           | `holdings`/`item` location + Axiell location note |
| `barcode`             | `949$a`           | `item.barcode`                                    |
| `loan_type_code`      | `949$l`           | `item.permanentLoanType`                          |
| *(record selection)*  | `351$c`           | must be `ITEM` to sync                             |
| *(harvest flag)*      | `980$a`           | opt-in gate — **currently disabled**              |

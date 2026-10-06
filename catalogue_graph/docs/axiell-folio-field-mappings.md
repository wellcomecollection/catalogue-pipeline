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

Mapping version: **2.6.1** (`config.VERSION`), stamped into every payload's
`meta` block so you can tell which rules produced a given record.

## What gets synced

Two gates, and a record has to pass both:

| Gate | MARC | Rule |
| --- | --- | --- |
| Harvest flag | `980 $a` | Present and non-empty. This is the curator-facing opt-in to the FOLIO sync. |
| Record level | `351 $c` | Equals `ITEM`, case-insensitive. |

A record failing either is skipped completely: never created, updated, or
suppressed, and not reported as an error. An unflagged record is a deliberate
opt-out rather than a data problem.

Both gates live in `_passes_selection_gates`, which `is_selected_for_sync` and
`select_and_build` share so the two cannot disagree.

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
| `permanentLocationId` | FOLIO location UUID | MARC `984 $b` (AxC **normal** location), via `resolve_location` | The location rules below apply. No default: a missing `984 $b`, or a location the tenant does not know, fails the record with a `MappingError`. |

## Item

Built by `build_item` in `builders.py`, against the `payloads.Item` contract.

| FOLIO field | Value | Source | Notes |
| --- | --- | --- | --- |
| `hrid` | `AxC-item-<001>` | MARC `001` | Required. |
| `holdingsRecordId` | Parent holdings UUID | Injected by the upsert orchestrator | Not set when the payload is built. |
| `status.name` | Item-status name | MARC `506 $f` (access category), via `ACCESS_ITEM_STATUS` (default `Unavailable`) | Table value is final; statuses are a fixed FOLIO enum, not tenant reference data. **Create-only**: mod-circulation owns the field once the item exists, so updates send FOLIO's own status back and an AxC access change does not propagate. See `_CREATE_ONLY_FIELDS` in `upsert/entities.py`. |
| `materialType.id` | Material-type UUID | MARC `655 $a`, via `resolve_material_type` | Uses the normalization table below. No default: an absent or unmapped category fails the record. |
| `permanentLoanType.id` | `Can circulate` UUID | Constant, via `resolve_loan_type` | **No AxC mapping**, pending Collection Information |
| `permanentLocation.id` | FOLIO location UUID | MARC `984 $b` (AxC **normal** location), via `resolve_location` | Same source and rules as the holdings location above, so the two always agree. |
| `administrativeNotes[]` | `"Axiell Current Location: <852 $b>"` | MARC `852 $b`, or `unknown` when absent | Keeps the raw AxC current location as an administrative note. A plain string, so no item note type has to exist in the tenant. The label is in the string because an administrative note carries no type, and is also what the upsert matches on to reclaim the note. Always written, so an update cannot leave a stale location behind. |

## How values are resolved to FOLIO UUIDs

For any field that needs a FOLIO tenant UUID, `_resolve` in `builders.py` runs
the raw AxC value through these steps in order:

1. Start with the raw AxC value.
2. If the field is **required** and that value is empty, raise a `MappingError`
   immediately, because there is no default to fall back on. The normal location
   and the material type are the required fields today.
3. Apply the location rules (location fields only), which resolve the AxC hierarchy
   to a FOLIO location name.
4. Apply the normalization table (if the field has one).
5. Fall back to the field's default if the value is now empty. A required field has
   no default, so this step never applies to one.
6. Look the resulting name up through the matching `RefCache` resolver to get a UUID.

If the resolved name is unknown to the FOLIO tenant, the sync raises a
`MappingError` instead of sending a payload that FOLIO would reject with a 422.
Either way the record is reported as an error rather than written. Nothing is
silently substituted.

### Material type

AxC `object_category` (`655 $a`) maps to a FOLIO material-type name. Matching is
case-insensitive.

| AxC object_category (`655 $a`) | FOLIO material type |
| --- | --- |
| `Archives - Non-digital` | `archive` |
| `Archives - Digital` | `archive` |
| `Archives - Hybrid` | `archive` |
| `Moving Image - Non-digital` | `film` |
| `Moving Image - Digital` | `video format non-requestable` |
| `Sound - Non-digital` | `audio format requestable` |
| `Sound - Digital` | `audio format non-requestable` |
| `Visual Material` | `non-projected graphic` |
| `Pictures` | `non-projected graphic` |
| Anything else | `MappingError`: the raw value resolves to nothing in the tenant |
| *(absent)* | `MappingError`: required, with no default |

The digital rows take the `non-requestable` halves because this tenant encodes
requestability in the material type, and a digital surrogate is not the carrier a
reader requests. Those three are still to be confirmed with Collection
Information. For the AxC value distribution behind this table, see
[axiell-folio-mapping-options.md](axiell-folio-mapping-options.md) section 1.

### Access category to item status

`access_category` (`506 $f`) maps to a FOLIO item status. Case-insensitive.
Values are FOLIO's fixed enum, so nothing is resolved against the tenant.

| AxC access category (`506 $f`) | FOLIO item status |
| --- | --- |
| `OPEN`, `OPENWITHADVISORY` | `Available` |
| `RESTRICTED` | `Available` (see below) |
| `PERMISSIONREQUIRED`, `SAFEGUARDED`, `CLOSED` | `Restricted` |
| `MISSING` | `Missing` |
| `DEACCESSIONED` | `Withdrawn` |
| `DATAISSUES` | `Unknown` |
| *(absent)* | `Unavailable` (`DEFAULT_ITEM_STATUS`) |
| *(present but unrecognised)* | `MappingError` |

**`RESTRICTED` is `Available`, not `Restricted`.** Restricted material is
genuinely available and can be requested online. The restriction is that the
reader signs to agree to the conditions of viewing restricted material, and they
do that before the material is handed over, so it does not affect whether the
item can be requested or produced.

Two caveats: `Restricted` and `Withdrawn` are unconfirmed on this tenant's FOLIO
version, and the status is create-only, so a later access change in AxC does not
reach an existing item.

### Loan type: no mapping

Every item takes the default, `Can circulate`. No AxC field is mapped to the
loan type, so nothing overrides it. Two candidates are unsettled: the access
category (`506 $f`, who may access it) and the use restriction (`540 $a`, how it
may be requested). Both have been mapped here and reverted pending Collection
Information, which also has to say whether open archival material should
circulate at all.

Until then a reader can request any item, including those whose access note
reads *"This item is closed and cannot be accessed"*. Settle before a
production run.

### Location rules

The AxC normal location (`984 $b`) maps to a FOLIO location by the **leading code**
of its hierarchy. AxC nests two ways at once: the path is `/`-separated and each
segment is `;`-separated, so `984 $b` reads `215/215;B11/215;B11;MR/...` and its leaf
(`984 $c`) reads `215;B11;MR;84`. Taking the first component of each split gives
`215` from either spelling, which is the unit these rules match. Comparing whole
codes rather than string prefixes also means `215` cannot swallow `2150` or `215A`.
Code and prefix matching is case-insensitive, as every other lookup in the mapping
is: AxC does not control its own casing, and `resolve_location` folds case too.

First match wins. FOLIO's hierarchy is institution, campus, library, location,
but only the leaf is resolved (`RefCache` indexes by code and name, and a leaf
implies its parents); the parents are listed because they are what was agreed and
what provisioning the tenant requires. The institution is `Wellcome Collection`
throughout.

| Leading code | Campus | Library | FOLIO location (resolved) |
| --- | --- | --- | --- |
| `215` or `183` | Euston Road (Axiell) | Axiell sync | `AxC Euston Road` |
| `Deepstore` | Deepstore | Offsite (DS) | `AxC Deepstore` |
| starts `CLW` | Constantine London West | Axiell sync | `AxC Constantine London West` |

A location matching no rule falls through to the ordinary FOLIO code/name lookup
as its **leading code**, not as the raw `984 $b` value:. There is no default.

### Defaults

Used when the record has no value for a resolved field.

| Field | Default |
| --- | --- |
| Material type | **None, the record fails instead** |
| Loan type | `Can circulate`, the only value any item gets |
| Item status | `Unavailable` |
| Holdings source | `MARC` |
| Identifier type | `Local identifier` |
| Administrative note label | `Axiell Current Location` |
| **Location** | **None, the record fails instead** |

The location is deliberately the exception. It used to default to
`History of Medicine`, which meant an unmapped or unknown location produced a real,
plausible-looking, wrong shelf. It is now `required`, so such a record is reported
as an error for someone to act on.

## Full inbound MARC field map

Taken from `config.FIELDS`.

| CanonicalRecord field | MARC spec | Feeds |
| --- | --- | --- |
| `source_id` | `001` | HRIDs, `meta.source_id` |
| `title` | `245$a` | instance title |
| `object_number` | `035$a(AltRefNo)` | local identifier |
| `object_category` | `655$a` | material type |
| `current_location` | `852$b` | admin note |
| `normal_location` | `984$b` | holdings/item permanent location |
| `access_category` | `506$f` | item status |
| record selection | `351$c` | must be `ITEM` |
| harvest flag | `980$a` | must be present and non-empty |

## Code locations

- mapping config: `src/adapters/steps/axiell_folio_sync/mapping/config.py`
- builders: `src/adapters/steps/axiell_folio_sync/mapping/builders.py`
- MARC extraction: `src/adapters/steps/axiell_folio_sync/mapping/marc.py`
- payload contracts: `src/adapters/steps/axiell_folio_sync/mapping/payloads.py`

Mapping version: `2.6.1` (`config.VERSION`).

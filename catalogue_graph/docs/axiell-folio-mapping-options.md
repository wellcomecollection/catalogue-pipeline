# Axiell to FOLIO mapping: what is missing

A decision document for Collection Information. It covers the fields where the
mapping is incomplete, undecided or absent, in that order. For what the sync
maps today, see
[axiell-folio-field-mappings.md](axiell-folio-field-mappings.md).

## State

| Mapping | State | Where |
| --- | --- | --- |
| **Permanent location**, from `984$b` (normal location) | **Mapped, incomplete.** Three rules cover 89.8% of records; 13,284 (7.2%) match no rule and fail. The only thing failing records today. | [Section 1](#1-permanent-location-incomplete-and-failing-13284-records), [questions 1 to 3](#location) |
| **Loan type** | **Implemented** (mapping version 2.8.0). Derived from `540$a` (use restriction): `Can circulate` for `Online request` and `Restricted` only, `Unavailable` for the rest, including an absent or unlisted term. No access-category ceiling. | [Section 2](#2-loan-type-implemented), [questions 4 and 5](#loan-type) |
| **Material type**, from `655$a` (object category) | **Implemented and exhaustive** over the corpus, with no default. The three digital rows were decided on our own reasoning and need confirming. | [Section 3](#3-material-type-three-rows-need-a-decision), [questions 7 and 8](#material-type-and-selection) |
| **Item status**, from `506$f` (access category) | **Implemented.** Needs confirming. `Restricted` does not block a request on the status gate, though the request policy is a second gate and is unchecked. Create-only, so an AxC access change never reaches an existing item. | [Section 4](#4-item-status-implemented-needs-confirming), [questions 5 and 6](#item-status) |
| **Identifier format** (HRID) | **Implemented.** A 45 to 49 character string built from the AxC GUID. Whether that format is acceptable is open, and it cannot change after the first run. | [Question 10](#identifiers) |
| **Selection**, from `980$a` (harvest flag) and `351$c` (record level) | **Implemented.** What should happen when the flag is unticked on an already-synced record is current behaviour rather than an agreed rule. | [Question 9](#material-type-and-selection) |
| **Deletion** of a Collect record | **Implemented, both ways.** Suppresses by default; `HARD_DELETE` switches it to an irreversible delete. Which one is policy, not development. | [Question 11](#deletion) |

Nothing in this document is implemented beyond what the State column says is
implemented. The code is unchanged pending these answers.

## Where a run stands today

| | Records | of selectable |
| --- | ---: | --- |
| would sync | 165,220 | 89.8% |
| fails: no `984$b` (normal location) at all | 5,388 | 2.9% |
| fails: `984$b` matches no location rule | 13,284 | 7.2% |
| **total failures** | **18,672** | **10.2%** |

Of 188,019 item-level records, 4,127 (2.2%) carry no harvest flag and are
skipped, leaving 183,892 selectable. Nothing fails on material type, title,
identifier or access category. **The location is the only thing failing records,
and it is section 1.**

## Provenance

| | Source | Date |
| --- | --- | --- |
| All AxC counts | Full scan of `axiell_adapter_table`: 209,374 rows, 131 deleted, 188,019 item-level, 0 parse errors, 183,892 passing both selection gates | 2026-10-12 |
| Location rule coverage | Same table, applying `LOCATION_RULES` to the leading code of every selectable `984$b` (normal location). Pure code, no FOLIO call | 2026-10-13 |
| `540$a` and the loan-type mapping | Same table, applying the mapping in section 2 to every selectable record and cross-tabulating the result against `506$f`. Confirms 0 multi-valued records and the eight-term vocabulary | 2026-10-13 |
| FOLIO reference data | Read-only GETs against prod (`api-wellcome.folio.ebsco.com`, tenant `fs00001190`) on the endpoints `RefCache.load()` uses | 2026-09-29 |
| AxC to MARC | `axiell-collections-xslt`, `axc_to_marcxml_collect.xsl` at `d19f42d` | 2026-06-11 |

Counts are item-level only (record level `351$c == ITEM`), so percentages are of
188,019 unless they are stated against the 183,892 selectable. `984$b` (normal
location) and `540$a` (use restriction) only began arriving on 2026-10-06 and
reached their current coverage by 2026-10-08, so any figure for them dated
earlier is superseded.

One caveat on the readiness table: the 2026-10-12 scan ran against a reference
cache that resolved every tenant lookup, so it could not see a value FOLIO would
reject and reported 5,388 failures. The 2026-10-13 location scan supplies the
13,284 it could not see. The material-type figures are unaffected, because that
table is checked before the resolver.

---

## 1. Permanent location: incomplete, and failing 13,284 records

`984$b` (the AxC normal location) is the sole source of the FOLIO location on
both the holdings and the item, required with no default. Three rules map it,
matching on the leading code of the AxC hierarchy, case-insensitively, first
match wins. A `984$b` of `215/215;B11/215;B11;MR/...` matches on `215`.

| Rule | Matches | FOLIO location | Records | |
| --- | --- | --- | ---: | --- |
| 1 | codes `215`, `183` | `AxC Euston Road` | 165,118 | 89.8% |
| 2 | code `Deepstore` | `AxC Deepstore` | 94 | |
| 3 | prefix `CLW` | `AxC Constantine London West` | 8 | |
| | **no rule matches** | bare code sent to FOLIO | **13,284** | **7.2%** |
| | *(no usable `984$b`)* | fails today | 5,388 | 2.9% |

One rule does almost all the work. Rule 1 carries 89.8% of selectable records
through its two codes, `215` on 141,967 and `183` on 23,151. Rules 2 and 3 match
102 records between them.

**The 13,284 fall-throughs span 2,460 distinct codes, and most are not
locations:**

| | Records | Example `984$b` |
| --- | ---: | --- |
| the born-digital storage service | 5,056 | `wellcomecollection-storage/born-digital` |
| box numbers and shelfmarks | 8,228 | `Box 489: B000002594886`, `Box O/S 123`, `7/1/Box 208`, `MIS/Box 2` |

The box-number group is the more revealing: those records carry no site prefix
at all, so the leading component is the box itself. `984$b` is not uniformly
shaped and the leading-code rule assumes it is, which no amount of rule-adding
fixes cleanly.

The born-digital group is a different problem. `wellcomecollection-storage` is a
storage service, not a shelf, so 5,056 items have no physical location to map.

**What happens to an unmatched code.** It is not rejected by the mapping. The
bare leading code is passed to FOLIO, which syncs the record if the tenant knows
a location by that code or name and fails it with `Unresolved normal location`
otherwise. Nobody would have provisioned `Box 489: B000002594886` as a location,
so in practice these fail, but which of the two happens is decided by the tenant
rather than by anything agreed here.

**To decide:** questions 1, 2 and 3.

---

## 2. Loan type: implemented

The permanent loan type comes from `540$a`: `Online request` and `Restricted`
map to `Can circulate`; all other or absent values map to `Unavailable`.
The tenant has six loan types and none needs
provisioning: `Can circulate`, `Course reserves`, `ILL`, `Reading room`,
`Selected`, `Unavailable`.

### What AxC sends: `540$a` (use restriction)

Present on 183,615 item records (97.7%). Eight terms, measured per record over
the 183,892 selectable:

| `540$a` | Records | |
| --- | ---: | --- |
| `Online request` | 143,787 | 78.2% |
| `By appointment` | 21,314 | 11.6% |
| `Unavailable` | 9,192 | 5.0% |
| `Restricted` | 5,144 | 2.8% |
| `By approval` | 345 | 0.2% |
| `Missing` | 7 | |
| `Deaccessioned` | 6 | |
| `Manual request` | 1 | |
| *(absent)* | 4,096 | 2.2% |

These reconcile to 183,892. **No record carries more than one distinct value**,
so the mapping is a plain lookup needing no precedence rule, which matters
because `extract()` reads the first non-empty subfield.

Two cautions. The vocabulary grew from six terms to eight between 2026-10-06 and
2026-10-12, with `Missing` and `Deaccessioned` appearing late, so it may not be
complete. And those two, plus `Unavailable`, describe access or disposal states
rather than request mechanisms, so `540$a` is not a clean single-purpose
vocabulary.

### The proposal

Only `Online request` and `Restricted` circulate. Everything else, including an
absent `540$a`, is `Unavailable`. `Reading room` is deliberately unused. This is
what we are putting to Collection Information, not something they have agreed.

| `540$a` (use restriction) | Records | | **Proposed loan type** |
| --- | ---: | --- | --- |
| `Online request` | 143,787 | 78.2% | `Can circulate` |
| `Restricted` | 5,144 | 2.8% | `Can circulate` |
| `By appointment` | 21,314 | 11.6% | `Unavailable` |
| `Unavailable` | 9,192 | 5.0% | `Unavailable` |
| *(absent)* | 4,096 | 2.2% | `Unavailable` |
| `By approval` | 345 | 0.2% | `Unavailable` |
| `Missing` | 7 | | `Unavailable` |
| `Deaccessioned` | 6 | | `Unavailable` |
| `Manual request` | 1 | | `Unavailable` |

That gives `Can circulate` on 148,931 records (81.0%) and `Unavailable` on
34,961 (19.0%), summing to 183,892.

96.5% of circulating records are open, and 80 have an access category that
restricts access; see the ceiling discussion below.


**Requestability has two gates, and the loan type feeds one of them.**
mod-circulation selects a request policy from the circulation rules, which match
on `loanTypeId` among their criteria, and rejects a request type the selected
policy disallows. Separately it checks the item status against
`RequestTypeItemStatusWhiteList`. A request has to pass both.

So changing the loan type **can** change requestability, through which request
policy applies. Whether it does here depends on this tenant's request policies
and circulation rules, and neither has been examined. Nothing in this section
should be read as settling what the loan type does or does not control; see
question 5.

**`Can circulate` only has effect through a loan policy, and none has been
examined.** A zero-day or in-house policy makes it mean "issued in the reading
room"; a standard lending policy means unique archival material can leave the
building. 148,931 records depend on which it is, 5,144 of them `Restricted`
material whose access is conditioned.

### The access category as a ceiling: now largely unnecessary

`540$a` (use restriction) and `506$f` (access category) agree almost everywhere.
455 records carry a value on a category that restricts access, all of which
circulated under an earlier draft of this mapping:

| `540$a` (use restriction) | `506$f` (access category) | Records |
| --- | --- | ---: |
| `By approval` | `PERMISSIONREQUIRED` | 343 |
| `Online request` | `MISSING` | 67 |
| `By appointment` | `MISSING` | 16 |
| `By appointment` | `PERMISSIONREQUIRED` | 13 |
| `Online request` | `DATAISSUES` | 11 |
| `By approval` | `SAFEGUARDED` | 1 |
| `Manual request` | `MISSING` | 1 |
| `By appointment` | `CLOSED` | 1 |
| | **total** | **453** |

A ceiling letting the access category narrow but never widen would send all 453
to `Unavailable`. Excluding the `By approval` on `PERMISSIONREQUIRED` pairing,
which is arguably not a conflict since approval is how permission is granted,
gives a narrower ceiling moving **110 records** and leaving that workflow alone.
That is the version worth taking.

**The 453 is a floor.** It was measured by looking only for the four request
mechanisms on a restricting category. Now that `Restricted` and an absent
`540$a` also yield `Can circulate`, more records qualify: `CLOSED` totals 9,819
while only 8,851 carry `540$a` of `Unavailable`, so roughly 950 closed records
take their loan type from something else. The exact total is unmeasured and
should be known before implementing.

Separately, 332 records say `Unavailable` in `540$a` while `506$f` says `OPEN`.
A ceiling does not catch these; under the table above they take `Unavailable`.

**Implemented** in mapping version 2.8.0, as the table above
(`USE_RESTRICTION_LOAN_TYPE` in `mapping/config.py`), tracked on
wellcomecollection/platform#6774. `DEFAULT_LOAN_TYPE` is now `Unavailable`, and
an unlisted term takes it rather than failing the record. The access-category
ceiling is not implemented, so the 453-plus records discussed above circulate if
their `540$a` says so. The loan policy behind `Can circulate` (question 4) is
still unexamined.

---

## 3. Material type: three rows need a decision

Implemented and exhaustive over the corpus. The tenant has 29 material types and
every target already exists.

| `655$a` (object category) | Records | | Maps to | |
| --- | ---: | --- | --- | --- |
| `Archives - Non-digital` | 176,133 | 93.7% | `archive` | |
| `Visual Material` | 4,347 | 2.3% | `non-projected graphic` | |
| **`Sound - Digital`** | 2,860 | 1.5% | `audio format non-requestable` | **needs CI** |
| **`Archives - Digital`** | 2,535 | 1.3% | `archive` | **needs CI** |
| `Moving Image - Non-digital` | 1,048 | 0.6% | `film` | |
| `Sound - Non-digital` | 894 | 0.5% | `audio format requestable` | |
| **`Moving Image - Digital`** | 85 | | `video format non-requestable` | **needs CI** |
| `Pictures` | 1 | | `non-projected graphic` | |
| `Archives - Hybrid` | 1 | | `archive` | |
| *(absent)* | 115 | | nothing: the record fails | |

**This tenant encodes requestability in the material type** (note the
`requestable` / `non-requestable` pairs). So the three digital rows also decide
whether a reader can request that material, which is why they need an answer
rather than a default. The reasoning applied is that a digital surrogate is not
the carrier a reader requests, and that no digital-archive type exists so
`Archives - Digital` takes the same intellectual form as the rest;
`computer media` and `migration` are the alternatives.

The table is strict: a value absent from it fails the record before the tenant
is consulted, and there is no default. A new AxC term therefore halts those
records until someone maps it. No value in the corpus currently falls outside
the table.

**To decide:** questions 7 and 8.

---

## 4. Item status: implemented, needs confirming

Every value is in FOLIO's fixed item-status enum, so nothing needs provisioning.

| `506$f` (access category) | Records | Item status |
| --- | ---: | --- |
| `OPEN`, `OPENWITHADVISORY` | 171,830 | `Available` |
| `RESTRICTED` | 5,496 | `Available` |
| `PERMISSIONREQUIRED`, `SAFEGUARDED` | 357 | `Restricted` |
| `CLOSED` | 9,819 | `Unavailable` |
| `MISSING` | 160 | `Missing` |
| `DATAISSUES` | 24 | `Unknown` |
| `DEACCESSIONED` | 13 | `Withdrawn` |
| *(absent)* | 320 | `Unavailable` |

`RESTRICTED` maps to `Available` because restricted material is genuinely
available and can be requested online. The restriction is that the reader signs
to agree to the viewing conditions, which happens before the material is handed
over, so it does not affect whether the item can be requested or produced.

The status is **create-only**: the sync sends it on create, and on later runs
reads the item back and sends FOLIO's own status unchanged, because circulation
owns the field once the item exists. An AxC access change therefore never
reaches an existing item. That is question 6.

`Restricted` and `Withdrawn` did not appear in a 5,000-item prod sample and
should be confirmed against this tenant's FOLIO version.

**To decide:** questions 5 and 6.

---

## 5. Decisions needed from Collection Information

### Location

1. Should a `984$b` (normal location) whose leading code matches no rule fail
   the record outright, as the material type now does, rather than being passed
   to FOLIO to land wherever it happens to match?
2. What location should the 5,056 born-digital items take, if any? Should they
   be in this sync at all?
3. Do the 8,228 box-number records need more rules, or a stylesheet fix so that
   `984$b` always carries its site prefix?

### Loan type

4. Confirm `Can circulate` for `Online request` and `Restricted`, 148,931
   records, and `Unavailable` for the other 34,961. Note that this gives
   `Unavailable` to 25,023 records whose access category is `OPEN`, almost all
   of them `By appointment`. Then confirm what loan policy the circulation rules
   attach to `Can circulate`, which is what decides whether archival material
   can leave the building.


### Item status
5. **Is `Restricted` meant to stop a request?** On the item-status gate alone,
   it does not: `Restricted` is pageable in mod-circulation, like `Available`.
   The 357 `Restricted` records (`PERMISSIONREQUIRED` and `SAFEGUARDED`) are
   therefore fully pageable, subject to the tenant's request policy and
   circulation rules. `Unavailable`, `Unknown` and `Withdrawn` block requests,
   while `Missing` allows holds only.

6. Is "set the item status once, when the item is created" good enough? The cost
   is that an AxC access change never reaches an existing item. If that is not
   acceptable, the proposal is split ownership: the sync overwrites only the
   statuses it derives (`Available`, `Restricted`, `Missing`, `Withdrawn`,
   `Unavailable`) and leaves circulation statuses alone.

### Material type and selection

7. What material type for `Sound - Digital`, `Archives - Digital` and
   `Moving Image - Digital`? Because requestability is encoded in the material
   type, that choice also decides whether a reader can request them.
8. Is failing the right behaviour for the 115 records with no `655$a` (object
   category), or would you rather they defaulted to `archive` and synced?
9. **When the Harvest flag is unticked on a record that has already synced,
   should its FOLIO records be suppressed, or left?** Today they are left: the
   record stops passing the selection gate, so it is skipped and never updated
   again, while its instance, holdings and item stay in FOLIO unsuppressed with
   whatever data they last had. Nothing reclaims them, because the only delete
   path is the reconciler working from superseded GUIDs and a flag removal is
   not a delete signal. That is current behaviour rather than an agreed rule,
   and it is the less safe reading: if curators untick Harvest to withdraw
   something from the LMS, that intent is silently dropped. Tracked in
   wellcomecollection/platform#6663.

### Identifiers

10. **Is the FOLIO identifier format acceptable?** Each record gets a
    predictable hrid built from the Axiell GUID in `001` (record identifier),
    which is always a 36-character UUID:

    | FOLIO record | hrid | Length |
    | --- | --- | ---: |
    | Instance | `AxC-instance-44c350e7-79d9-4e87-aa1b-ea14ecc22dc8` | 49 |
    | Holdings | `AxC-holding-44c350e7-79d9-4e87-aa1b-ea14ecc22dc8` | 48 |
    | Item | `AxC-item-44c350e7-79d9-4e87-aa1b-ea14ecc22dc8` | 45 |

    The `AxC-` prefix identifies the record as synced from Axiell; the hrid also
    includes the record type and Axiell GUID, making each record easy to find
    and identify. The human-readable archival reference is carried, but
    as the instance's `Local identifier` (for example `WMS/Amer.147`, from
    `035$a(AltRefNo)`, the object number), not as the hrid.

    **This needs settling before the first production run.** The hrid is how the
    sync finds its own records again on every later run, and how the reconciler
    resolves deletions, so changing the format afterwards would orphan every
    record already written.

    Two smaller points. The prefixes are inconsistent: `AxC-holding-` is
    singular where FOLIO's entity is a holdings record. And whether FOLIO
    constrains hrid length or character set has not been verified.

### Deletion

11. **When a Collect record is deleted, should its FOLIO records be suppressed
    or hard deleted?** Both paths are implemented and the choice is a runtime
    flag, so this is a policy decision rather than development work.

    | | What happens | Reversible |
    | --- | --- | --- |
    | **Suppress** (default) | `discoverySuppress` set on the instance, holdings and item; `staffSuppress` on the instance as well, since it is the only entity FOLIO gives that field | Yes. The records stay, out of discovery, and can be unsuppressed |
    | **Hard delete** | the item, then the holdings, then the instance are DELETEd | **No** |

    Today it suppresses. `HARD_DELETE` defaults to `false`, and the per-run
    `hard_delete` flag on the event overrides it, so switching is a
    configuration change.

    Points worth weighing. Suppression keeps an audit trail but can leave
    deleted records visible to cataloguers. Hard delete avoids orphaning parent
    records by deleting child records first, but it is irreversible; if a delete
    is issued in error, the sync would only recreate the records if the AxC
    record reappeared and passed the harvest gate again.

    This is different from question 9: there, unticking Harvest means nothing
    reaches FOLIO at all; here, Collect sends an actual deletion fact and the
    sync acts on it.

---

## Before implementing

**On the FOLIO tenant**, none of these verified: that `Restricted` and
`Withdrawn` are accepted by this FOLIO version; what loan policy the circulation
rules attach to `Can circulate` and whether `Unavailable` genuinely prevents a
loan; and that the three `AxC *` location names still resolve.

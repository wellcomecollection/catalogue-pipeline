# Axiell to FOLIO mapping: what is missing

A decision document for Collection Information. It covers the fields where the
mapping is incomplete, undecided or absent, in that order. For what the sync
maps today, see
[axiell-folio-field-mappings.md](axiell-folio-field-mappings.md).

## State

| Mapping | State | Where |
| --- | --- | --- |
| **Permanent location**, from `984$b` (normal location) | **Mapped, incomplete.** Three rules cover 89.8% of records; 13,284 (7.2%) match no rule and fail. The only thing failing records today. | Section 1, questions 1 to 3 |
| **Loan type** | **Proposed, not agreed.** Constant `Can circulate` for every record today. The proposal derives it from `540$a` (use restriction). | Section 2, questions 4 and 5 |
| **Material type**, from `655$a` (object category) | **Implemented and exhaustive** over the corpus, with no default. The three digital rows were decided on our own reasoning and need confirming. | Section 3, questions 7 and 8 |
| **Item status**, from `506$f` (access category) | **Implemented.** Needs confirming, and note that `Restricted` does not block a request. Create-only, so an AxC access change never reaches an existing item. | Section 4, questions 5 and 6 |
| **Identifier format** (HRID) | **Implemented.** A 45 to 49 character string built from the AxC GUID. Whether that format is acceptable is open, and it cannot change after the first run. | Question 10 |
| **Selection**, from `980$a` (harvest flag) and `351$c` (record level) | **Implemented.** What should happen when the flag is unticked on an already-synced record is current behaviour rather than an agreed rule. | Question 9 |

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

| Leading code | FOLIO location | Records | |
| --- | --- | ---: | --- |
| `215` | `AxC Euston Road` | 141,967 | 77.2% |
| `183` | `AxC Euston Road` | 23,151 | 12.6% |
| `Deepstore` | `AxC Deepstore` | 94 | |
| starts `CLW` | `AxC Constantine London West` | 8 | |
| **no rule matches** | bare code sent to FOLIO | **13,284** | **7.2%** |
| *(no usable `984$b`)* | fails today | 5,388 | 2.9% |

Two rules carry 89.8% of the corpus. The other two match 102 records between
them.

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

## 2. Loan type: proposed, not agreed

Every item is written `Can circulate` today, undifferentiated, because no AxC
field is mapped to the loan type. The tenant has six loan types and none needs
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

Everything consultable is `Can circulate`, open and conditioned alike; only the
three states where the item cannot be produced fall outside it. `Reading room`
is deliberately unused. This is what we are putting to Collection Information,
not something they have agreed.

| `540$a` (use restriction) | Records | **Proposed loan type** |
| --- | ---: | --- |
| `Online request`, `By appointment`, `By approval`, `Manual request`, `Restricted`, *(absent)* | 174,687 | `Can circulate` |
| `Unavailable`, `Missing`, `Deaccessioned` | 9,205 | `Unavailable` |

The default for an absent `540$a` is `Can circulate`, since 4,020 of those 4,096
records carry `506$f` (access category) of `OPEN`.

**A loan type does not gate requests.** It feeds the circulation rules that set
loan period and renewability. Whether a request is accepted comes from the item
status, through mod-circulation's `RequestTypeItemStatusWhiteList`, so no choice
here makes closed material unrequestable. That is question 5.

**`Can circulate` only has effect through a loan policy, and none has been
examined.** A zero-day or in-house policy makes it mean "issued in the reading
room"; a standard lending policy means unique archival material can leave the
building. 174,687 records depend on which it is, including 5,489 whose access is
conditioned.

### Recommended addition: the access category as a ceiling

`540$a` (use restriction) and `506$f` (access category) agree almost everywhere,
but 453 records carry a permissive mechanism on a category that restricts
access, and all 453 resolve to `Can circulate` under the table above:

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

**Not implemented.** `DEFAULT_LOAN_TYPE` remains the constant `Can circulate`
for every record. That coincides with what the proposal gives consultable
material, but the 9,205 it would mark `Unavailable` are currently
`Can circulate` too. Nothing changes in the code until CI confirms the table.

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
| `PERMISSIONREQUIRED`, `SAFEGUARDED`, `CLOSED` | 10,176 | `Restricted` |
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

**Location** (section 1, the only thing failing records today)

1. Should a `984$b` (normal location) whose leading code matches no rule fail
   the record outright, as the material type now does, rather than being passed
   to FOLIO to land wherever it happens to match?
2. What location should the 5,056 born-digital items take, if any? Should they
   be in this sync at all?
3. Do the 8,228 box-number records need more rules, or a stylesheet fix so that
   `984$b` always carries its site prefix?

**Loan type** (section 2)

4. Confirm `Can circulate` for everything consultable, 174,687 records, and
   `Unavailable` for the 9,205 that cannot be produced. Then confirm what loan
   policy the circulation rules attach to `Can circulate`, which is what decides
   whether archival material can leave the building.
5. **Is `Restricted` meant to stop a request?** It does not. In
   mod-circulation's `RequestTypeItemStatusWhiteList` on master, `Restricted`
   allows page, hold and recall (`pageRules.put(RESTRICTED, true)`), the same as
   `Available` does for paging. `Unavailable`, `Unknown` and `Withdrawn` allow
   none. So mapping `CLOSED`, `PERMISSIONREQUIRED` and `SAFEGUARDED` to
   `Restricted` changes what FOLIO displays while a request on those items is
   still accepted. `Unavailable` is the status that blocks. That affects 10,176
   records. The dev instance has no `Restricted` items today, so the first run
   after a clear is the chance to confirm the behaviour there.

**Item status** (section 4)

6. Is "set the item status once, when the item is created" good enough? The cost
   is that an AxC access change never reaches an existing item. If that is not
   acceptable, the proposal is split ownership: the sync overwrites only the
   statuses it derives (`Available`, `Restricted`, `Missing`, `Withdrawn`,
   `Unavailable`) and leaves circulation statuses alone.

**Material type and selection**

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

**Identifiers**

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

---

## Before implementing

**On the FOLIO tenant**, none of these verified: that `Restricted` and
`Withdrawn` are accepted by this FOLIO version; what loan policy the circulation
rules attach to `Can circulate` and whether `Unavailable` genuinely prevents a
loan; and that the three `AxC *` location names still resolve, last confirmed
2026-09-29.

**`907$a`**, a UUID present on every harvested record, is unidentified. If it is
already a FOLIO identifier, the sync may be creating duplicates of records that
already exist, which would be a correctness problem rather than a mapping
choice. Worth establishing before a production run.

**On the AxC side**, the full use-restriction thesaurus. Eight terms are visible
as of 2026-10-12, up from six on 2026-10-06, and that growth while the field was
being read is the reason to doubt the list is complete.

**Measurements still owed:** the exact count of records that would get
`Can circulate` despite a restricting access category (the 453 above is a
floor), and whether any fall-through location code happens to resolve on the
tenant.

wellcomecollection/platform#6746 tracks the adapter refresh that was expected to
populate `984$b` (normal location). That has largely happened through the
stylesheet rollout, so the issue should be re-read against these figures rather
than actioned as written.

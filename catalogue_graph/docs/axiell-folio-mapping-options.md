# Axiell to FOLIO mapping: what is missing

A decision document for Collection Information. It covers the fields where the
mapping is incomplete, undecided or absent, in that order. For what the sync
maps today, see
[axiell-folio-field-mappings.md](axiell-folio-field-mappings.md).

## State

| Mapping | State | Where |
| --- | --- | --- |
| **Permanent location**, from `984$b` (normal location) | **Mapped, incomplete.** Three rules cover 96.0% of selectable records; 3,470 (1.9%) match no rule and fail, and 3,672 (2.0%) have no `984$b` at all. The only thing failing records today, bar two. | [Section 1](#1-permanent-location-incomplete-and-failing-3470-records), [questions 1 to 3](#location) |
| **Loan type** | **Implemented** (mapping version 2.8.0). Derived from `540$a` (use restriction): `Can circulate` for `Online request` and `Restricted` only, `Unavailable` for the rest, including an absent or unlisted term. No access-category ceiling. | [Section 2](#2-loan-type-implemented), [questions 4 and 5](#loan-type) |
| **Material type**, from `655$a` (object category) | **Implemented and exhaustive** over the corpus, with no default. The three digital rows were decided on our own reasoning and need confirming. | [Section 3](#3-material-type-three-rows-need-a-decision), [questions 7 and 8](#material-type-and-selection) |
| **Item status**, from `506$f` (access category) | **Implemented.** Needs confirming. Since mapping version 3.6.0 `PERMISSIONREQUIRED` and `SAFEGUARDED` give `Available`, as on migrated items, so the sync no longer sets `Restricted`. Since mapping version 3.4.0 an AxC access change reaches an existing item while FOLIO holds a status the sync sets, and circulation statuses are left alone (wellcomecollection/platform#6862). | [Section 4](#4-item-status-implemented-needs-confirming), [questions 5 and 6](#item-status) |
| **Statistical code** (access) | **Agreed rows implemented** (mapping version 3.4.0, wellcomecollection/platform#6862). One code on the item from the `506$f` and `540$a` pair, as an explicit table of the rows Collection Information agreed; any other pair gets no code. `CLOSED` and `DONOR PERMISSION` were not in the prod tenant when last checked, and a code the tenant lacks fails the record. | [Section 5](#5-statistical-code-agreed-rows-implemented), [questions 12 and 13](#statistical-code) |
| **Record identity** (HRID) | **Implemented** (ids in mapping version 3.0.0, labels and marker in 3.1.0, item GUID label dropped in 3.2.0, marker moved to the instance status and holdings GUID label dropped in 3.5.0). Records are found by an id derived from the AxC GUID (the GUID itself for the instance, a `uuid5` in it for the holdings and item), and the sync sends no HRID, so FOLIO generates it. The instance carries the `Synched by Axiell` instance status as the synced marker, which still has to be created on prod. Tracked in wellcomecollection/platform#6805 and wellcomecollection/platform#6863. | [Question 10](#identifiers) |
| **Selection**, from `980$a` (harvest flag) and `351$c` (record level) | **Decided and implemented** (mapping version 3.3.0). Unticking Harvest suppresses an item-level record's FOLIO records; ticking it again restores the same records. Tracked in wellcomecollection/platform#6834. | [Question 9](#material-type-and-selection) |
| **Deletion** of a Collect record | **Decided and implemented: hard delete.** Both a superseded GUID and a Collections tombstone delete the item, holdings and instance, child first. `HARD_DELETE` is `true` in Terraform. Tracked in wellcomecollection/platform#6833. | [Question 11](#deletion) |

Nothing in this document is implemented beyond what the State column says is
implemented. The code is unchanged pending these answers.

## Where a run stands today

| | Records | of selectable |
| --- | ---: | --- |
| would sync | 171,985 | 96.0% |
| fails: no `984$b` (normal location) at all | 3,672 | 2.0% |
| fails: `984$b` matches no location rule | 3,470 | 1.9% |
| fails: access category with no item status | 2 | |
| **total failures** | **7,144** | **4.0%** |

Of 188,083 item-level records, 8,954 (4.8%) carry no harvest flag, leaving
179,129 selectable. Nothing fails on material type, loan type, statistical
code, title or identifier. **The location is what fails records, and it is
section 1.**

## What to expect from a run

The scheduled sync targets the FOLIO test environment
(`api-wellcome-test.folio.ebsco.com`), a copy of the production tenant, and
writes to it once `dry_run_default = false` is applied in Terraform. A run that
targets prod is always a dry run, whatever the event or Terraform says, until
production writes are switched on.

**Each run is incremental.** It processes only the records in the changesets of
its `axiell.adapter.completed` event, so a run sends what changed in that
harvest, not the whole table. The table above is what a full backfill would
produce: about 172,000 records created, each as an instance, a holdings record
and an item, with about 7,100 failures reported in the run manifest.

**Per record**, a run does one of the following:

| Record | What happens |
| --- | --- |
| item-level, harvest flag set, maps | created, or updated if it already exists |
| item-level, harvest flag set, fails to map | reported as an error and not written; its existing FOLIO records are hidden from discovery when a mandatory field is missing or the record is withheld |
| item-level, no harvest flag | its FOLIO records are suppressed if they exist; otherwise skipped |
| not item-level | skipped |
| deleted in Collections, or its GUID superseded | its item, holdings and instance are hard deleted |

**The test tenant has the reference data the mapping needs.** The `Synched by
Axiell` instance status and the `Axiell Current Location` and `Axiell Normal
Location` item note types were added on 2026-11-20. With them in place, the
only failures are the location and access-category ones above. Prod does not
have them yet, and needs the same three values before it is switched on.

**A one-record live run has passed.** Record `collect:110098305` created an
instance, holdings record and item on the test tenant, with status `Available`,
loan type `Can circulate`, location `AxC Euston Road` and material type
`archive`. A dry run straight after planned three updates rather than creates,
so a second run finds the same records rather than duplicating them. This is the
check wellcomecollection/platform#6849 asked for before `dry_run_default` was
switched to `false`.

## Provenance

| | Source | Date |
| --- | --- | --- |
| Readiness table and section 1 | Full read-only scan of `axiell_adapter_table`: 209,374 rows, 103 deleted, 188,083 item-level, 0 parse errors, 179,129 passing both selection gates. Each record mapped with `select_and_build` against the test tenant's reference data, loaded with GETs only | 2026-11-23 |
| Test tenant reference data | Read-only GETs against `api-wellcome-test.folio.ebsco.com`, tenant `fs00001190`: the three `AxC *` locations, `Synched by Axiell`, both Axiell item note types and the `CLOSED` and `DONOR PERMISSION` statistical codes all present | 2026-11-23 |
| One-record live run | `collect:110098305` created on the test tenant, then a dry run planned updates | 2026-11-20 |
| Section 2 to 5 counts | Full scan of `axiell_adapter_table`: 209,374 rows, 131 deleted, 188,019 item-level, 0 parse errors, 183,892 passing both selection gates | 2026-10-12 |
| `540$a` and the loan-type mapping | Same table, applying the mapping in section 2 to every selectable record and cross-tabulating the result against `506$f`. Confirms 0 multi-valued records and the eight-term vocabulary | 2026-10-13 |
| FOLIO reference data | Read-only GETs against prod (`api-wellcome.folio.ebsco.com`, tenant `fs00001190`) on the endpoints `RefCache.load()` uses | 2026-09-29 |
| Record identity | GUID format from the first 2,000 adapter rows (UUIDv4 in every `001`). Client-supplied ids and FOLIO-generated hrids on the dev sandbox (instance, holdings, item) and on prod (one instance, created then deleted). `AXIELL` code on the sandbox only (the marker became the `Synched by Axiell` instance status in mapping 3.5.0). See [axiell-folio-record-identity.md](axiell-folio-record-identity.md) | 2026-10-20 |
| Statistical code vocabulary | [decision-table.md](folio/access-conditions/decision-table.md) (platform#6589), from the prod tenant pull | 2026-10-01 |
| AxC to MARC | `axiell-collections-xslt`, `axc_to_marcxml_collect.xsl` at `d19f42d` | 2026-06-11 |

Counts are item-level only (record level `351$c == ITEM`). The readiness table
and section 1 are from 2026-11-23, with percentages of the 179,129 selectable.
Sections 2 to 5 are still from the October scans, with percentages of 188,019
item-level records unless they are stated against the 183,892 selectable. `984$b` (normal
location) and `540$a` (use restriction) only began arriving on 2026-10-06 and
reached their current coverage by 2026-10-08, so any figure for them dated
earlier is superseded.

The 2026-11-23 scan resolved every lookup against the test tenant, so a value
the tenant lacks shows up as a failure. It does not check prod, which still
lacks the values listed under [What to expect from a run](#what-to-expect-from-a-run).

---

## 1. Permanent location: incomplete, and failing 3,470 records

`984$b` (the AxC normal location) is the sole source of the FOLIO location on
both the holdings and the item, required with no default. Three rules map it,
matching on the leading code of the AxC hierarchy, case-insensitively, first
match wins. A `984$b` of `215/215;B11/215;B11;MR/...` matches on `215`.

| Rule | Matches | FOLIO location | Records | |
| --- | --- | --- | ---: | --- |
| 1 | codes `215`, `183` | `AxC Euston Road` | 167,235 | 93.4% |
| 2 | code `Deepstore` | `AxC Deepstore` | 4,744 | 2.6% |
| 3 | prefix `CLW` | `AxC Constantine London West` | 8 | |
| | **no rule matches** | bare code sent to FOLIO | **3,470** | **1.9%** |
| | *(no usable `984$b`)* | fails today | 3,672 | 2.0% |

One rule does almost all the work. Rule 1 carries 93.4% of selectable records
through its two codes, `215` on 143,247 and `183` on 23,988. Rule 2 has grown
from 94 records in October to 4,744, and rule 3 matches 8.

**The 3,470 fall-throughs span 2,431 distinct codes, and most are not
locations:**

| | Records | Example leading code |
| --- | ---: | --- |
| box numbers | 3,017 | `Box 336: B000003172212`, `Box A0089` |
| the born-digital storage service | 404 | `wellcomecollection-storage` |
| other shelfmarks | 49 | `S 41`, `RTO`, `AMS` |

None of them resolves on the test tenant.

The box-number group is the more revealing: those records carry no site prefix
at all, so the leading component is the box itself. `984$b` is not uniformly
shaped and the leading-code rule assumes it is, which no amount of rule-adding
fixes cleanly.

The born-digital group is a different problem. `wellcomecollection-storage` is a
storage service, not a shelf, so 404 items have no physical location to map.

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

Implemented and exhaustive over the corpus. The test tenant had 29 material
types on 2026-11-23, and every target existed under its old name. Mapping
version 3.6.0 uses the renamed audio and video types (`audio - digital`,
`audio`, `video - digital`, and `video` with no row mapping to it), which were
not yet on the test tenant that day, so the 3,839 audio and video records fail
until the types are renamed there.

| `655$a` (object category) | Records | | Maps to | |
| --- | ---: | --- | --- | --- |
| `Archives - Non-digital` | 176,133 | 93.7% | `archive` | |
| `Visual Material` | 4,347 | 2.3% | `non-projected graphic` | |
| **`Sound - Digital`** | 2,860 | 1.5% | `audio - digital` | **needs CI** |
| **`Archives - Digital`** | 2,535 | 1.3% | `archive` | **needs CI** |
| `Moving Image - Non-digital` | 1,048 | 0.6% | `film` | |
| `Sound - Non-digital` | 894 | 0.5% | `audio` | |
| **`Moving Image - Digital`** | 85 | | `video - digital` | **needs CI** |
| `Pictures` | 1 | | `non-projected graphic` | |
| `Archives - Hybrid` | 1 | | `archive` | |
| *(absent)* | 115 | | nothing: the record fails | |

**This tenant encodes requestability in the material type** (the old names
were `requestable` / `non-requestable` pairs, and the new ones are plain and
`- digital` pairs). So the three digital rows also decide
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
| `PERMISSIONREQUIRED`, `SAFEGUARDED` | 357 | `Available` |
| `CLOSED` | 9,819 | `Unavailable` |
| `MISSING` | 160 | `Missing` |
| `DATAISSUES` | 24 | `Unknown` |
| `DEACCESSIONED` | 13 | `Withdrawn` |
| *(absent)* | 320 | `Unavailable` |

`RESTRICTED` maps to `Available` because restricted material is genuinely
available and can be requested online. The restriction is that the reader signs
to agree to the viewing conditions, which happens before the material is handed
over, so it does not affect whether the item can be requested or produced.

`PERMISSIONREQUIRED` and `SAFEGUARDED` map to `Available` since mapping version
3.6.0, as Collection Information asked: migrated safeguarded and donor
permission items are `Available`, and the access statistical code carries the
restriction. They were `Restricted` before.

Until mapping version 3.4.0 the status was **create-only**, so an AxC access
change never reached an existing item. Question 6 was settled on
wellcomecollection/platform#6862 with split ownership: on update the sync
replaces the status only while FOLIO holds one the sync itself sets
(`Available`, `Unavailable`, `Unknown`, `Missing`, `Withdrawn`), and leaves
circulation statuses such as `Checked out` alone.

`Withdrawn` did not appear in a 5,000-item prod sample and should be confirmed
against this tenant's FOLIO version.

Question 5 is settled by the 3.6.0 change.

---

## 5. Statistical code: agreed rows implemented

**Implemented in mapping version 3.4.0** (wellcomecollection/platform#6862), as
an explicit table of the `506$f` and `540$a` pairs Collection Information
agreed, rather than the precedence rules proposed below. A pair outside that
table, including `OPEN` with `Unavailable`, `MISSING` with `Missing` and
`DEACCESSIONED`, gets no access code. The code goes on the item and is replaced
on every run. The proposal below is kept as the
reasoning behind it. See
[axiell-folio-field-mappings.md](axiell-folio-field-mappings.md) for what the
sync does.

The access condition decision table for migrated Sierra items
([decision-table.md](folio/access-conditions/decision-table.md), platform#6589)
decides most outcomes on the item's statistical code, which is where the
migration put the Sierra OPAC message, one code per value. The sync writes no
such code, so a synced item that no status row catches reaches the table's
`fallback` row: not requestable, with the contact note. This section proposes
sending one code from the same vocabulary, so synced items go through the same
rows as migrated ones.

### The vocabulary

The codes the migration created, as the tenant held them on 2026-10-01, and the
row of the decision table each one drives:

| Statistical code | Row | What the reader sees |
| --- | --- | --- |
| `ONLINE REQUEST` | `online-request`, closed stores only | online request, open |
| `RESTRICTED` | `restricted`, closed stores only | online request, restricted |
| `BY APPOINTMENT` | `by-appointment` | manual request, by appointment |
| `MANUAL REQUEST` | `manual-request` | manual request, with the manual-request note |
| `SAFEGUARDED` | `safeguarded` | not requestable, safeguarded |
| `DIGITISATION` | `digitisation` | not requestable, temporarily unavailable |
| `OPEN SHELVES` | `open-shelves`, open shelves only | open shelves |
| `UNAVAILABLE` | none: the table keys unavailability on the item status | |
| `AVAILABLE` | none: Collection Information lists it as an incorrect mapping meant for bound-with items | |

Two more codes have rows but were not in the tenant: `CLOSED` (`closed`: not
requestable, closed) and `DONOR PERMISSION` (`donor-permission`: manual request,
permission required). Both would need provisioning before the sync could send
them.

### The proposal

One code per item. `506$f` (access category) decides it where it names a state
the table holds as a code. Otherwise `540$a` (use restriction) decides it,
because it is the AxC field closest to the OPAC message: it says how a reader
gets the item, and its terms are mostly the OPAC message terms already. This is
a proposal to Collection Information, not something they have agreed.

| AxC value | Records | Statistical code | Item status | Row reached |
| --- | ---: | --- | --- | --- |
| `506$f` `CLOSED` | 9,819 | `CLOSED` | `Unavailable`, unchanged | `closed` |
| `506$f` `SAFEGUARDED` | in 357 | `SAFEGUARDED` | `Available` (3.6.0), was `Restricted` | `safeguarded` |
| `506$f` `PERMISSIONREQUIRED` | in 357 | `DONOR PERMISSION` | `Available` (3.6.0), was `Restricted` | `donor-permission` |
| otherwise `540$a` `Online request` | 143,787 | `ONLINE REQUEST` | from `506$f`, as today | `online-request` |
| `540$a` `Restricted` | 5,144 | `RESTRICTED` | as today | `restricted` |
| `540$a` `By appointment` | 21,314 | `BY APPOINTMENT` | as today | `by-appointment` |
| `540$a` `By approval` | 345 | `DONOR PERMISSION` | as today | `donor-permission` |
| `540$a` `Manual request` | 1 | `MANUAL REQUEST` | as today | `manual-request` |
| `540$a` `Unavailable` | 9,192 | `UNAVAILABLE` | as today | `unavailable` if the status is `Unavailable`, otherwise `not-circulating` (contact note) |
| `540$a` `Missing`, `Deaccessioned`, absent | 4,109 | none | as today | the status row, otherwise `not-circulating` |

Counts are per source value over the 183,892 selectable records, from the
2026-10-12 and 2026-10-13 scans. They overlap, because the three `506$f` rows
take precedence over `540$a` (343 of the 345 `By approval` records are
`PERMISSIONREQUIRED`, for instance), and the distribution after that precedence
has not been measured.

The table's status rows (`Missing`, `Withdrawn`, `Unknown`) still come first, as
they do for migrated items. A `MISSING` item keeps the code from its `540$a`
underneath, as 1,847 migrated `ONLINE REQUEST` items do, so once staff find it
and set it `Available` it reaches the right row with nothing else changed.

**The item status change.** Collection Information keeps migrated restricted, by
appointment, safeguarded and donor permission items `Available`, so that staff
can still request them, and lets the code carry the restriction. The proposal
moves the 357 `SAFEGUARDED` and `PERMISSIONREQUIRED` records from `Restricted`
to `Available` to match. On the item-status gate nothing changes, since
`Restricted` is pageable like `Available` (question 5). What changes is that
synced and migrated items look the same to staff, and that `Restricted`, not yet
confirmed on this FOLIO version, drops out of the mapping.

### What it depends on

- **Location type.** `online-request` and `restricted` match closed stores only.
  `AxC Euston Road` and `AxC Constantine London West` sit in the `Axiell sync`
  libraries, which the location-type rule does not cover;
  [location-mapping.md](folio/location-mapping.md) proposes closed stores for
  them, pending. Until that lands, every `ONLINE REQUEST` and `RESTRICTED`
  synced item outside `AxC Deepstore` reaches `fallback`.
- **Loan type.** `not-circulating` reads a loan type of `Unavailable`, but sits
  below the restricted states, so the `Unavailable` loan type that mapping 2.8.0
  gives `By appointment`, `By approval` and `Manual request` does not change
  what the reader sees. It does differ from migrated items, which Collection
  Information keeps `Can circulate` so staff can request them. That is part of
  question 4.
- **Material type.** The table has no row for the audiovisual material types, so
  a `Sound - Digital` or `Moving Image - Digital` item with `Online request` in
  `540$a` would be offered online, whatever its non-requestable material type
  says. Question 7 decides the material type; question 12 asks whether the code
  should follow it.
- **The synced marker.** Since mapping 3.5.0 the marker is the instance status
  (question 10), not a statistical code, so the access code is the only code the
  sync puts on the item. On update the sync replaces only the code it owns from
  the access vocabulary and keeps the rest.
- **It is not create-only.** Circulation does not own statistical codes, so the
  sync can send the code on every run, and an AxC access change reaches the
  reader through it even though the status does not. That narrows question 6
  without settling it: an item created `CLOSED` keeps the status `Unavailable`,
  and the table's `unavailable` row then shows it as temporarily unavailable
  whatever its new code.

**To decide:** questions 12 and 13.

---

## 6. Decisions needed from Collection Information

### Location

1. Should a `984$b` (normal location) whose leading code matches no rule fail
   the record outright, as the material type now does, rather than being passed
   to FOLIO to land wherever it happens to match?
2. What location should the 404 born-digital items take, if any? Should they
   be in this sync at all?
3. Do the 3,017 box-number records need more rules, or a stylesheet fix so that
   `984$b` always carries its site prefix?

### Loan type

4. Confirm `Can circulate` for `Online request` and `Restricted`, 148,931
   records, and `Unavailable` for the other 34,961. Note that this gives
   `Unavailable` to 25,023 records whose access category is `OPEN`, almost all
   of them `By appointment`. Then confirm what loan policy the circulation rules
   attach to `Can circulate`, which is what decides whether archival material
   can leave the building.


### Item status
5. **Settled in mapping version 3.6.0: `PERMISSIONREQUIRED` and `SAFEGUARDED`
   give `Available`.** Is `Restricted` meant to stop a request? On the item-status gate alone,
   it does not: `Restricted` is pageable in mod-circulation, like `Available`.
   The 357 `Restricted` records (`PERMISSIONREQUIRED` and `SAFEGUARDED`) are
   therefore fully pageable, subject to the tenant's request policy and
   circulation rules. `Unavailable`, `Unknown` and `Withdrawn` block requests,
   while `Missing` allows holds only.

6. **Settled on wellcomecollection/platform#6862, split ownership, implemented
   in mapping version 3.4.0.** Is "set the item status once, when the item is
   created" good enough? The cost
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
   should its FOLIO records be suppressed, or left?** Decided: suppressed, and
   restored when the flag is ticked again. Agreed with Collection Information on
   wellcomecollection/platform#6663, implemented in mapping 3.3.0
   (wellcomecollection/platform#6834). An item-level record without `980$a`
   has `discoverySuppress` set on its item, holdings and instance and
   `staffSuppress` on the instance, never a hard delete. Ticked again, it updates
   the same three records and clears the flags. Two consequences were settled
   in the implementation rather than with Collection Information, and are
   worth confirming:

   - The flags are cleared on every update of a selected record, because FOLIO
     does not record who suppressed it. A suppression set by hand in FOLIO on a
     record that is still ticked is undone on its next update.
   - A record that stops being item-level (`351$c`) is skipped, not suppressed.

### Identifiers

10. **Is the FOLIO identifier format acceptable?** Decided: no. Each record
    used to get an hrid built from the Axiell GUID in `001` (for example
    `AxC-instance-<guid>`), and that hrid was how the sync found its own records.
    Instead (ids implemented in mapping 3.0.0, `formerIds` and marker in 3.1.0, item `formerIds` dropped in 3.2.0, holdings `formerIds` dropped and the marker moved to the instance status in 3.5.0):

    | | Instance | Holdings | Item |
    | --- | --- | --- | --- |
    | hrid | Generated by FOLIO; the sync sends none | Same | Same |
    | `id` (the lookup key) | the GUID itself | `uuid5(<guid>, "holdings")` | `uuid5(<guid>, "item")` |
    | GUID label | none needed for lookup, since the `id` is the GUID; an `(AxC)` System control number is planned (platform#6771) | none: staff reach the GUID through the instance, whose id it is | none: the FOLIO transformer reads an item's `formerIds` as its Sierra item number, and the item id is recomputable from the GUID |
    | Synced marker | `Synched by Axiell` instance status | none: reached through the instance | none: reached through the instance |

    The `id` is recomputed from the GUID on every run, so nothing is stored and
    deletion facts, which carry only the GUID, resolve the same way.
    The names `holdings` and `item` must never change after the first run.
    The marker is for staff, not lookup, so an edit in FOLIO does not break the
    sync.

    The instance needs no GUID label for lookup, because its `id` is the
    GUID, so the planned "Axiell GUID" identifier type is dropped. It still
    takes the GUID as an `(AxC)` System control number under change 3 of
    wellcomecollection/platform#6771, so the harvested MARC carries the 035
    `(AxC)` that marks it as Axiell-mastered.

    `identifiers` and the item's `statisticalCodeIds` are merged on update,
    keeping entries the sync does not own: on `identifiers` only the
    `Local identifier` is replaced, and on `statisticalCodeIds` only the access
    code. The sync sends no `formerIds`, so those are left as FOLIO holds them.

    The archival reference stays as the instance's `Local identifier` (for
    example `WMS/Amer.147`, from `035$a(AltRefNo)`). Prod holds no synced
    records, so only the dev sandbox needs clearing before the switch. Detail
    and evidence: [axiell-folio-record-identity.md](axiell-folio-record-identity.md);
    work: wellcomecollection/platform#6805.

### Deletion

11. **When a Collect record is deleted, should its FOLIO records be suppressed
    or hard deleted?** Decided: hard deleted. Agreed with Collection
    Information and conveyed in the weekly sync meeting, recorded on
    wellcomecollection/platform#6833 (2026-10-28) in answer to #6664.
    Implemented 2026-11-03: `HARD_DELETE` is `true` in Terraform, and the sync
    now acts on Collections tombstones as well as superseded GUIDs.

    | | What happens | Reversible |
    | --- | --- | --- |
    | **Suppress** (default) | `discoverySuppress` set on the instance, holdings and item; `staffSuppress` on the instance as well, since it is the only entity FOLIO gives that field | Yes. The records stay, out of discovery, and can be unsuppressed |
    | **Hard delete** | the item, then the holdings, then the instance are DELETEd | **No** |

    The code default for `HARD_DELETE` is still `false`; Terraform sets it to
    `true`, and the per-run `hard_delete` flag on the event overrides both.

    Before the decision, only the superseded-GUID path reached FOLIO. A record
    whose collectId was not reused arrived as an OAI-PMH tombstone, which the
    sync logged and ignored, following RFC 090's description of the loader's
    `deleted` flag as unreliable. On inspection that unreliability is a missed
    delete rather than a false one: the adapter writes a tombstone only when the
    OAI-PMH server reports `status="deleted"` (an id-mode run never writes one,
    and the missing-from-snapshot marking is EBSCO's alone), but a reused
    collectId never produces one, which is why the reconciler exists. The one
    false-positive case found is a GUID another record also holds, so the sync
    checks the reconciler store and skips such a tombstone.

    Points worth weighing. Suppression keeps an audit trail but can leave
    deleted records visible to cataloguers. Hard delete avoids orphaning parent
    records by deleting child records first, but it is irreversible; if a delete
    is issued in error, the sync would only recreate the records if the AxC
    record reappeared and passed the harvest gate again.

    Checked on the dev sandbox on 2026-11-03: FOLIO does not refuse the delete
    when the item has an open loan (written to loan-storage, as the sandbox
    service point has no calendar for a check-out) or an open Page request. All
    three records are deleted and the loan or request stays open, pointing at an
    item that no longer exists. The sync does not check for either. Not yet
    checked against the production tenant. A record restored in
    Collections after its FOLIO records were deleted is recreated under the same
    ids by the next sync, with new HRIDs and none of the FOLIO-side history.

    This is different from question 9: there, unticking Harvest means nothing
    reaches FOLIO at all; here, Collect sends an actual deletion and the sync
    acts on it.

### Statistical code

12. Confirm the statistical code mapping in section 5, in particular:
    `By approval` and `PERMISSIONREQUIRED` to `DONOR PERMISSION`; `CLOSED` and
    `SAFEGUARDED` in `506$f` taking precedence over `540$a`; and no code for
    `Missing`, `Deaccessioned` or an absent `540$a`. Should digital audiovisual
    material with `Online request` get a non-requestable code instead, to match
    its material type?
13. **Settled in mapping version 3.6.0: yes.** Should `SAFEGUARDED` and
    `PERMISSIONREQUIRED` items be `Available`, as
    migrated items with those restrictions are, rather than `Restricted`?

---

## Before implementing

**On the FOLIO tenant**, none of these verified: that `Restricted` and
`Withdrawn` are accepted by this FOLIO version; what loan policy the circulation
rules attach to `Can circulate` and whether `Unavailable` genuinely prevents a
loan; that the three `AxC *` location names resolve on prod; and that the
`CLOSED` and `DONOR PERMISSION` statistical codes exist on prod (neither did on
2026-10-01). Both hold on the test tenant (2026-11-23).

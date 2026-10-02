# FOLIO data profile

Step 3 of [folio_access_conditions_plan.md](folio_access_conditions_plan.md), for platform#6589. It profiles the FOLIO fields an access rule could read and crosswalks each migrated item to what readers are told today. The tables come from [folio_access_conditions.ipynb](folio_access_conditions.ipynb).

The data is a read-only pull of the production tenant (`api-wellcome.folio.ebsco.com`) taken on 2026-10-01: 478,390 items, 592,865 holdings and the reference data. The baseline is the served index `works-indexed-2026-07-03`, scrolled the same day, as described in [current_access_rules.md](current_access_rules.md).

The third input is Collection Information's draft mapping, "Access States - FOLIO mapping draft" (`QA_Wellcome_AccessStatusMapping.xlsx`, received 2026-10-01). It sets out what each Sierra access state is meant to become in FOLIO, which is not always what the tenant holds today.

The two sources are kept apart throughout. Statements about "the tenant" or "today's data" are observed in the pull. Statements attributed to "the Collection Information mapping" are its intended design, and are not necessarily reflected in the data. The comparison between them is in [its own section](#comparison-with-the-collection-information-mapping), and targets that can't be reached with the data as it currently sits in the tenant are marked as pending further data migration.

## What is in the tenant

| Origin | Items |
|---|---|
| Migrated from Sierra (an `i…` number in `formerIds`) | 478,387 |
| Created in FOLIO since migration (three items paged at the Library Enquiry Desk) | 3 |
| Written by the Axiell to FOLIO sync | 0 |

There are no Axiell-synced items in production. The Axiell campuses and libraries exist ("Euston Road (Axiell)", "Constantine London West (Axiell)", "Axiell sync"), but nothing sits in them, and no item or holdings record has an `AxC-` hrid. The Axiell segment of the plan can't be profiled from production yet.

## Archive items are awaiting the Axiell to FOLIO sync

Of the 737,366 Sierra items in the served index, 469,800 have a FOLIO item and 267,566 don't.

| Missing from FOLIO | Items |
|---|---|
| Sierra items on Axiell works (the CALM harvest items) | 178,747 |
| Sierra items on Sierra works | 88,750 |
| Sierra items on TEI works | 69 |

All but three of the 178,750 CALM harvest items that stand in for archive items on Axiell works are absent from FOLIO. This is expected: archive items reach FOLIO through the Axiell to FOLIO sync, not the Sierra migration, and the sync hasn't written to the production tenant yet. Until it does, archive items have no FOLIO item, and the one-source-per-item overlap check in the plan can't be run. With the CALM harvest items absent from the migration, I'd expect it to find almost no duplicates.

The 88,750 missing items on Sierra works belong to 80,695 works, about one item per work, and 88,594 of them are in closed stores. They include the audiovisual, iconographic and offsite items found behind the Sierra fallback branch (see [current_access_rules.md](current_access_rules.md)). Which bibs the rest belong to hasn't been checked; that needs the FOLIO instance list or the Sierra bib data.

The gaps are concentrated in the branches that matter most for archives:

| Sierra branch | Served | In FOLIO |
|---|---|---|
| `closed-stores-online-request` | 514,772 | 292,816 |
| `unmapped-fallback` | 29,832 | 306 |
| `unavailable-assessment` | 6,704 | 0 |
| `closed-stores-restricted` | 5,071 | 20 |
| `closed` | 2,448 | 11 |
| `safeguarded` | 73 | 14 |
| `open-shelves-available`, `request-top-item`, `closed-stores-manual-request`, `by-appointment` | 170,219 | 170,153 |

So the restricted, closed, safeguarded and assessment outcomes are almost entirely archive outcomes, and the FOLIO data will only show them once the sync writes those items.

The other direction has 8,587 FOLIO items with no served item. 4,157 of them are suppressed. Most of the rest are 2,683 available offsite items, 866 available closed-stores items and 258 bound-with items. Those could be items the index drops for reasons unrelated to access, so they need a separate look.

## Which FOLIO fields carry access information

| Field | Values in the tenant | Carries access information? |
|---|---|---|
| Item status | Available 466,703; Missing 5,014; Intellectual item 2,524; Unavailable 2,074; Withdrawn 1,318; Unknown 641; Awaiting pickup 111; Paged, In transit, Checked out | yes: missing, withdrawn, unavailable, and live circulation states |
| Permanent loan type | Can circulate 475,528; Unavailable 2,862 | barely in today's data, where it only marks unavailable items. The Collection Information mapping makes it an access input, pending migration (see below) |
| Temporary loan type | set on 1 item | no |
| ILL policy (holdings) | not set on any holdings record | no |
| Holdings source | FOLIO on every record | no |
| Item and holdings discovery suppression | 4,170 items, 3,857 holdings | yes, for visibility |
| Statistical codes | ONLINE REQUEST, OPEN SHELVES, AVAILABLE, MANUAL REQUEST, BY APPOINTMENT, DIGITISATION, UNAVAILABLE, RESTRICTED, SAFEGUARDED, or none | yes, this is the Sierra OPAC message |
| Effective location | 198 locations whose codes are the Sierra location codes | yes, for open shelves, offsite, exhibition, bound with |
| Temporary location | set on 3 items | no |
| Public item notes ("Display note") | 7,041 items | yes, the access instructions |

The OPAC message survived the migration as a statistical code on each item, one code per Sierra value: `-` is AVAILABLE, `a` BY APPOINTMENT, `b` DIGITISATION, `f` ONLINE REQUEST, `n` MANUAL REQUEST, `o` OPEN SHELVES, `u` UNAVAILABLE, `c` RESTRICTED and `p` SAFEGUARDED.

The Sierra location codes also survived as FOLIO location codes. Items sit in six libraries: Closed stores, Open shelves, Offsite (DS), `bwith` ("bound in above"), `cwith` ("contained in above") and Laptops. An On Exhibition library with one location exists, but no item is in it, and the 24 migrated items Sierra treats as on exhibition sit in Open shelves. The location-based rules in the rules for requesting can be written against FOLIO locations for the items migrated so far: FOLIO kept the Sierra location codes, so 15 of the 81 codes those rules name exist in the tenant, covering all 112,596 migrated items in them, nearly all on open shelves. The other 66 codes (data protection, digitisation, film and audio, and others) have no FOLIO location yet. They belong to material that hasn't been migrated, such as archive items awaiting the Axiell sync and audiovisual items, so the granularity loss #6584 anticipated is small for what is in the tenant, and open for what is still to come.

## How the Sierra outcomes line up with FOLIO values

| Sierra branch | Items in FOLIO | FOLIO values the items share |
|---|---|---|
| `closed-stores-online-request` | 292,816 | status Available, statistical code ONLINE REQUEST, Closed stores (291,336) |
| `open-shelves-available` | 108,985 | status Available, OPEN SHELVES, Open shelves library (108,977) |
| `request-top-item` | 35,894 | `bwith` or `cwith` library (35,889); status Intellectual item for 2,170 of them |
| `closed-stores-manual-request` | 21,517 | MANUAL REQUEST, Closed stores (all) |
| `missing` | 4,210 | status Missing (4,182) |
| `by-appointment` | 3,757 | BY APPOINTMENT (3,753), in Closed stores or Offsite (DS) |
| `unavailable-digitisation` | 1,868 | DIGITISATION with status and loan type Unavailable (1,117); ONLINE REQUEST and Available (751) |
| `closed-stores-on-hold-or-in-use` | 269 | Available (236) or Awaiting pickup (32): the hold isn't in the static data |
| `open-shelves-in-use-or-on-loan` | 107 | Available, OPEN SHELVES: the loan isn't in the static data |
| `closed-stores-restricted` | 20 | RESTRICTED |
| `safeguarded` | 14 | SAFEGUARDED |
| `closed` | 11 | status and loan type Unavailable, UNAVAILABLE |
| `unmapped-fallback` | 306 | mixed; 131 have status Unknown |

Most branches map onto a single FOLIO tuple: status, statistical code and library. The exceptions are the ones that depend on live circulation (holds and loans), which the transform can't see and the items service (#6654) can, and `unavailable-digitisation`, where 751 items say ONLINE REQUEST and Available in FOLIO but are being digitised according to Sierra. Those 751 probably changed state since the migration snapshot, or the Sierra digitisation state was carried by a location code that FOLIO keeps. The Collection Information mapping notes the same drift for digitisation ("this could've changed between loads"). That needs checking before the table treats them either way.

## The restricted-in-Sierra, can-circulate-in-FOLIO case

Every by-appointment, restricted and safeguarded item in FOLIO has the loan type "Can circulate" and the status Available: 3,791 items. Only the 11 closed items have "Unavailable". The restriction is carried by the statistical code.

The Collection Information mapping shows that this is deliberate. By appointment, donor permission, restricted and safeguarded are meant to stay Available and "Can circulate" so staff can still request them in the tenant, and in the case of restricted so the public can request them online. A FOLIO status of Restricted would block requesting altogether. So the mismatch #6589 describes isn't a migration error: the restriction has moved from the item status to the statistical code, and a FOLIO rule built on status, statistical code and location reproduces the Sierra outcome for these items.

Loan type is part of the rule even though it carries little in the tenant today. In the tenant, 99.4% of items are "Can circulate" and the rest are "Unavailable", so loan type barely separates one access state from another. The Collection Information mapping makes it an access input: it is used with the statistical code to decide whether the request button shows and which circulation rules apply, and most non-requestable states get a "Can't circulate" loan type. That loan type doesn't exist in the tenant yet, so this part of the design is pending further data migration.

## Sierra inputs and where they went

| Sierra input | FOLIO carrier |
|---|---|
| OPAC message | statistical code |
| Item status (missing, withdrawn, unavailable) | item status |
| Status "As above" (bound in, contained in) | `bwith` and `cwith` libraries, some with status Intellectual item |
| Location code | effective location code, unchanged for **migrated locations** |
| Hold count, holdshelf, loan rule, due date | live circulation only (requests, loans), for #6654 |
| Display note | public "Display note" item notes |
| Item type | material type, not yet checked one to one |

## Comparison with the Collection Information mapping

For each access state, the Collection Information mapping gives a target FOLIO item status, statistical code and loan type, requesting rules for the public and for staff, whether a hardcoded statement is shown, and Collection Information's own Sierra and FOLIO counts.

### The counts agree

Collection Information's FOLIO counts come from the same load as this pull, and nearly all of them match exactly:

| Pairing | Spreadsheet | This pull |
|---|---|---|
| ONLINE REQUEST with status Available | 296,133 | 296,133 |
| OPEN SHELVES with status Available | 109,375 | 109,375 |
| MANUAL REQUEST with status Available | 21,966 | 21,966 |
| BY APPOINTMENT with status Available | 6,452 | 6,452 |
| RESTRICTED | 20 | 20 |
| SAFEGUARDED | 14 | 14 |
| DIGITISATION with status Unavailable | 2,049 | 2,049 |
| status Unavailable | 2,074 | 2,074 |
| status Missing | 5,014 | 5,014 |
| status Intellectual item | 2,524 | 2,524 |
| AVAILABLE with status Available, listed as an incorrect mapping | 32,720 | 32,720 |
| OPEN SHELVES with status Unknown | 35 | 36 |
| OPEN SHELVES with status Missing | 1,971 | 1,864 |
| OPEN SHELVES with status Withdrawn | 144 | 3 |

The last two rows don't match and I haven't found why.

### The requesting rules agree with the Sierra outcomes

| Access state | Spreadsheet rule | Sierra branch today |
|---|---|---|
| Online request | public and staff can request | `closed-stores-online-request`: online request, open |
| Open shelves | no one requests | `open-shelves-available`: open shelves |
| Manual request | no one requests in FOLIO, hardcoded statement | `closed-stores-manual-request`: manual request, fixed note |
| By appointment | staff only | `by-appointment`: manual request, by appointment |
| Donor permission | staff only | `donor-permission`: manual request, permission required (no items in either system) |
| Restricted | public and staff | `closed-stores-restricted`: online request, restricted |
| Safeguarded | staff only | `safeguarded`: not requestable, safeguarded |
| Unknown (data issues) | no one, hardcoded statement | `unmapped-fallback` for most such items |
| Unavailable, closed, missing, withdrawn, on search | no one; hardcoded statement for missing and withdrawn | the unavailable, closed, missing and withdrawn branches |
| Intellectual item (bound with, contained in) | no one | `request-top-item` |

The spreadsheet adds something the Sierra rule never expressed: a separate staff answer. By appointment, donor permission and safeguarded items can't be requested by the public, but staff can request them in the tenant. The access condition only describes the public view, so the staff side belongs to FOLIO's circulation rules, not to the decision table.

Two states in the spreadsheet don't line up cleanly. Open shelves is grouped under "Open/Open With advisory", but the Sierra rule gives open-shelves items no access status, and nothing produces open with advisory today. Digitisation has no requesting rule in the spreadsheet.

### Targets pending further data migration

These targets can't be reached with the data as it currently sits in the tenant. A decision table can be written against the targets, but it will only produce the intended outcome for these items once the data is migrated or corrected.

| State | Spreadsheet target | In the tenant now |
|---|---|---|
| Manual request, missing, withdrawn, unknown, unavailable, closed, on search, intellectual items | loan type "Can't circulate" | no "Can't circulate" loan type exists; these items are "Can circulate", apart from 2,862 items with "Unavailable" (the digitisation items and the withdrawn items with an UNAVAILABLE code) |
| Digitisation | status "In process (non-requestable)", or a temporary location, still to be tested | status Unavailable and loan type Unavailable on 2,049 items; no temporary locations in use |
| Bound with ("As above") | status Intellectual item | 34,032 `bwith` items are Available, most with the AVAILABLE code the spreadsheet calls incorrect; only the `cwith` items (2,516) have Intellectual item |
| Data issues | status Unknown with a "Data issues" code | 641 items have status Unknown but keep their original code. The spreadsheet says no data issues code exists, but `DATA ISSUES` is in the tenant's statistical codes, unused |
| Closed | a "Closed" statistical code with status Unavailable | `CLOSED` exists with no items; the 11 closed items carry UNAVAILABLE and can't be told apart from unavailable items |
| On search | status Declared lost with an "On search" code | no item has either; the 69 Sierra items on search haven't been traced |
| Withdrawn and unavailable | no statistical code needed where the status is enough | 716 withdrawn items still carry UNAVAILABLE |

Two consequences follow for the decision table. Loan type stays an input even though it carries almost nothing today, and the outcomes for bound-with, closed, data issues and digitisation items depend on migration work that hasn't happened yet. The coverage check in step 5 should report results against today's data and, separately, against the spreadsheet's target, so the migration team can see which rows only work once their changes are in.

### Outside the spreadsheet's scope

The spreadsheet doesn't cover location (by appointment items sit in both closed stores and offsite; exhibition has no FOLIO carrier), live holds and loans, the wording of the hardcoded statements, or Axiell-synced items.

## Gaps in this profile

- Instance-level suppression isn't included; it needs the instance records or the bib store's 999 `$t`.
- The 69 Sierra items on search, which the spreadsheet maps to Declared lost, don't appear under any status in the tenant.
- Two of the spreadsheet's open-shelves counts (Missing and Withdrawn) differ from this pull.
- The 88,750 missing items on Sierra works and the 8,587 FOLIO items with no served item are counted, not explained.
- Nothing about Axiell-synced items can be profiled until the mappings are settled 
- 25,667 migrated items carry administrative notes. Most start with a numeric location path such as `215 B11 MR …`, which looks like an Axiell current location, on ephemera and other items. They don't affect access, but that's worth knowing alongside #6731, which writes the same field.

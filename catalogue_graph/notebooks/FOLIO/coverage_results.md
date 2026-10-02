# Coverage check results

Step 5 of [folio_access_conditions_plan.md](folio_access_conditions_plan.md), for platform#6589. It runs the draft decision table ([access_rules/decision_table.md](access_rules/decision_table.md)) over every item in the production tenant, as pulled on 2026-10-01, and compares each outcome with what readers are told today in the served index `works-indexed-2026-07-03`. The tables come from [folio_access_coverage.ipynb](folio_access_coverage.ipynb).

The check runs twice. The **today** run uses the tenant as it sits. The **target** run applies the changes Collection Information's mapping (`QA_Wellcome_AccessStatusMapping.xlsx`) intends but the tenant doesn't hold yet: bound-with items get the status Intellectual item, digitisation items get In process (non-requestable), the 11 closed items get the CLOSED code, and non-requestable states get the loan type "Can't circulate".

## Scope

| | Items |
|---|---|
| Items in the tenant | 478,390 |
| Suppressed, left to item filtering (#6608) | 4,170 |
| Evaluated | 474,220 |
| Evaluated and also in the served index | 469,787 |

Every evaluated item has exactly one statistical code or none, so the code input is never ambiguous.

## Agreement with what readers are told today

The table gives the same method and status as Sierra for **99.41%** of the 469,787 items found in both systems. Each disagreement is put in the first category that fits, in this order of precedence: intended, pending, live, decision, drift.

| Category | Today | Target | What it means |
|---|---|---|---|
| agrees | 466,993 | 467,004 | same method and status |
| drift | 2,028 | 2,028 | the migrated FOLIO data disagrees with Sierra's current state |
| live | 423 | 423 | a hold or loan that only the items service can see |
| decision | 324 | 325 | a policy difference for Collection Information |
| pending | 12 | 1 | depends on data still to be migrated |
| intended | 3 | 3 | a deliberate deviation from Sierra |
| unexplained | 4 | 3 | none of the above |

### Drift

The table maps the FOLIO data faithfully, but FOLIO holds the migration snapshot while the served index follows live Sierra, and the two have moved apart for about 2,000 items. The largest groups:

| FOLIO says | Sierra says now | Items |
|---|---|---|
| digitisation | online request | 926 |
| online request | digitisation | 751 |
| manual request | online request | 190 |
| no OPAC message (AVAILABLE) | online request | about 50 |
| online request or open shelves | missing | 24 |

These aren't rule problems. They resolve on their own once FOLIO is the system of record, or whenever the migration data is refreshed. The digitisation groups are the same drift the Collection Information mapping already notes ("this could've changed between loads").

### Live

228 items on hold and 105 open-shelves items on loan in Sierra show as available in FOLIO's static data. Another 80 items are the other way round: FOLIO's status (mostly Awaiting pickup) says they're in circulation, while Sierra shows them as available. This is what the items service (#6654) exists for. The transform can only see circulation through the item status, and the `open-request` row, live only, covers holds.

### Decision

324 contained-in ephemera items (`cwith`, status Intellectual item) can be requested online in Sierra today, and the table makes them not requestable, following Collection Information's mapping for contained-in items. Whether that's intended is a question for Collection Information, listed in the decision table's open questions.

### Pending

Today, 11 of the 12 are the closed items: they carry UNAVAILABLE in the tenant, so they show as temporarily unavailable instead of closed. In the target run they get the CLOSED code and agree, which leaves 1.

### Intended

The `by-appointment` deviation affects only 3 items in the tenant. Almost all of the roughly 25,700 items it corrects in the served index are archives awaiting the Axiell sync, or audiovisual items not yet migrated.

### Unexplained

4 items today, 3 in the target run. They are small enough to check one by one with Collection Information, and they don't point to a missing row.

## Rows

| Row | Today | Target |
|---|---|---|
| `online-request` | 292,949 | 292,949 |
| `open-shelves` | 109,202 | 109,202 |
| `bound-with-interim` | 33,980 | 0 |
| `bound-with` | 2,523 | 36,503 |
| `manual-request` | 21,839 | 21,839 |
| `by-appointment` | 6,447 | 6,447 |
| `missing` | 4,508 | 4,508 |
| `digitisation` | 2,049 | 2,049 |
| `data-issues` | 282 | 282 |
| `fallback` | 222 | 222 |
| `in-circulation-closed-stores` | 113 | 113 |
| `on-exhibition-material` | 26 | 26 |
| `unavailable` | 22 | 11 |
| `restricted` | 20 | 20 |
| `safeguarded` | 14 | 14 |
| `closed` | 0 | 11 |
| `offsite-manual-request` | 7 | 7 |
| `not-circulating` | 6 | 6 |
| `computer-media` | 5 | 5 |
| `withdrawn` | 3 | 3 |
| `in-circulation-open-shelves` | 3 | 3 |

In the target run `bound-with` absorbs `bound-with-interim`, as intended: once bound-with items carry the status Intellectual item, the interim row has nothing left to catch.

Seven rows reach no item today, and each one has a reason:

| Row | Why no item reaches it |
|---|---|
| `closed` | pending migration; reaches the 11 closed items in the target run |
| `on-search` | pending migration; no item has the status Declared lost |
| `digitisation-status` | pending migration; in the target run `digitisation` still catches the same items first by their code |
| `on-exhibition` | pending migration; the "Exhibitions" location is empty |
| `open-request` | live only |
| `donor-permission` | no item has the code in either system; kept as a policy guard |
| `audiovisual-non-requestable` | no audiovisual items migrated yet |

## Fallback

222 items reach the fallback in both runs. 132 of them are also in the served index, and 78 of those get the same fallback in Sierra today. Most of the others are drift: items with no OPAC message (AVAILABLE) in FOLIO that Sierra now lets readers request online. The rest are 8 equipment items in the Laptops library, which Sierra doesn't show, and new or unmatched items that aren't in the served index.

## Statuses and methods

| Status | Produced by |
|---|---|
| open | `online-request` |
| restricted | `restricted` |
| by appointment | `by-appointment` |
| safeguarded | `safeguarded` |
| temporarily unavailable | `digitisation`, `unavailable`, the circulation rows |
| unavailable | `missing`, `withdrawn` |
| closed | `closed`, in the target run only |
| permission required | `donor-permission`, which no item reaches |
| open with advisory | ruled out |
| licensed resources | ruled out |

Every method except view online is produced, and view online is ruled out.

## Location codes still matter

Seven location codes that Sierra's rules for requesting reserve for manual requests (`somet`, `somsy` and five others) exist in the tenant, and 7 items sit in them with the code ONLINE REQUEST. Without a location-code input, the table would offer those items online requesting, which Sierra doesn't. The `offsite-manual-request` row covers them, which is why `location_code` is an input.

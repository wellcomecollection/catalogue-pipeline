# PhysicalLocation parity: FOLIO against the catalogue

For every FOLIO item that is also shown in the catalogue, this check builds the item's `PhysicalLocation` from FOLIO data alone, using the rules found in [location_codes.md](location_codes.md) and the [decision table](access_rules/decision_table.md), and compares it with the `PhysicalLocation` readers see today. It covers the item's location and access condition only, which is what platform#6584 and platform#6589 need.

The data was captured on 2026-10-09, and [folio_physical_location_parity.ipynb](folio_physical_location_parity.ipynb) reproduces the check:
- **FOLIO:** a read-only pull of the production tenant's items (478,406), holdings, bound-with parts and reference data.
- **Catalogue:** each Sierra item's `PhysicalLocation` from the served works index, `works-indexed-2026-09-30`. This is a newer index than the one the earlier comparisons used.

Items are joined on the Sierra item number in FOLIO's `formerIds`. 474,234 FOLIO items aren't suppressed, and 470,391 of them are in the catalogue today.

## How the location is built

| Field | Rule |
|---|---|
| Location type | From the FOLIO library: Closed stores and Offsite (DS) give closed stores, Open shelves gives open shelves, On Exhibition gives on exhibition. The `migration` placeholder gives none. |
| Bound in above, contained in above | Items in the `bwith` and `cwith` libraries take the one type and label shared by the related items, as Sierra does per bib. The related items are the instance's other items and the items its bound-with parts point at (see below). If they don't agree, the item has no location. |
| Label | "Closed stores" for closed stores, otherwise the FOLIO location name. |
| Shelfmark | The item's effective call number. |
| Access condition | The decision table's evaluator, on the data as it sits in the tenant today. |

An item with no location type gets no `PhysicalLocation`, as in Sierra.

### Bound-with items need the bound-with parts

FOLIO doesn't keep a bound-with volume's items on one record the way Sierra keeps them on one bib. When a Sierra bib had the physical volume and a "bound in above" item, the migration moved the physical item to the instance of the book it's bound into, and left only the `bwith` item on the bound-in book's instance. The two are linked through a second holdings record on the bound-in instance and a bound-with part (`/inventory-storage/bound-with-parts`, 137,931 in the tenant) that points that holdings record at the physical item.

For example, Sierra bib `b1523891x` holds `i14224793` (`semst`, `EPB/MST/262`) and the bound-in `i1454295x` (`bwith`, `EPB/MST/262.5`). In FOLIO, `i14224793` sits on the instance for `b15055607`, and `b1523891x`'s instance has the `bwith` item plus a holdings record whose bound-with part points at `i14224793`.

Looking only at items on the same instance finds no related item for about 30,600 bound-with items, so they'd lose their location. Following the bound-with parts finds one for all but about 280. With it, 98.96% of the 36,559 bound-with and contained-in items get the same location as today, and most of the rest are the contained-in ephemera below.

## Results

**99.12% of items get exactly the same `PhysicalLocation`** (type, label, shelfmark, access method, status and note).

| Field | Same as today | Different |
|---|---|---|
| Has a location at all | 99.91% | 420 |
| Location type | 99.91% | 422 |
| Label | 99.88% | 563 |
| Shelfmark | 99.73% | 1,254 |
| Access method | 99.31% | 3,243 |
| Access status | 99.33% | 3,132 |
| Access note | 99.29% | 3,363 |
| Location type, label and shelfmark together | 99.65% | 1,662 |
| Everything | 99.12% | 4,129 |

### Why the rest differ

Each item is counted once, under the first cause that fits.

| Cause | Items | What it is |
|---|---|---|
| Digitisation and hold drift | 2,066 | FOLIO and Sierra disagree on whether the item is at digitisation or on hold. The same drift as in [coverage_results.md](coverage_results.md): FOLIO holds the migration snapshot, Sierra the current state. |
| Shelfmark | 605 | 334 differ, 261 of them the renumbered conference volumes (`sgmc2`, `M32255` against `18459`). 271 have a FOLIO call number but no shelfmark in the catalogue, because Sierra reads only 949 `$a` with field tag `c`. |
| Location code `none` | 327 | Sierra's "none" location gives no location type, so the catalogue shows no location. FOLIO's `none` location sits in the Closed stores library, so the library rule makes it closed stores. The mapping needs `none` to give no location. |
| Contained-in ephemera | 324 | The `cwith` ephemera will be inventoried and its location code updated. Until then, not requestable in FOLIO, requestable in Sierra, with a different shelfmark. |
| Circulation | 225 | Holds and loans that only the items service (#6654) can see. |
| Manual against online request | 190 | Drift: MANUAL REQUEST in FOLIO, online request in Sierra now. |
| Label rename | 140 | "Student Coll (Med Lit)" became "Student Coll. (Med Lit)" (139 items), and one item in "Medical Collection" sits in "History of Medicine". |
| Other access drift | 131 | The item's statistical code in FOLIO disagrees with Sierra's current OPAC message, in small groups. Not looked at one by one. |
| Exhibition and the `migration` placeholder | 61 | Items on exhibition in Sierra that sit in `migration` or `bwith` in FOLIO. Pending the exhibition decision. |
| Missing and unknown status drift | 31 | Missing or Unknown on one side only. |
| Bound-with fallback differs | 23 | The related items disagree, or there aren't any, on one side only. |
| Note only | 6 | Two notes doubled because the display note repeats the row's note, one missing full stop, and three items where Sierra says "Please request top item" and FOLIO gives the fallback note. |

Apart from the `none` rule, none of these points to a missing location rule. The biggest groups are data drift and live circulation, which resolve once FOLIO is the system of record or the items service is in place.


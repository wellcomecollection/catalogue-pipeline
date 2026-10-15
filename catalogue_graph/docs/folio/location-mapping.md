# FOLIO location mapping (platform#6584)

How the transform turns a FOLIO item into the catalogue's `PhysicalLocation` location type, label and shelfmark. The evidence is in [location_codes.md](https://github.com/wellcomecollection/catalogue-pipeline/blob/963a528f5f60e368a58988fa6a8419641a57e6bf/catalogue_graph/notebooks/FOLIO/location_codes.md) and [physical_location_parity.md](https://github.com/wellcomecollection/catalogue-pipeline/blob/963a528f5f60e368a58988fa6a8419641a57e6bf/catalogue_graph/notebooks/FOLIO/physical_location_parity.md), kept with catalogue-pipeline#3776. Location reference data is from the production tenant pull of 2026-10-09: 199 locations, all active, in 12 libraries.

## Which location

The item's effective location, which `items-and-holdings` returns as `items[].location.location`. FOLIO derives it from the item's temporary location, then its permanent location, then its holdings record's location. Migrated items have neither of their own, so today it's always the holdings location. A temporary location set for an exhibition or for digitisation will come through it without any change here.

## Location type

The type comes from the location's library, with a short list of exceptions by location code. The exceptions come first.

### Rule

| Library | Location type | Locations | Items |
|---|---|---|---|
| Closed stores | closed stores | 169 | 324,270 |
| Offsite (DS) | closed stores | 6 | 5,699 |
| Open shelves | open shelves | 14 | 111,774 |
| On Exhibition | on exhibition | 1 | 0 |

Against the catalogue today, this gives the same type for every item at 176 of the 180 locations that have items in both systems, leaving out `bwith` and `cwith`. Of the other four, `none` and `migration` are exceptions below, and `somja` and `wgpvm` differ by one item each: one on exhibition in Sierra, and one Sierra shows as closed stores.

Typing by library rather than by a per-code table means a location Collection Information adds to one of these libraries is typed without a change here. The cost is that a location put in the wrong library gets the wrong type without any warning. I think that's the better trade, since locations are added more often than they're misfiled.

### Exceptions

| Code | Name | Library | Mapping | Why |
|---|---|---|---|---|
| `none` | No location | Closed stores | no location | Sierra's "none" location gives no type, and the catalogue shows no location for these items today. FOLIO files it under Closed stores only because every location needs a library. 1,110 items, 781 of them suppressed from discovery. Of the 329 shown, 327 have no location in the catalogue today; the other 2 are Missing in FOLIO and have moved to closed stores in Sierra since the migration snapshot. |
| `migration` | migration | Open shelves | no location | The migration's placeholder. It holds 43 items, most of them on exhibition in Sierra, and 29,188 holdings records made from Sierra check-in records. Filed under Open shelves, so the rule would make it open shelves. |
| `bwith`, `cwith` | bound in above, contained in above | their own libraries | the host item's type and label | The "bound in above" fallback. `items-and-holdings` returns the host item on the same instance, under a "Boundwiths" holdings record, so the transform takes the type and label of the instance's items outside `bwith` and `cwith`, when they all agree, as Sierra does per bib. Otherwise no location. 36,642 items. |
| `elro` | Online | Closed stores | no location | Holdings for online resources, 5,035 of them and no items. They aren't physical, so belong to platform#6612, not here. |

### Pending a decision

These locations hold almost nothing today, so they don't affect the migrated items, but each needs a decision before something lands in it.

| Code | Name | Library | Items | Proposal |
|---|---|---|---|---|
| `DIGITISATION` | Digitisation | Digitisation | 0 | Closed stores. The item is in the building and the access condition says it's at digitisation. Depends on how Collection Information settles the digitisation question in platform#6589. |
| `Exhibitions` | On exhibition | On Exhibition | 0 | On exhibition, through the rule. Listed because it's the temporary location Collection Information may use for exhibitions, which is still open in platform#6589. |
| `axce`, `axcc` | AxC Euston Road, AxC Constantine London West | Axiell sync (EUA), Axiell sync | 11 | Closed stores. These are the Axiell to FOLIO sync's locations. Their finer location comes in the "Axiell Current Location" item note. Depends on the sync's mapping. |
| `axcd` | AxC Deepstore | Offsite (DS) | 0 | Closed stores, through the rule. Same as above. |
| `hicon` | Offsite Closed stores CLW | Axiell sync | 0 | Closed stores, as Sierra does. Iconographic material moves to Axiell Collections, so this may never hold items. |
| `ILL` | Interlibrary Loan | Interlibrary Loan | 0 | No location. Items borrowed from other libraries aren't part of the collection. |
| `ACQL` | Acquisitions Temporary Location | Acquisitions | 2 | No location. Items still being acquired. On-order items belong to platform#6592. |
| `enhal` | Enquiry Hall | Laptops | 8 | No location. Laptops for readers, not collection items. |
| `LED` | Library Enquiries Desk | Open shelves | 3 | Open shelves, through the rule, unless these are staff copies. |

### Unmapped location codes

An item whose location's library isn't in the rule and whose code isn't an exception gets no location, and the transform logs a warning with the item id, the location code and the library. That's what Sierra does with a location name it can't type. The item is still shown, without a location.

## Label

"Closed stores" for closed stores, otherwise the location's name. `items-and-holdings` gives the location's discovery display name as `location.location.name`, falling back to its name, so the label uses that. On all 199 locations the two are the same today, so the choice only matters if Collection Information starts setting display names.

For items the catalogue labels with their own location name, this matches today's label for 95.7%. The rest are the bound-with items, labelled through the fallback, and "Student Coll (Med Lit)", which FOLIO renamed "Student Coll. (Med Lit)" (139 items). I think the rename is a correction and should go through.

## Shelfmark

The item's effective call number, `items[].callNumber.callNumber`. FOLIO falls back from the item's own call number to the holdings record's, and the two never disagree on a migrated item. It matches today's shelfmark for 99.81% of the items that show one. The differences are the renumbered conference volumes (`sgmc2`), the contained-in ephemera still being clarified, and 182 journals in `seser`: 180 have title-abbreviation call numbers in FOLIO (`SCI /1`, `RUD /1 (2)`) where the catalogue shows `EPB/S/…` today, and 2 have no call number. 172 of the 182 are coded MANUAL REQUEST in FOLIO but requestable online in Sierra, as are 18 more `seser` journals whose call numbers match. Both fields differing on the same items suggests Sierra has re-catalogued them since the migration snapshot, but that needs confirming with Collection Information before go-live, since the FOLIO values are what readers would see.

Sierra hides the shelfmark for archives and manuscripts, and for iconographic items whose number doesn't match the bib. Neither kind is migrating to FOLIO, so neither rule is needed for migrated items. One thing is still open. 272 books and journals have a FOLIO call number but no shelfmark today. The migration took FOLIO's call number from the Sierra item's `callNumber` field: 166 match it exactly, and the other 106 are the same value with the volume moved to the item's `volume` field (Sierra `/STU Vol. 52 (2007)`, FOLIO `/STU`). They're ordinary shelfmarks, such as `WS200 1881G23s`, `EPB/P/51596` and `Pamphlets /BUC`. The catalogue doesn't show them today only because these items have no 949 `$a` with field tag `c`. I think they should be shown, but Collection Information should confirm, since it changes what readers see for these items. The Sierra values were checked on 2026-10-13 against the served index `works-indexed-2026-09-30`, which is why the count differs from the 269 in [location_codes.md](https://github.com/wellcomecollection/catalogue-pipeline/blob/963a528f5f60e368a58988fa6a8419641a57e6bf/catalogue_graph/notebooks/FOLIO/location_codes.md).

## Granularity

Nothing is lost for migrated items. FOLIO kept the Sierra codes, and 99.86% of items are in the same code as in Sierra. The codes FOLIO doesn't have hold archives, visual and audiovisual material, which go to Axiell Collections instead. The mapping keys on codes for its exceptions, not names, because several locations were renamed in the migration.

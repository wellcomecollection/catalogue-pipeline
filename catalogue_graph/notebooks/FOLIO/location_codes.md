# Location codes: Sierra against FOLIO

A comparison of the locations Sierra items sit in with the locations defined in FOLIO, to find gaps that matter for platform#6584 (location types) and platform#6589 (access conditions). Not every Sierra location is expected to migrate, so the aim is to show where items are and aren't, not to demand a one-to-one mapping.

The two sides were captured on 2026-10-08:
- **Sierra:** item counts per location code and name from the `sierra_items` index on the reporting cluster, which the Sierra adapter keeps up to date (latest `updatedDate` that day). Deleted and suppressed items are left out, which leaves 915,628 items.
- **FOLIO:** the tenant's location reference data, with item and holdings counts per effective location, from a read-only pull of the production tenant: 478,404 items, 199 locations.
- **Catalogue:** for the item-level comparison, each Sierra item's location type, label and shelfmark as readers see them today, from the served works index `works-indexed-2026-07-03`.

Locations are matched on code. For migrated locations, FOLIO kept the Sierra code. In Sierra every code has exactly one name.

## Summary

| | Sierra | FOLIO |
|---|---|---|
| Location codes | 205 with items | 199 defined |
| Codes in both | 185 | 185 |
| Codes only on this side | 20, holding 159,917 items | 14 |
| Items | 915,628 | 478,404 |

The difference in items is almost entirely these collections:

| Collection | Sierra items | FOLIO items |
|---|---|---|
| Archives (`scm…`, `sc#…`, `har…` codes) | 338,526 | 35 |
| Western Manuscripts (`swm…` codes) | 9,552 | 5,898 |
| Visual (`sicon`, `hicon`) | 79,445 | 0 |
| Moving image and sound (`mfvac`, `mfohc`) | 14,169 | 139 |
| Everything else, in codes FOLIO has | 473,460 | 472,172 |

So the library material has migrated almost completely. Most of the gap is archives, visual and audiovisual material, and none of it is meant to migrate from Sierra to FOLIO: it lands in Axiell Collections instead. Western Manuscripts are the exception: 62% of their items are in FOLIO and 38% aren't, and it isn't clear whether they're meant to migrate at all (see Findings). Visual and audiovisual material moves from Sierra to Axiell Collections later this year, before the March 2027 go-live (Collection Information, on PR #3736, 2026-10-07). Archive records are already catalogued in Axiell Collections, and the Sierra items counted here are the copies the CALM harvest created.

## Items in both Sierra and FOLIO

The migration is what platform#6584 maps, so the most useful comparison is per item: for each item in both systems, does FOLIO hold the same location data Sierra does? Items are joined on the Sierra item number, which FOLIO keeps in `formerIds`. 474,131 FOLIO items are also live in Sierra, and 469,799 of them are shown in the catalogue today.

### Location code

The item's effective location in FOLIO has the same code as its Sierra location for 99.86% of items. The 641 that differ are mostly two groups:

| Sierra location | FOLIO location | Items | What happened |
|---|---|---|---|
| `sgeph` Closed stores ephemera | `cwith` contained in above | 324 | A migration mapping error. These ephemera were taken out of oversize boxes and given their own shelfmarks (`EPH/791/18`), and each bib keeps a 773 recording the box it used to be in ("Previous box title: Oversize ephemera. EPH+32"). The migration read that historical link as current containment: in FOLIO the item is contained-in, with the status Intellectual item and the call number `EPH+32:18`, under a container holdings record built from the old, suppressed box item. In Sierra they are available and requestable; in FOLIO they wouldn't be. All 20 sampled items follow this pattern. |
| `sgmon` Closed stores mons. | `sgmc2` Closed stores Conferences 2 | 262 | Re-shelved and renumbered (`M32255` became `18459`) |
| `exres` On Exhibition, and a few others | `migration` placeholder | 32 | Placed in the placeholder location |

The rest are single items.

### Where FOLIO keeps the location

FOLIO holds the location on the holdings record, not the item. No migrated item has a permanent location of its own and none has a temporary location, so each item's effective location is its holdings record's location, for every item. For platform#6584's question of which of the effective, permanent and temporary locations is authoritative, today that's the holdings location, which FOLIO surfaces as the item's effective location. Temporary locations aren't used yet, but Collection Information intends to use them for exhibitions and digitisation, and the effective location picks those up when they are.

### Location type

Today's location types come from Sierra's location names. A type derived provisionally from the FOLIO library (Closed stores and Offsite as closed stores, Open shelves as open shelves) gives the same type for every item except two groups:

| Catalogue today | FOLIO location | Items |
|---|---|---|
| closed stores | `bwith` or `cwith` (bound in above, contained in above) | 35,846 |
| open shelves | `bwith` or `cwith` | 408 |
| on exhibition | `migration` placeholder | 55 |

Bound-with and contained-in items get their type in Sierra from the cross-item fallback: when an item's location is "bound in above" or "contained in above", it takes the one location type the other items on the bib share. FOLIO keeps these as the `bwith` and `cwith` locations, so "bound in above" does survive the migration, and the transform needs the same fallback, or another way to type these 36,254 items. That answers one of platform#6584's questions, against the earlier indication from harvested MARC that these values had gone.

### Label

Where the catalogue shows the source location's own name (every type except closed stores), the label matches the FOLIO location name for 95.7% of 115,692 items. The rest are the bound-with items, whose label comes from the fallback, and "Student Coll (Med Lit)", renamed "Student Coll. (Med Lit)" (140 items).

### Shelfmark

The catalogue's shelfmark comes from the Sierra item's 949 `$a` with field tag `c`, and is hidden for archives and manuscripts and for most iconographic items. Against that:

- FOLIO's effective call number equals the catalogue's shelfmark for 99.81% of the 467,837 items that show one.
- The item-level call number and the holdings call number are identical on every migrated item, so the two candidate sources in platform#6584 never disagree today.
- The 875 that differ are the 324 ephemera wrongly migrated as contained-in (`EPH/791/18` against `EPH+32:18`, see above), the renumbered conference volumes (`M32255` against `18459`), and 182 journals in `seser` whose FOLIO call numbers use title abbreviations (`PHI /9`) where Sierra shows `EPB/S/…`.
- 269 items show no shelfmark in the catalogue but have a FOLIO call number. Their call numbers come from the Sierra item's `callNumber` field and are ordinary shelfmarks; see the Shelfmark section of [location_mapping.md](location_mapping.md).

## Sierra locations with no FOLIO location

| Code | Sierra name | Items |
|---|---|---|
| `sicon` | Closed stores Visual | 78,082 |
| `sc#ac` | Unrequestable Arch. & MSS | 51,238 |
| `scmwf` | Closed stores A&MSS Well.Found. | 20,369 |
| `mfohc` | Closed stores Moving image and sound collections | 5,785 |
| `harcl` | Offsite Closed stores non-requestable Deepstore Archives | 3,596 |
| `exres` | On Exhibition | 280 |
| `sc#wf` | Unrequestable A&MSS WF | 175 |
| `swm#6`, `swm#3`, `swm#4`, `swm#m` | Unrequestable WMS 6, 3, 4, Amer. | 117 |
| `scmwa` | Closed stores A&MSS Well.Arch. | 79 |
| no code | | 164 |
| `zzzzz`, `admi`, `gblip`, `temp`, `sepbk`, `semsl`, `sepbn` | administrative or stray | 32 |

## Sierra locations in FOLIO, with few or none of their items

The FOLIO location exists, but most of its Sierra items aren't in it.

| Code | Name | Sierra items | FOLIO items |
|---|---|---|---|
| `scmac` | Closed stores Arch. & MSS | 253,465 | 35 |
| `harop` | Offsite requestable Deepstore Archives | 9,604 | 0 |
| `mfvac` | Closed stores Moving image and sound | 8,384 | 139 |
| `hicon` | Offsite Closed stores CLW | 1,363 | 0 |
| `swms1` to `swms7`, `swmam` | Closed stores WMS (Western Manuscripts) | 9,258 | 5,724 |
| `seser` | Closed stores EPB journals | 4,215 | 3,373 |

`hicon` sits under the "Axiell sync" library in FOLIO, the location the Axiell to FOLIO sync used to send Euston Road records to, before platform#6731 changed the mapping.

## FOLIO locations with no Sierra code

| Code | Name | Library | Items | Holdings |
|---|---|---|---|---|
| `migration` | migration | Open shelves | 43 | 29,188 |
| `elro` | Online | Closed stores | 0 | 5,035 |
| `sslor` | Closed stores Student Reprint | Closed stores | 100 | 23 |
| `axce`, `axcc`, `axcd` | AxC Euston Road, AxC Constantine London West, AxC Deepstore | Axiell sync, Offsite (DS) | 11 | 1 |
| `Exhibitions` | On exhibition | On Exhibition | 0 | 0 |
| `DIGITISATION`, `ILL`, `LED`, `ACQL` | Digitisation, Interlibrary Loan, Library Enquiries Desk, Acquisitions Temporary Location | their own libraries | 5 | 2 |
| `wslor`, `wsrer`, `sobte` | Student Coll. reprints, reprints (ref only), Closed stores P.B. Telugu | Open shelves, Closed stores | 1 | 1 |

The `AxC` locations belong to the Axiell to FOLIO sync. Their first handful of items has arrived. `elro` holds electronic holdings, which platform#6612 covers.

`sslor` and `sobte` do exist in Sierra, but only on suppressed items, which the Sierra counts here leave out. All 100 `sslor` items are suppressed in Sierra and in FOLIO. `sobte` holds one item, `i16640998`, which is suppressed in Sierra but not in FOLIO, so it would start showing in the catalogue once FOLIO is the source. That's a migration discrepancy for Collection Information to fix or confirm.

## Findings

**Most of the gap is archives, visual and audiovisual material, and it's expected.** None of it migrates from Sierra to FOLIO; it lands in Axiell Collections. Visual and audiovisual material, including `sicon` (Closed stores Visual, 78,082 items) and the moving image and sound codes, moves from Sierra to Axiell Collections before the March 2027 go-live. Archive records are catalogued in Axiell Collections already. Whether any of this material then reaches FOLIO, through the Axiell to FOLIO sync, depends on that sync's mapping, which is still pending.

**Western Manuscripts migrated only in part.** 5,898 of the 9,552 live Sierra items in the `swm…` codes are in FOLIO, about 62%. Counts are from Sierra on 2026-10-13 and the FOLIO pull of 2026-10-09:

| Code | Name | Sierra items | FOLIO items |
|---|---|---|---|
| `swms4` | Closed stores WMS 4 | 3,423 | 2,577 |
| `swms3` | Closed stores WMS 3 | 3,137 | 2,000 |
| `swms2` | Closed stores WMS 2 | 1,080 | 790 |
| `swms6` | Closed stores WMS 6 | 624 | 0 |
| `swms7` | Closed stores WMS 7 | 519 | 149 |
| `swmam` | Closed stores WMS Amer. | 304 | 90 |
| `swmlo` | Closed stores WMS MSL | 161 | 158 |
| `swm#6`, `swm#3`, `swm#4`, `swm#m` | Unrequestable WMS 6, 3, 4, Amer. | 117 | 0, no FOLIO location |
| `swms5` | Closed stores WMS 5 | 99 | 69 |
| `swms1` | Closed stores WMS 1 | 72 | 49 |
| `swmal` | Closed stores Batak mss | 16 | 16 |

Every code is short except `swmal` and nearly `swmlo`, and `swms6` and the unrequestable codes have nothing. Whether Western Manuscripts are meant to migrate to FOLIO isn't clear. Either way a partial migration looks more like a filter or a data issue than a decision, so it needs raising with Collection Information: if they're meant to be in FOLIO, 3,654 items are missing, and if they aren't, 5,898 are there by mistake. The four unrequestable codes are on Sierra's data protection list, and have no FOLIO location to land in. If they're meant to migrate, their restriction needs a home in FOLIO first.

**FOLIO's `none` location holds 1,110 items, against 391 in Sierra's, but the extra items are suppressed.** 781 of the 1,110 are suppressed from discovery in FOLIO, so they aren't shown at all. Of the other 329, 327 have no location in the catalogue today, and the remaining 2 are Missing in FOLIO while Sierra now shows them in closed stores, which is drift since the migration snapshot (checked 2026-10-13 against `works-indexed-2026-09-30`).

**Some codes hold more items in FOLIO than in Sierra:** `sgmc2` (234 in Sierra, 751 in FOLIO), `wslob` (66 against 326), `cwith` (2,192 against 2,517), `setra` (1,350 against 1,692) and a few more. Items have probably moved in Sierra since the migration snapshot, the same drift the access condition coverage check found.

**Names changed while codes stayed the same.** "Sanskrit mss" became "Indic mss", "Arch. & MSS" became "archives & manuscripts", and "Moving image and sound collections" became "Moving Image and sound". Sierra's location-type rule matches on location names, so a straight port would misclassify renamed locations. That's a reason for platform#6584 to map on codes.

**The `migration` placeholder holds 29,188 holdings records but only 43 items.** It's worth checking whether these are the extra holdings records migration created from Sierra check-in records, the FOLIO-47 risk in platform#6615.

**The exhibition location is empty.** Sierra's `exres` holds 280 items, and FOLIO's `Exhibitions` location none. The exhibit items that did migrate sit in the `migration` placeholder.

## Location codes in Sierra's rules for requesting

Sierra's rules for requesting name 80 distinct location codes. 58 of them have no live Sierra items: they're stale entries in the rules configuration, such as the film and audio contact codes, the `temp` codes and most of the manual request list. Of the 22 that do have items:

| Rule group | Codes with Sierra items | Sierra items | Codes in FOLIO |
|---|---|---|---|
| open shelves | 12 | 111,922 | 12 |
| data protection | 6 | 51,530 | 0 |
| unavailable (`harcl`) | 1 | 3,596 | 0 |
| offsite manual request | 2 | 29 | 2 |
| manual request | 2 | 9 | 1 |

The location-based rules that FOLIO can't express yet therefore come down to the data protection codes and `harcl`, plus one manual request item in `gblip` ("215 Information Point"). That item is a departmental copy of the BMJ (`i18699145`, status "Dept material") on a suppressed bib, so readers never see it and the location is effectively obsolete. The data protection codes cover both archives (`sc#…`, about 51,400 items) and Western Manuscripts (`swm#…`, 117 items). The archive codes, and `harcl`, matter once archives arrive through the Axiell to FOLIO sync, and how they apply then depends on its mapping, which is still pending. The Western Manuscripts codes matter only if those items migrate to FOLIO, which is part of the question above.

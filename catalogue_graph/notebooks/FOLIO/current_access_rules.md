# Current access condition rules

This is what readers are told today about whether they can request a physical item, and the rules that produce it, before any FOLIO mapping is proposed.

Volumes come from the served production index `works-indexed-2026-07-03` (named in `search-templates.json` on 2026-10-01), scrolled on 2026-10-01. Every physical item on a visible work was mapped back to the Sierra branch that produced it, using each branch's output signature (method, status and fixed note). Every item matched a branch. Counts are distinct items: 4,513 Sierra items appear on more than one work, mostly TEI manuscripts and bound-with volumes, and are counted once.

## What readers see today

| Item source | Physical items | Where the access condition comes from |
|---|---|---|
| Sierra items on Sierra and TEI works | 558,616 | Sierra rule |
| Sierra items on Axiell works | 178,750 | Sierra rule, the item having been merged onto the Axiell work |
| Axiell items | 441 | Axiell transformer |
| CALM items | 7,004 | CALM transformer, for the reindex-only CALM subset |
| Sierra on-order items | 3,452 | none |

Requesting for archives currently runs through Sierra. When an Axiell work has a matching Sierra item, the merged work shows the Sierra item and its access condition rather than the Axiell one, which is why only 441 Axiell items appear in the index. FOLIO therefore has to replace the Sierra rule for both the library's own items and the archive items that are requested through Sierra today.

## The Sierra rule

The rule lives in `common/source_model/src/main/scala/weco/catalogue/source_model/sierra/rules/`. `SierraItemAccess.scala` matches a tuple of five inputs against an ordered list of branches, and the first match wins.

| Input | Sierra source | Values used |
|---|---|---|
| hold count | `holdCount` on the item | 0, or more than 0 |
| item status | fixed field 88 | `-` available, `y` permission required, `m` missing, `r` unavailable, `h` closed, `g` safeguarded, `x` withdrawn, `!` on holdshelf |
| OPAC message | fixed field 108 | `f` online request, `n` manual request, `o` open shelves, `a` by appointment, `b` at digitisation, `q` donor permission, `u` unavailable, `c` restricted, `p` by approval |
| rules for requesting | `SierraRulesForRequesting.scala`, below | a requestable or not-requestable result with a message |
| location type | `SierraPhysicalLocationType.scala`, from the location name | closed stores, open shelves, on exhibition, or none |

Two more fields are read inside branches: a due date (fixed field 65) and MARC 999 `$a` on the item (exhibition text).

### Branches, in match order

| Branch | Matches when | Method | Status | Note | Items | On Axiell works |
|---|---|---|---|---|---|---|
| `closed-stores-online-request` | no holds, available, OPAC message online request, requestable, closed stores | online request | open | none | 514,772 | 142,281 |
| `open-shelves-available` | no holds, available, OPAC message open shelves, rules say open shelves, open shelves location, no due date | open shelves | none | none | 109,005 | 0 |
| `request-top-item` | rules say "request top item" (status `b` or `c`, which Sierra shows as "As above") | not requestable | none | "Please request top item." | 35,894 | 0 |
| `closed-stores-manual-request` | no holds, available, OPAC message manual request, rules say manual request, closed stores | manual request | none | the display note if it reads as a manual request note, otherwise a fixed "needs to be ordered manually" note | 21,553 | 0 |
| `closed` | status closed, OPAC message unavailable, rules say closed, closed stores or no location | not requestable | closed | none | 2,448 | 2,305 |
| `unavailable-assessment` | status unavailable, OPAC message unavailable, rules say unavailable | not requestable | temporarily unavailable | "undergoing internal assessment or conservation work" | 6,704 | 6,440 |
| `unavailable-digitisation` | status unavailable, OPAC message at digitisation, rules say unavailable | not requestable | temporarily unavailable | "being digitised" | 2,802 | 812 |
| `closed-stores-restricted` | no holds, available, OPAC message restricted, requestable, closed stores | online request | restricted | none | 5,071 | 4,937 |
| `by-appointment` | no holds, status permission required, OPAC message by appointment, closed stores | manual request | by appointment | none | 3,767 | 3 |
| `donor-permission` | no holds, status permission required, OPAC message donor permission, closed stores | manual request | permission required | none | 0 | 0 |
| `missing` | status missing | not requestable | unavailable | "This item is missing." | 4,563 | 148 |
| `withdrawn` | status withdrawn | not requestable | unavailable | "This item is withdrawn." | 1 | 0 |
| `safeguarded` | status safeguarded, OPAC message by approval | not requestable | safeguarded | none | 73 | 1 |
| `closed-stores-on-hold` | holds above 0, closed stores | not requestable | temporarily unavailable | "in use by another reader" | 511 for both | 141 for both |
| `closed-stores-in-use` | rules say in use (loan rule non-zero, or status on holdshelf), closed stores | not requestable | temporarily unavailable | "in use by another reader" | (with the row above) | |
| `open-shelves-in-use` | rules say in use, open shelves | open shelves | temporarily unavailable | "in use by another reader" | 107 for both | 0 |
| `open-shelves-on-loan` | open shelves with a due date | open shelves | temporarily unavailable | "in use by another reader" | (with the row above) | |
| `on-exhibition` | on exhibition location with a MARC 999 | not requestable | none | the 999 `$a` text | 217 | 107 |
| `on-loan-elsewhere` | any item with a due date not caught above | not requestable | temporarily unavailable | "in use by another reader" | 46 | 0 |
| `unmapped-fallback` | nothing above matched; logs a warning | not requestable | none | "cannot be requested online, please contact library@" | 29,832 | 21,575 |

The output signatures can't separate the on-hold branch from the in-use branch, or the two open-shelves branches, so those pairs share a count.

Three branches carry 87% of items. Four carry fewer than 100 items each, and `donor-permission` carries none. The fallback is the fourth largest outcome, at 29,832 items, and readers see its generic "cannot be requested online" message on all of them today.

### Why items reach the fallback

The index doesn't hold the Sierra inputs, so a stratified random sample of 600 fallback items (300 on Axiell works, 300 not) was looked up in the Sierra adapter's store (DynamoDB `vhs-sierra-sierra-adapter-20200604` and its S3 objects, records last modified between 2025-02-21 and 2026-09-23). Rerunning the rule over their fixed fields reproduced the fallback for all 600.

| Cause | On Axiell works | On other works |
|---|---|---|
| Status Available with OPAC message By appointment | 284 | 193 |
| Item type 15, 17 or 18, which the rules for requesting give no public message | 0 | 78 |
| Status Available with OPAC message Unavailable | 6 | 9 |
| Data issues status (`j`) | 0 | 8 |
| Data protection location codes | 5 | 0 |
| Status Permission required with a message other than By appointment | 4 | 3 |
| Other | 1 | 9 |

Status Available with By appointment is the main cause. The rules for requesting turn it into "needs manual request", but the `by-appointment` branch only matches status Permission required, and `closed-stores-manual-request` only matches the Manual request message, so the combination falls through. Weighted by the two groups, it accounts for roughly 25,700 of the 29,832 fallback items (about 95% of those on Axiell works and 64% of the rest).

This is a long-standing gap in the Sierra rule. RFC 042's list of unhandled combinations (`docs/rfcs/042-requesting-model/unhandled.csv`, 2021-05-20) already has "Closed stores, Available, By appointment" at 3,359 items.

The items behind it differ by group. On Axiell works they are archive items in the location "Unrequestable Arch. & MSS" (code `sc#ac`, 275 of the 284). That code is also on the data protection list in the rules for requesting, but the By appointment check runs first, so readers get the generic fallback note instead of the data protection message. On other works they are mostly not archives: moving image and sound (`mfohc`, 104), iconographic and visual (`sicon`, 46) and the Constantine London West offsite store (`hicon`, 43).

Almost none of these items are in FOLIO: 6 of the 600 sampled. The archive items fit the migration leaving archives to the Axiell sync. The audiovisual, visual and offsite items are part of the 88,750 items on Sierra works that FOLIO doesn't hold (see [folio_data_profile.md](folio_data_profile.md)).

So it can't be told yet whether a FOLIO rule needs to handle this case at all. The two groups would come back by different routes. The archive items would return through the Axiell sync, which as it stands writes status Available and a loan type but no statistical code, so the Sierra combination couldn't reappear in the same form, and the open question is how the sync represents by-appointment and unrequestable archives. The audiovisual, iconographic and offsite items would only return if a later migration load includes them, and would then most likely arrive with the BY APPOINTMENT code, as the migrated by-appointment items do.

### Rules for requesting

`SierraRulesForRequesting.scala` is a port of the Sierra "rules for requesting" configuration, last checked against Sierra on 2024-03-20 according to its scaladoc. It runs in order and the first match wins.

| Condition | Result |
|---|---|
| status `m`, `s`, `x`, `r`, `z`, `v`, `h`, `g`, `j` | missing, on search, withdrawn, unavailable, no public message, at conservation, closed, safeguarded, unavailable (data issues) |
| status `b` or `c` | request top item |
| status `d`, `e`, `y` | on new books display, on exhibition, no public message |
| loan rule (fixed field 87) not 0, or status `!` | in use by another reader |
| OPAC message `n`, `a` or `p` | needs manual request |
| OPAC message `u` | unavailable |
| OPAC message `b` | at digitisation |
| location code in the Medical Film and Audio Library list | contact us |
| location code in a list of 15 codes (`dbiaa`, `dinad`, `gblip` and others) | needs manual request |
| location code `harcl` | unavailable |
| location code `isvid` or `iscdr` | contact us |
| location code in the 29 open shelves codes | on open shelves |
| item type 22 | on exhibition |
| item type 17, 18 or 15 | no public message |
| item type 14, or location code in 7 more codes (`ofvn1`, `somet` and others) | needs manual request |
| location code `sepep` | no public message |
| location code in the data protection list (`sc#ac`, `swm#m` and others) | unavailable, citing the Data Protection Act |
| location code `temp1` to `temp6` | at digitisation and temporarily unavailable |
| location code `rm001` or `rmdda` | no public message |
| anything else | requestable |

Several of these results have no branch of their own above (on search, at conservation, contact us, on new books display, and the no-public-message results apart from by appointment), so items producing them fall through to `on-loan-elsewhere` if they have a due date, or to `unmapped-fallback`.

`gblip` sits in both the manual request list and the open shelves list. The manual request list is checked first, so it wins.

### How the item note and the access condition note interact

Sierra has one free-text display note per item, used both to tell copies apart and to give access instructions. After the branch has produced an access condition, `SierraItemAccess.scala` lines 43 to 62 reconcile the two notes, first match wins:

| Case | Result |
|---|---|
| the access condition note equals the display note, or both are empty | the access condition keeps its note and the item gets none |
| the display note reads as an access note and the access condition already has a note | the display note is dropped |
| the display note reads as an access note and the access condition has no note | the display note moves onto the access condition |
| the display note isn't an access note | it stays on the item |

A note "reads as an access note" if it contains any of "unavailable", "access", "please contact", "@wellcomecollection.org", "offsite" or "shelved at", case-insensitively.

In the index, 4,305 items have a note moved onto an access condition whose branch sets none. They are mostly "This item requires facilitated access. Email library@…" (2,987) and "Please consult the digitised version…" (about 1,060). Only 369 items carry an item note, and 210 of those also have an access condition note. So the access condition note is where access instructions end up, and the precedence that #6589 has to specify applies mainly to these few thousand facilitated-access and fragile items.

The item note is computed by a second call to the same rule (`SierraItems.scala` lines 259 to 269). When an item's location type can't be resolved, it gets no physical location and so no access condition, and a display note that would have been folded in is lost.

### Which statuses and methods the Sierra rule can produce

| Value | Produced by |
|---|---|
| open | `closed-stores-online-request` |
| restricted | `closed-stores-restricted` |
| by appointment | `by-appointment` |
| permission required | `donor-permission`, with no items today |
| closed | `closed` |
| temporarily unavailable | the assessment, digitisation, hold, in-use and on-loan branches |
| unavailable | `missing`, `withdrawn` |
| safeguarded | `safeguarded` |
| open with advisory | never |
| licensed resources | never for physical items; digital locations only |
| online request, manual request, open shelves, not requestable | as in the branches table |
| view online | never for physical items |

`terms` is never set by the Sierra rule, although a code comment refers to it.

## The Axiell side

### What the Axiell transformer emits

`AxiellWorkBuilder.items` (`src/adapters/transformers/builders/axiell_work_builder.py` line 190) produces one item per work, in closed stores, with method not requestable and a status mapped from MARC 506 `$f` (`src/adapters/transformers/axiell/access_status.py`). The mapping is open, open with advisory, restricted (from both RESTRICTED and RESTRICTIONSAPPLY), permission required, unavailable (from DEACCESSIONED and MISSING), safeguarded, by appointment and closed. DATAISSUES and PRIVATE are not mapped and are logged as unrecognised. The method is always not requestable, so nothing that comes from Axiell alone can be requested today. Requesting works only where a Sierra item has been merged on.

The 441 Axiell items in the index split as not requestable with status open (330), restricted (48), closed (27), unavailable (5), and 31 with no access condition.

### What the Axiell to FOLIO sync writes

From `src/adapters/steps/axiell_folio_sync/mapping/config.py` (mapping version 2.6.0, last changed 2026-08-11) and `payloads.py`:

| FOLIO item field | Source |
|---|---|
| status | always "Available" (the payload default) |
| permanent loan type | 949 `$l`, defaulting to "Can circulate" |
| material type | 655 `$a` through a fixed table, defaulting to "book" |
| location | 852 `$b` (current location), with `215` and `183` overridden to `hicon` and a default of "History of Medicine" |
| barcode | 949 `$a` |
| discovery suppression | not set |
| the Axiell access status (506 `$f`) | not written anywhere |

platform#6731 changes the location rows: permanent location from 983 `$b` (the normal location), no default, and the current location written into administrative notes as "Axiell Current Location: …". Those changes aren't on this branch yet.

## Sierra inputs with no obvious FOLIO equivalent

| Sierra input | What it drives today | FOLIO candidate, to check in step 3 |
|---|---|---|
| OPAC message | online, manual, restricted, by appointment, donor permission, safeguarded | loan type, or a typed item note, if the migration carried it |
| hold count, loan rule, holdshelf status | the in-use branches | live request and loan state, available to the items service (#6654), not to the transform |
| due date | the on-loan branches | loans, live only |
| location codes in the rules for requesting | manual request, open shelves, unavailable, digitisation | FOLIO location, if the migration kept the granularity (#6584 says it's coarser) |
| item type | exhibition reserve, manual request | material type, if it was migrated one to one |
| status `b` or `c` (bound in, contained in) | `request-top-item`, 35,894 items | unknown; possibly bound-with relationships in FOLIO |
| display note | access instructions on 4,305 items | typed item notes |

## What stands out

- Three branches carry 87% of items. Online request from closed stores has 515k items, open shelves 109k, and "request top item" 36k. Four branches have fewer than 100 items each, and `donor-permission` has none.
- The catch-all fallback is the fourth-largest outcome, at 29,832 items. Most of it is one long-standing gap: Sierra status Available with OPAC message By appointment matches no branch, which accounts for roughly 25,700 items. On Axiell works these are archive items in "Unrequestable Arch. & MSS"; elsewhere they are mostly audiovisual, iconographic and offsite items. Almost none of them are in FOLIO yet, so whether the case needs handling there depends on how the Axiell sync and any later migration load bring them in.
- Archive requesting runs entirely through Sierra today. The Axiell transformer always emits "not requestable", so archives can only be requested through the 178,750 CALM-harvest Sierra items merged onto Axiell works. Once Sierra goes, FOLIO has to supply requestable items for both library material (the migrated Sierra items) and archives (the Axiell-synced items). The mapping for Axiell-synced items is still pending, so this analysis makes no recommendation on how their access conditions should be derived.
- Synced Axiell items carry no restriction in FOLIO. The sync sets status to "Available", never sets suppression, and doesn't write the 506 `$f` access status anywhere. Loan type (949 `$l`, defaulting to "Can circulate") is the only field that could differ between items, which likely explains the restricted-but-can-circulate case in #6589. I need to profile what 949 `$l` actually holds.
- Note precedence affects few items. 4,305 items have a display note moved onto the access condition, mostly "requires facilitated access" and "consult the digitised version". Only 369 items carry an item note.

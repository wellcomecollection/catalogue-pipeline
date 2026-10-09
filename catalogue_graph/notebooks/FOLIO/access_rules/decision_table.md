# FOLIO access condition decision table

Draft for platform#6589: a proposal for review and sign-off, not settled policy. It maps the state of a FOLIO item onto the access condition a reader sees, which is a method, an optional status and an optional note.

The table rests on three sources: the current Sierra rule ([current_access_rules.md](../current_access_rules.md)), the production tenant as pulled on 2026-10-01 ([folio_data_profile.md](../folio_data_profile.md)), and Collection Information's draft mapping, "Access States - FOLIO mapping draft" (`QA_Wellcome_AccessStatusMapping.xlsx`). Where the tenant doesn't yet hold what Collection Information intends, the row is written for the intended state and marked as pending further data migration.

Nothing here ships. Once signed off, the table moves into the transform (#6608) and the items service (#6654), so both read the same rules.

The machine-readable version sits next to this file: `rules.csv` (the rows, with model values for method and status), `notes.csv` (the note texts), `ruled_out.csv` and `evaluator.py`, which applies them to one item. Where a row below matches on one input or another, `rules.csv` splits it into two rows, so `digitisation` and `on-exhibition` each have a second row there (`digitisation-status`, `on-exhibition-material`).

## Inputs

The table reads five fields of the FOLIO item, plus one live input.

| FOLIO field | Table column | Values in the tenant today |
|---|---|---|
| Item status | `item_status` | Available, Missing, Intellectual item, Unavailable, Withdrawn, Unknown, Awaiting pickup, Paged, In transit, Checked out |
| Statistical code, the migrated Sierra OPAC message | `statistical_code` | ONLINE REQUEST, OPEN SHELVES, AVAILABLE, MANUAL REQUEST, BY APPOINTMENT, DIGITISATION, UNAVAILABLE, RESTRICTED, SAFEGUARDED, or none |
| Permanent loan type | `loan_type` | Can circulate, Unavailable |
| Effective location | `library`, `location_code`, `location_type` | 198 locations in six libraries: Closed stores, Open shelves, Offsite (DS), `bwith`, `cwith`, Laptops |
| Material type, the migrated Sierra item type under the same name | `material_type` | book, serial, boundwidth, ephemera, computer media, exhibit and 15 others |
| Open request on the item | `open_request` | live only: the items service (#6654) knows it, the transform doesn't |

### Why each one is needed

**Item status** carries the states that override everything else, as Sierra's status does: missing, withdrawn, unavailable, data issues, bound-with (Intellectual item) and circulation (checked out, awaiting pickup and so on).

**Statistical code** is where the Sierra OPAC message went in the migration, one code per Sierra value. It decides most outcomes, and it's the only field that tells restricted, by appointment, safeguarded and donor permission items apart. Those items are deliberately Available and "Can circulate" in FOLIO, so that staff can still request them, which means neither the status nor the loan type can express their restriction.

**Loan type** is needed because the Collection Information mapping makes it the field FOLIO uses to decide whether an item can circulate at all: it drives the request button and FOLIO's own circulation rules, and most non-requestable states get the loan type "Can't circulate". The access condition has to agree with that, or a reader would be shown an online request for an item FOLIO then refuses, which is the case #6622 is trying to avoid. So an item coded ONLINE REQUEST but given a non-circulating loan type isn't offered online (`not-circulating`). The restricted states sit above that row, so their "Can circulate" loan type never matters. In the tenant today, loan type barely separates anything: no "Can't circulate" loan type exists yet, and the row reaches 6 items. It's in the table for the target state, which is pending further data migration.

**Effective location** is one FOLIO field, read three ways:
- `library` is the library the location belongs to. It identifies bound-with and contained-in items (`bwith`, `cwith`), whose statistical codes are unreliable.
- `location_code` is the location's code, which for migrated locations is the Sierra location code. It carries the one location-code rule from Sierra's rules for requesting that still applies to migrated items (`offsite-manual-request`).
- `location_type` isn't a FOLIO field at all. It's derived from the library until #6584 settles location types: Closed stores and Offsite (DS) are closed stores, Open shelves is open shelves, and On Exhibition is on exhibition (no migrated item is in it today). `bwith`, `cwith` and Laptops have no location type, and neither does the placeholder location `migration`, which sits under Open shelves but holds 43 items that are not on the open shelves (most of them exhibit items).

**Material type** is the Sierra item type under the same name, and Sierra's rules for requesting use item types to block online requesting for computer media, exhibit and audiovisual material. It decides 31 items today: 5 computer media and 26 exhibit.

**Open request** covers holds, which win over the requesting rules in Sierra. Only the items service sees it.

### Considered and excluded

The issue also names ILL policy and the suppression flag. Neither is an input.

ILL policy isn't set on any holdings record in the tenant, and neither the Sierra rule nor the Collection Information mapping depends on it.

Suppression decides whether an item is shown at all, which happens before this table, in the item filtering of #6608. An item that is shown always gets an access condition from the table.

## Rows

First match wins, so order matters. A blank cell matches anything, and `|` separates alternatives. The baseline column names the Sierra branch that produces the same outcome today. The last column says whether the row works with the data as it currently sits in the tenant.

| Rule | When | Method | Status | Note | Baseline | Data |
|---|---|---|---|---|---|---|
| `missing` | status Missing | not requestable | unavailable | missing | `missing` | today |
| `withdrawn` | status Withdrawn | not requestable | unavailable | withdrawn | `withdrawn` | today |
| `data-issues` | status Unknown | not requestable | | contact | `unmapped-fallback` | today |
| `on-search` | status Declared lost | not requestable | | contact | `unmapped-fallback` | pending migration |
| `bound-with` | status Intellectual item | not requestable | | top item | `request-top-item` | today for `cwith`, pending migration for `bwith` |
| `bound-with-interim` | library `bwith` or `cwith` | not requestable | | top item | `request-top-item` | today only |
| `digitisation` | code DIGITISATION, or status In process (non-requestable) | not requestable | temporarily unavailable | digitisation | `unavailable-digitisation` | today, and pending migration for the new status |
| `closed` | code CLOSED | not requestable | closed | | `closed` | pending migration |
| `unavailable` | status Unavailable | not requestable | temporarily unavailable | assessment | `unavailable-assessment` | today |
| `in-circulation-closed-stores` | status Checked out, Awaiting pickup, Paged, In transit or Awaiting delivery; closed stores | not requestable | temporarily unavailable | in use | `closed-stores-on-hold-or-in-use` | today |
| `in-circulation-open-shelves` | same statuses; open shelves | open shelves | temporarily unavailable | in use | `open-shelves-in-use-or-on-loan` | today |
| `open-request` | open request; closed stores | not requestable | temporarily unavailable | in use | `closed-stores-on-hold-or-in-use` | live only |
| `on-exhibition` | location type on exhibition, or material type exhibit | not requestable | | exhibition text, or contact when there is none | `on-exhibition` | pending migration for the location and the text |
| `safeguarded` | code SAFEGUARDED | not requestable | safeguarded | | `safeguarded` | today |
| `restricted` | code RESTRICTED; closed stores | online request | restricted | | `closed-stores-restricted` | today |
| `by-appointment` | code BY APPOINTMENT | manual request | by appointment | | `by-appointment`, with a deviation (below) | today |
| `donor-permission` | code DONOR PERMISSION | manual request | permission required | | `donor-permission` | no items in either system; kept as a policy guard |
| `manual-request` | code MANUAL REQUEST | manual request | | manual request, or the display note | `closed-stores-manual-request` | today |
| `offsite-manual-request` | location code `ofvn1`, `scmwc`, `sgmoh`, `somet`, `somge`, `sompr` or `somsy` | not requestable | | contact | `unmapped-fallback` | today |
| `computer-media` | material type computer media | not requestable | | contact | `unmapped-fallback` | today |
| `audiovisual-non-requestable` | material type audio format non-requestable or video format non-requestable | not requestable | | contact | `unmapped-fallback` | no items yet |
| `not-circulating` | loan type Can't circulate or Unavailable | not requestable | | contact | `unmapped-fallback` | pending migration |
| `open-shelves` | code OPEN SHELVES; open shelves | open shelves | | | `open-shelves-available` | today |
| `online-request` | code ONLINE REQUEST; closed stores | online request | open | | `closed-stores-online-request` | today |
| `fallback` | anything else | not requestable | | contact | `unmapped-fallback` | catch-all |

### Notes

The note texts are the ones the Sierra rule uses today, so readers see no change in wording.

| Note | Text |
|---|---|
| missing | This item is missing. |
| withdrawn | This item is withdrawn. |
| top item | Please request top item. |
| digitisation | This item is being digitised and is currently unavailable. |
| assessment | This item is undergoing internal assessment or conservation work. |
| in use | Item is in use by another reader. Please ask at Library Enquiry Desk. |
| manual request | This item needs to be ordered manually. Please ask a member of staff, or email `<a href="mailto:library@wellcomecollection.org">library@wellcomecollection.org</a>`. |
| exhibition text | The item's own exhibition text, as Sierra took from MARC 999 `$a` (several joined with `<br />`); contact when there is none |
| contact | This item cannot be requested online. Please contact `<a href="mailto:library@wellcomecollection.org">library@wellcomecollection.org</a>` for more information. |

## Why the rows are in this order

Item status rows come first because a status overrides the statistical code, as it does in Sierra. The tenant has many items whose code says one thing and whose status another: 1,864 OPEN SHELVES and 1,847 ONLINE REQUEST items are Missing, and 716 withdrawn items still carry UNAVAILABLE. Putting the code rows first would offer those items for request.

`bound-with-interim` keys on the library, not the code. Collection Information lists the AVAILABLE code as an incorrect mapping meant for bound-with items, but it isn't reliable on its own: 470 closed-stores items carry it too, and 1,793 `bwith` items carry ONLINE REQUEST instead (the clean-up Collection Information's sheet notes as "data clean up to remove OPAC MSG"). The library catches all bound-with items whatever their code. Once Collection Information's target lands, `bwith` items get the status Intellectual item, `bound-with` catches them first, and this row becomes redundant.

`digitisation` comes before `unavailable` because today's digitisation items have the status Unavailable. Without it, they would get the assessment note instead of the digitisation note.

`closed` comes before `unavailable` for the same reason. Today the 11 closed items carry UNAVAILABLE, not CLOSED, so until that's migrated they reach `unavailable` and show temporarily unavailable instead of closed.

The circulation and open-request rows come before every code row. In Sierra, a hold or a loan wins over the requesting rules, because the specific branches all require a hold count of zero. Circulation shows up in the FOLIO item status, so the transform can see it at transform time (116 items today), and the items service refreshes it live.

`not-circulating` sits after the restricted states and before `open-shelves` and `online-request`. Collection Information keeps by appointment, donor permission, restricted and safeguarded items "Can circulate", so staff can request them in the tenant. For the rest, its target uses loan type to decide whether the request button shows. An item coded ONLINE REQUEST but given a non-circulating loan type is therefore not offered online. No such loan type exists in the tenant yet.

`on-exhibition` follows the circulation rows, as Sierra's exhibition branch follows its hold and loan branches. Exhibit items are caught by material type as well as location, because all 27 migrated exhibit items sit in the placeholder location `migration`, not in the empty "Exhibitions" location. Sierra showed each item's exhibition text from MARC 999. In the tenant that text is a staff-only "Reservation note (Sierra)", so readers can't be shown it until it becomes a public note; until then the row falls back to the contact note.

`offsite-manual-request` carries over the one location-code rule from Sierra's rules for requesting that still matters for migrated items. Sierra needs a manual request for these seven location codes, so an item there is only requestable when coded MANUAL REQUEST, which `manual-request` catches first; otherwise it gets the contact note. The coverage check found it: before this row, 7 such items were offered online requesting. The other location-code rules either name codes with no FOLIO location yet, or (the open shelves codes) are already covered by the OPEN SHELVES code.

`computer-media` and `audiovisual-non-requestable` carry over the item-type rules from Sierra's rules for requesting. Material types are the Sierra item types under the same names, one to one in a sample of 40 items per material type. Item type 14 (computer media) needs a manual request in Sierra: most of its 69 items carry BY APPOINTMENT or MANUAL REQUEST and are caught earlier, and this row stops the 5 coded ONLINE REQUEST from being offered online, which Sierra doesn't do either. Item types 15, 17 and 18 are moving image and sound, which Sierra never makes requestable online; none has been migrated, and the two material types whose names say non-requestable are the likely targets. Item type 22 is exhibit, covered by `on-exhibition`.

`open-shelves` and `online-request` check the location type as Sierra did. An ONLINE REQUEST item on the open shelves reaches `fallback`, as it does today.

`fallback` makes sure every shown item gets an access condition. Evaluation step counts the items reaching it as unmatched, because each one is a case the table doesn't explain.

## Sierra's rules for requesting

In Sierra, `SierraRulesForRequesting.scala` is an intermediate step. It turns five item fields into a verdict (requestable, needs manual request, on open shelves and so on), and `SierraItemAccess.scala` turns that verdict into the access condition. This table doesn't port that structure. It reads the same information from FOLIO directly, so the rules for requesting are absorbed into the rows above, not carried over as a separate step. Each rule group ended up in one of three places:

| Rule group, by the Sierra field it reads | Where it went |
|---|---|
| Item status (fixed field 88): missing, on search, withdrawn, unavailable, closed, safeguarded, data issues, bound-with | the status rows: `missing`, `withdrawn`, `on-search`, `unavailable`, `data-issues`, `bound-with`; closed and safeguarded via their statistical codes |
| Loan rule (fixed field 87) and the on-holdshelf status: in use by another reader | the circulation rows for what the item status shows, and `open-request` for holds, live only (#6654) |
| OPAC message (fixed field 108): manual request, unavailable, at digitisation | the statistical code rows: `manual-request`, `by-appointment`, `digitisation` |
| Item type (fixed field 61): exhibition reserve, no public message, manual request | material type: `on-exhibition-material`, `computer-media`; the audiovisual item types aren't migrating to FOLIO |
| Location code (fixed field 79), open shelves list | the OPEN SHELVES statistical code, which nearly all items in those locations carry (108,977 of the 108,985 Sierra open-shelves items in FOLIO) |
| Location code, offsite manual request list | `offsite-manual-request` |
| Location code, the other lists (contact us, digitisation `temp` codes, no public message, most of the manual request list) | dropped: none of these codes holds a live Sierra item (see [location_codes.md](../location_codes.md)) |
| Location code, data protection list and `harcl` | not yet decided; see below |

The coverage check is the evidence this holds. The table gives the same outcome as Sierra for 99.41% of the items found in both systems, and no rule-for-requesting outcome is left unexplained.

That evidence covers migrated items only. Archives aren't in FOLIO yet, and they're where the remaining location-based rules apply: the data protection codes (`sc#ac` and others, about 51,500 Sierra items) and `harcl` (3,596). When the Axiell to FOLIO sync mapping is settled, it has to be decided whether those restrictions come across as an access status, from the Axiell record's 506 `$f`, as a rule on the sync's `AxC` locations, or not at all. That's the one part of the rules for requesting whose fate is still open.

## Deviations from the Sierra rule

`by-appointment` corrects a gap in the current logic. Sierra only treats an item as by appointment when its status is Permission required. Items with the status Available and the OPAC message By appointment match no branch, so they get the generic fallback note: about 25,700 items in the served index, and unhandled since RFC 042's 2021 list. Collection Information's mapping gives every by-appointment item the status Available, so FOLIO can't tell the two apart, and the row treats both as by appointment. Few of the items affected are in the tenant today (6 of 600 sampled), because most are archives awaiting the Axiell sync or audiovisual items not yet migrated. So the size of this deviation can't be measured yet.

`unavailable` keys on the status alone. Sierra also required the OPAC message Unavailable, and sent other combinations to the fallback. In the tenant, the status Unavailable comes from Sierra's status `r`, and Collection Information records its other pairings as cleaned up, so I expect this to affect very few items.

## Ruled out

| Value | Why it is never produced |
|---|---|
| open with advisory | No Sierra or FOLIO field carries it for items. Collection Information groups open shelves under "Open/Open With advisory", but the Sierra rule gives open-shelves items no status. Which is meant is an open question below. |
| licensed resources | Digital locations only, outside this table. Addressed by platform#6612 |
| view online | Digital locations only, outside this table. Addressed by platform#6612 |
| `terms` | Never set, as in Sierra. Everything a reader is told goes in the note. |

## Note precedence

The issue asks for "the precedence between the note and the access condition" to be specified, because in the Sierra rule they are produced together and one can suppress the other. Here it is set per row, in the `display_note` column of `rules.csv`, and the evaluator applies whatever that column says.

There are two notes in play. The **access note** is the note on the access condition, set by the row (the Notes table above). The **display note** is the public "Display note" on the FOLIO item, which cataloguers use both to tell copies apart ("impression lacking lettering") and to give access instructions ("Email library@… to tell us why you need access").

| `display_note` value | Rows | What happens to a display note |
|---|---|---|
| `condition-note-wins` | all but one | If it reads as access information, it becomes the access note when the row sets none, and is dropped when the row sets one: the row's own note always wins. Any other display note stays on the item as the item note. |
| `display-note-wins-if-manual-request` | `manual-request` | If it reads as manual-request instructions, it replaces the row's placeholder note, because it explains how to ask for this particular item. Otherwise the row behaves as `condition-note-wins`. |

A display note "reads as access information" if it mentions "unavailable", "access", "please contact", "@wellcomecollection.org", "offsite" or "shelved at", and "reads as manual-request instructions" if it mentions phrases such as "to view this item", "why you need access" or "details of your request". Both lists, and both behaviours, are the Sierra rule's (`SierraItemAccess.scala`), so readers see no change.

So the precedence, in order, is:
1. On `manual-request`, a display note with manual-request instructions.
2. The row's own access note.
3. A display note with access information, when the row sets no note.

A display note that is neither stays on the item.

In the served index, 4,305 items have a display note moved onto the access condition this way, and 7,041 migrated items carry a public display note.

## Open questions

These need Collection Information, or whoever owns reading-room policy, to decide:

- **Open shelves.** **RESOLVED** Should open-shelves items show an access status (open, or open with advisory), or none as today?

  Collection Information's answer on PR #3736:

  > As discussed, there doesn't need to be any change to how `open-shelves` items look online. We spoke about "Status" online, doesn't actually mean Item Status nor is it coming from STATUS (Sierra) - "open shelves" is an OPAC MSG and will be a Statistical Code (Item) in Folio.
  >
  > The default Item Status = Available when Stat Code = Open Shelves. If the Item Status is Missing, Declared Lost (On Search), Unknown or Withdrawn this will be determined by the decision table.
- **Audiovisual material types.** **RESOLVED** Item types 15, 17 and 18 (moving image and sound) aren't migrated yet. The tenant has three empty material types that look like their targets: audio format non-requestable, video format requestable and video format non-requestable. Sierra makes all three item types non-requestable online, so which of them becomes "video format requestable", and whether it really should be requestable, needs confirming.

  Collection Information's answer on PR #3736:

  > AV (and visual/art) will not be migrating to Folio. It is planned to move from Sierra to Axiell Collections later this year-before March 2027 Go Live.
- **Exhibition.** The 27 exhibit items sit in the placeholder location `migration`, and their exhibition text is a staff-only note. Should they move to the "Exhibitions" location, and should the text become a public note so readers can see where the item is?

  Collection Information's answer on PR #3736:

  > This question is bound-up in how Exhibitions intend to use (or not) Folio for recording exhibitions data.
  >
  > In Folio future state, we may want to make use of a temporary location (we have set up Wellcome Collection (Institution) > Eustom Road (Campus) > On exhibition (Library) > On exhibition (location) for now. This means that other item data like Item Type, Item Status and Statistical codes don't have to be manually edited to replicate the Sierra situation, which was automated.
- **Digitisation.** Collection Information's mapping has no requesting rule for digitisation, and suggests a temporary location as an alternative to the status In process (non-requestable). The row covers both the code and the status. Which will be used?

  Collection Information's answer on PR #3736:

  > Nothing has been mapped here, the data is similar to Sierra with Item Status = Unavailable and Statistical Code (OPAC MSG) = @ Digitisation. But the intention is to change this.
  >
  > Similarly to Exhibitions, we intend to test changing the temporary location for digitisation and we have set up Wellcome Collection (Institution) > Eustom Road (Campus) > Digitisation (Library) > Digitisation (location) for post-Go Live playing around with.
- **Location rules.** Sierra's rules for requesting name 80 distinct location codes, and 66 of them have no FOLIO location yet (see [location_codes.md](../location_codes.md)):
  - 58 have no live Sierra items. They're stale entries in the rules configuration and need nothing.
  - 6 are data protection codes (`sc#ac` and others, about 51,500 Sierra items). I think they mostly or entirely apply to archive items, so their restriction would come across with the Axiell sync rather than the Sierra migration.
  - `harcl` (3,596 items) keeps offsite deepstore archives unrequestable. It's the same case as the data protection codes.
  - `gblip` makes its items manual request. It's set on one item, a departmental copy of the BMJ (`i18699145`) on a suppressed bib (`b1497535x`), so readers never see it.

  What's left to decide is whether the data protection and `harcl` restrictions come across with the Axiell sync, which depends on the sync's mapping.
- **Contained-in items.** 324 contained-in ephemera items (`cwith`, status Intellectual item in the tenant) can be requested online in Sierra today. Collection Information's mapping makes all contained-in items not requestable, so these 324 would stop being requestable. Is that intended?
- **Axiell-synced items.** Their mapping is still pending, so this table makes no claim about them.

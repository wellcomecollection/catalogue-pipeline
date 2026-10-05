# Axiell to FOLIO: material type and access mapping

A decision document for Collection Information, not a description of the code.
It sets what AxC sends beside what the FOLIO prod tenant offers, for the two
fields where the mapping is wrong or undecided: **material type** and **access**.

| | State |
| --- | --- |
| **Item status**, from the access category (`506$f`) | **Defined and implemented.** CI to Confirm the table in section 2. |
| **Loan type** | **Open.** No AxC field is mapped, so every item is `Can circulate`. Section 2 and question 1. |
| **Material type**, from object_category (`655$a`) | **Partly defined.** Three digital rows and the default need an answer. Section 1. |

For what the sync maps today see
[axiell-folio-field-mappings.md](axiell-folio-field-mappings.md); for the full
list of gaps see [axiell-folio-mapping-gaps.md](axiell-folio-mapping-gaps.md);
the questions themselves are in
[../rfcs/collection-information-questions.md](../rfcs/collection-information-questions.md).

## Provenance

| | Source | Date |
| --- | --- | --- |
| AxC values and counts | Full scan of the Axiell adapter table, 209,374 rows, **187,996 item-level**, 0 parse errors, via [`notebooks/axiell_adapter_field_coverage.ipynb`](../notebooks/axiell_adapter_field_coverage.ipynb) | 2026-09-29 |
| FOLIO values | Read-only GETs against prod (`api-wellcome.folio.ebsco.com`, tenant `fs00001190`) on the endpoints `RefCache.load()` uses | 2026-09-29 |
| AxC to MARC provenance | `axiell-collections-xslt`, `axc_to_marcxml_collect.xsl` at `d19f42d` | 2026-06-11 |

All counts are item-level only (`351$c == ITEM`); percentages are of 187,996.

---

## 1. Material type

### What AxC sends: `655$a`

| `655$a` | Item records | | Now resolves to |
| --- | ---: | --- | --- |
| `Archives - Non-digital` | 176,110 | 93.7% | `archive` |
| `Visual Material` | 4,347 | 2.3% | `non-projected graphic` |
| `Sound - Digital` | 2,860 | 1.5% | `audio format non-requestable` |
| `Archives - Digital` | 2,535 | 1.3% | `archive` |
| `Moving Image - Non-digital` | 1,048 | 0.6% | `film` |
| `Sound - Non-digital` | 894 | 0.5% | `audio format requestable` |
| `Moving Image - Digital` | 85 | | `video format non-requestable` |
| `Archives - Hybrid`, `Pictures` | 2 | | `archive`, `non-projected graphic` |
| *(absent)* | 115 | | nothing: the record fails |

Every value in the corpus now resolves, covering 187,882 of 187,997 item
records. Only the 115 with no `655$a` fail.

**What this fixed.** 9,829 records (5.2%) previously did not resolve at all. Two
causes, both key mistakes rather than missing targets: the table mapped
`Visual Material - Non Digital` while AxC only ever says `Visual Material`, so
the agreed `non-projected graphic` rule had never once fired; and the four
`- Non Digital` (spaced) keys matched nothing, because AxC emits `- Non-digital`.
Those four have been removed and the real values added.

*(An earlier version of this section said 9,944 records were silently typed
`book`. That was wrong: an unmapped value was passed to the tenant, resolved to
nothing and failed the record. Only the 115 with no category ever took the
default, which has since been removed too.)*

### The mapping: implemented

Live in `config.MATERIAL_TYPE`. The tenant has 29 material types and nothing
needed provisioning: every target below already exists. The three rows marked
**needs CI** are implemented on the reasoning given and are the ones to confirm.

| `655$a` | Records | Proposed | Why |
| --- | ---: | --- | --- |
| `Archives - Non-digital` | 176,110 | `archive` | unchanged |
| `Archives - Hybrid` | 1 | `archive` | same intellectual form |
| `Visual Material` | 4,347 | `non-projected graphic` | the agreed target; only the key was wrong |
| `Pictures` | 1 | `non-projected graphic` | same form |
| `Moving Image - Non-digital` | 1,048 | `film` | unchanged |
| `Sound - Non-digital` | 894 | `audio format requestable` | unchanged |
| **`Sound - Digital`** | 2,860 | `audio format non-requestable` | **needs CI**: digital surrogate, not the carrier a reader requests |
| **`Moving Image - Digital`** | 85 | `video format non-requestable` | **needs CI**: as above |
| **`Archives - Digital`** | 2,535 | `archive` | **needs CI**: no digital-archive type exists, so the same intellectual form as the non-digital archives. `computer media` and `migration` are the alternatives |

**This tenant encodes requestability in the material type** (note the
`requestable` / `non-requestable` pairs), and `Sound - Non-digital` is already
mapped to the requestable half. So the three digital rows also decide whether a
reader can request them, which is the same question section 2 asks. **Decide
sections 1 and 2 together.**

### The default: removed

There is no longer a material-type default. A record with no `655$a`, like one
with a category the table does not map, now fails and is reported.

The options considered were to keep `book`, to default to `archive` (right far
more often, since 93.7% of the corpus is archival, but still silent), or to have
no default at all. The last was chosen: `book` was wrong for every one of the 115
records it applied to, and a plausible-looking wrong material type is worse than
an error row, because this tenant encodes requestability in the material type.

**To confirm:** is failing the right behaviour for the 115 records with no
category, or would you rather they defaulted to `archive` and synced?

---

## 2. Access

### What AxC sends

| AxC field | MARC | Says | Item records |
| --- | --- | --- | ---: |
| `access_status/value` | `506$f` | **Who** may access it | 187,687 (99.8%) |
| `access_category.notes` | `506$a` | Free text, 609 distinct | 45,327 (24.1%) |
| `closed_until` | `506$g` | Date access opens | 15,291 (8.1%) |
| `use_restriction.restriction` | `540$a` | **How** it may be requested | **0 (0.0%)** |
| `use_restriction.date` | `540$g` | Date the restriction lifts | **0 (0.0%)** |
| loan-type code (retired) | `949$l` | nothing, it is empty | 0 (0.0%) |

`506$f` distribution: `OPEN` 171,804 (91.4%), `CLOSED` 9,819 (5.2%),
`RESTRICTED` 5,496 (2.9%), `PERMISSIONREQUIRED` 356, *absent* 309, `MISSING`
160, `DATAISSUES` 24, `OPENWITHADVISORY` 14, `DEACCESSIONED` 13, `SAFEGUARDED` 1.

**`540` is emitted by the stylesheet but absent from the harvest.** The
stylesheet has mapped `UseRestriction` to `540` since 2026-06-11 and both
item-level sample records produce it, yet the 2026-09-29 scan found `540$a` on 0
of 187,996. The live OAI feed is probably running an older stylesheet. Until
that is resolved there is no `540` data to measure.

### Defined: `506$f` to item status

Implemented. Every value is in FOLIO's fixed item-status enum, so nothing needs
provisioning.

| `506$f` | Records | Item status |
| --- | ---: | --- |
| `OPEN`, `OPENWITHADVISORY` | 171,818 | `Available` |
| `RESTRICTED` | 5,496 | `Available` |
| `PERMISSIONREQUIRED`, `SAFEGUARDED`, `CLOSED` | 10,176 | `Restricted` |
| `MISSING` | 160 | `Missing` |
| `DEACCESSIONED` | 13 | `Withdrawn` |
| `DATAISSUES` | 24 | `Unknown` |
| *absent* | 309 | `Unavailable` |

`RESTRICTED` maps to `Available` because restricted material is genuinely
available and can be requested online. The restriction is that the reader signs
to agree to the viewing conditions, which happens before the material is handed
over, so it does not affect whether the item can be requested or produced.

`Restricted` and `Withdrawn` did not appear in a 5,000-item prod sample and
should be confirmed against the tenant's FOLIO version. Statuses observed in
use: `Available`, `Missing`, `Intellectual item`, `Unknown`, `Unavailable`,
`Awaiting pickup`.

### Open: the loan type

Every item is written as `Can circulate`, the default and currently the only
value, because no AxC field is mapped to the loan type. Both candidates have
been mapped here and reverted pending your answer.

**Consequence:** all **15,869 records (8.4%) whose category restricts access**,
including the 9,819 whose own access note reads *"This item is closed and cannot
be accessed"*, are requestable. The item status does carry the restriction, so
FOLIO displays them correctly while the request path does not. **Settle before
any production run.**

The tenant has 6 loan types and nothing needs provisioning: `Can circulate`,
`Course reserves`, `ILL`, `Reading room`, `Selected`, `Unavailable`.

Two shapes to choose between:

| | Approach | Note |
| --- | --- | --- |
| **A** | The access category drives it | Deployable now. `CLOSED` gives `Unavailable`, `RESTRICTED` gives `Reading room`, and `OPEN` is question 1. |
| **B** | The use restriction drives it, with the access category as a ceiling it can only narrow | Better matched to what a loan type means, but blocked until `540` reaches the harvest. The ceiling is what stops a permissive restriction making a `CLOSED` item requestable. |

Evidence for B, from the stylesheet's own samples: an `OPEN` record carrying
`Online request`, and a `RESTRICTED` one carrying `By appointment` with a
`540$g` of 2066-01-01. An item that must be requested online is not one a reader
can borrow, and only `540$a` says so. Note that a loan type has no time
dimension, so a date-bounded restriction can only be recorded as a note, never
enforced.

---

## 3. What we need from Collection Information

1. **Should open archival material be `Can circulate` or `Reading room`?**
   Covers **171,804 records, 91.4% of everything the sync writes**, the widest
   blast radius here. `Reading room` exists precisely for material that does not
   circulate, which is how archival collections are often held.
2. **Which field should drive the loan type:** the access category, the use
   restriction, or both with the category as a ceiling? (Section 2, A or B.)
3. **What material type for the three digital categories?** `Sound - Digital`,
   `Archives - Digital`, `Moving Image - Digital`. Because this tenant encodes
   requestability in the material type, that choice also decides whether a
   reader can request them.
4. **Is the item-status table in section 2 right?** In particular, should `CLOSED` be
   `Restricted` or `Unavailable`?
5. **Is "set the item status once, when the item is created" good enough?**
   That is what the sync does today: it sends the derived status on create, and
   on every run after that it reads the item back from FOLIO and sends FOLIO's
   own status unchanged. Circulation owns the field once the item exists, so
   writing it every run would reset a checked-out item to `Available`. The cost
   is that an AxC access change never reaches an item that already exists. If
   that is not acceptable, the proposal is split ownership: the sync may
   overwrite only the statuses it derives (`Available`, `Restricted`, `Missing`,
   `Withdrawn`, `Unavailable`) and leaves circulation statuses alone. See
   section 6 of the questions doc for the three options side by side.
6. **Should `506$a` / `506$g` / `540$g` be carried as an item note?** Free text
   on 24.1% of records and a date on 8.1%; the only way a date-bounded
   restriction can be represented at all.
7. **Should an unrecognised `655$a` or `506$f` fail the record, or default?**
   As implemented for access: absent takes the safe default, present-but-
   unrecognised fails. A new AxC category would then halt those records until
   someone maps it.

## Before implementing

**On the tenant:** that `Restricted` and `Withdrawn` are accepted by this FOLIO
version; and that the chosen loan types behave as expected in the request path
(the names exist, their circulation rules have not been examined).

**On the AxC side:** which stylesheet the live OAI feed runs (the blocker for
option B); the full use-restriction thesaurus, since only `Online request` and
`By appointment` are known; and whether `use_restriction.restriction.lref` can
be emitted as `540$0`, so a mapping can key on a thesaurus id rather than a
display string that Axiell sometimes stores doubled
(`"By appointmentBy appointment"`).

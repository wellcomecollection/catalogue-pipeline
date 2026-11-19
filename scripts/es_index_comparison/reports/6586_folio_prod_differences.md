# FOLIO pipeline against production: field differences

Round 1 comparison for [platform#6586](https://github.com/wellcomecollection/platform/issues/6586).

## What was compared

- **Production:** `works-identified` on pipeline `2026-09-30` (Scala transformer on Sierra records).
- **FOLIO:** `works-identified` on pipeline `2026-10-20` (Python transformer on FOLIO records), after the full reindex of 18 November 2026.
- **Population:** 738,858 pairs of Visible works. A FOLIO work has the canonical id of the Sierra work it replaces, so pairs are matched by id.
- **Normalisation:** the same steps as the transformer validation notebook (`standardise_works_permanent`), which remove the differences already agreed as structural, such as NFC normalisation of Sierra labels and Period ranges that only Python produces.

16,560 pairs (2.2%) differ in at least one field after normalisation. Counts ignore the order of values within a field. Items and holdings are out of scope for this round.

## How to read the tables

Each row is one kind of difference, with the number of works it affects.

**Cause** says where the difference comes from:

- **Transformer:** the Python extractor gives the FOLIO result from the Sierra record too, so the two pipelines disagree on the same data.
- **Source:** the Python extractor gives the production result from the Sierra record, so the FOLIO record itself differs from Sierra's.
- **Stale production:** production holds a value from before a Scala fix, and would match after the Sierra work is transformed again.

**Reference** points to the PR description that names the difference, where there is one.

**Status** says whether the difference needs attention, whether or not it was already known:

| Status | Meaning |
|:---:|---|
| ✅ | Explained and needs no action |
| ⚠️ | Needs investigation or a decision. The row is in bold |

## Contributors

Reference: [#3787](https://github.com/wellcomecollection/catalogue-pipeline/pull/3787).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 259 | Identifier scheme is `nlm-mesh` in production and `lc-names` in FOLIO. On 10 of them the contributor is also an Agent in production and a Person in FOLIO | Transformer: Scala reads the second indicator of an analytical 7xx entry as a thesaurus | #3787, analytical added entries | ✅ |
| 101 | `primary` flag differs | Source: FOLIO records were edited to keep one main entry where Sierra has several | | ✅ |
| 63 | Type differs, for example Concept in production and Person in FOLIO | Transformer: Python gives a name used as both contributor and subject the more specific type | #3787, ontology type harmonisation | ✅ |
| 56 | Backslash in production, space in FOLIO | Source: the migration turned backslashes into spaces | | ✅ |
| 31 | Double space in the production label | Transformer: Python strips whitespace around subfield values | #3787, subfield whitespace | ✅ |
| 3 | Trailing comma on the production label | Transformer: Python trims every trailing comma | #3787, trailing commas | ✅ |
| **3** | **Production has an `lc-names` identifier, FOLIO a label-derived one** | **Source: the Sierra ǂ9 local code became a second ǂ0 in FOLIO, and the transformer drops the identifier when a field has two** | | ⚠️ |
| 3 | Trailing full stop missing from the FOLIO label, for example `A. B` | Source: the FOLIO record has no full stop | | ✅ |
| 3 | Production has a contributor with an empty or `.` label | Source: the FOLIO record has no such field | | ✅ |
| 2 | Production has one more contributor, a duplicate with an `nlm-mesh` identifier | Transformer: a knock-on of the analytical entry misreading, which stops Scala merging the duplicate | #3787 names the misreading but not this effect | ✅ |

## Subjects

Reference: [#3766](https://github.com/wellcomecollection/catalogue-pipeline/pull/3766).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 81 | Label-derived identifier of a name concept differs, for example `lararivera,jorge,(1961-)` in production and `lararivera,jorge,` in FOLIO | Transformer: the sole concept of a name heading keeps its own identifier in Python | #3766, name concept identifiers | ✅ |
| 63 | Type differs, for example Concept in production and Person in FOLIO. These are the same 63 works as in contributors | Transformer: Python gives a name used as both contributor and subject the more specific type | #3787, ontology type harmonisation | ✅ |
| **49** | **Production has an `lc-subjects` or `nlm-mesh` identifier, FOLIO a label-derived one** | **Source: the Sierra ǂ9 local code became a second ǂ0 in FOLIO, and the transformer drops the identifier when a field has two** | **#3766 calls it a data quality issue and leaves it open** | ⚠️ |
| 8 | Production label repeats a date, for example `Folie et déraison, 1926-1984.` | Transformer: Python drops a repeated subfield that duplicates an earlier value | #3766, duplicated subfield | ✅ |
| 2 | `Before 1800` starts at year -9999 in production and year 1 in FOLIO | Transformer: period parser | #3766, period parser | ✅ |
| 1 | Production identifier reads `inuenzaepidemic`, and the Period has no range | Transformer: Scala strips `fl` from inside `Influenza` | #3766, period identifiers | ✅ |
| 1 | Backslash in production, space in FOLIO | Source: the migration turned backslashes into spaces | | ✅ |

## Alternative titles

References: [#3646](https://github.com/wellcomecollection/catalogue-pipeline/pull/3646), [#3754](https://github.com/wellcomecollection/catalogue-pipeline/pull/3754).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 760 | Production title includes a control subfield, on 718 of them an LC Names URI at the end | Stale production: these Sierra works were last transformed before the Scala fix of 6 October | #3754 | ✅ |
| 2 | Backslash in production, space in FOLIO | Source: the migration turned backslashes into spaces | | ✅ |

## Production

Reference: [#3729](https://github.com/wellcomecollection/catalogue-pipeline/pull/3729).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 6,090 | Same date range, but the label stored on the range differs, for example `1802` in production and `An X, 1802` in FOLIO | Transformer: Python parses the catalogued date. Scala could not, and took the range from 008 | #3729, period parser | ✅ |
| 1,723 | Date range differs, for example `1538 [colophon 1537]` is 1538 in production and 1537 to 1538 in FOLIO | Transformer: Python parses the catalogued date, where Scala took the range from 008 or parsed it differently | #3729, period parser | ✅ |
| 950 | FOLIO has a date range, production has the date label only, for example `1975,` | Transformer: Python parses a catalogued date Scala cannot | #3729, period parser | ✅ |
| 468 | FOLIO has a date, production has none, for example `1900-1970` | Transformer: Python reads 008 dates with unknown digits such as `19uu` | #3729, 008 date parser | ✅ |
| **47** | **FOLIO date range starts before the year 1000, for example `[199]` becomes the year 199 and `905` the year 905. On 28 of them production has the right year from 008** | **Transformer: Python reads a truncated year as a three-digit year. One of the 47, `400?`, is a genuine early date** | | ⚠️ |
| 23 | Production has a date range, FOLIO has the date label only, for example `[1584-1566?]` | Transformer: Python declines backwards ranges and ambiguous comma pairs | #3729, period parser | ✅ |
| 12 | FOLIO has a production event with a year, production has none | Source: the Sierra record has no 008, and the migration created one with the year | | ✅ |
| 7 | Production has a date `9999`, FOLIO has none | Transformer: Python does not read `9999` in 008 as a year | | ✅ |
| 3 | Backslash in production, space in FOLIO | Source: the migration turned backslashes into spaces | | ✅ |
| 1 | Production has `1981-1974`, FOLIO `1974-1981` | Transformer: Python reorders a backwards 008 range | | ✅ |

## Languages

Reference: [#3639](https://github.com/wellcomecollection/catalogue-pipeline/pull/3639).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 3,854 | Production has a language, FOLIO has none. 2,168 of them are journals | Transformer: FOLIO reads 008, which is blank, fill characters, `und`, `mul` or `zxx`. Production reads the Sierra LANG field, which has a language | #3639, 008 uninformative | ✅ |
| **864** | **Production has a language, FOLIO has none. 646 of them are ephemera** | **Source: the Sierra record has no 008, and the migration created one that says `und`. The Sierra language survives only in 998 ǂf, which the transformer does not read** | **#3639 flagged the earlier placeholder 008, which said `eng`** | ⚠️ |
| 572 | The two have different languages | Transformer: 008 and the Sierra LANG field name different languages | #3639, both set | ✅ |
| 382 | FOLIO has a language, production has none | Transformer: 008 has a language, the Sierra LANG field is blank or unrecognised, such as `n/a` | #3639, LANG blank | ✅ |
| 14 | Production lists a language twice, once under an obsolete code (`scc` on 13, `snh` on 1) | Transformer: Python keeps one language per label and prefers the current code | | ✅ |
| 1 | FOLIO has Hebrew, production has none | Source: the Sierra record has no 008, and the migration created one that says `heb` | | ✅ |

## Collection path

Reference: [#3825](https://github.com/wellcomecollection/catalogue-pipeline/pull/3825).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 61 | Own segment differs, for example `IDC20007/25203` in production and `IDC20007/b20241951` in FOLIO | Transformer: FOLIO takes the own identifier from 099 or the b-number, Scala from 001 | #3825, own identifier | ✅ |
| 56 | Production host keeps the `(UkLW)` prefix, and the own segment differs. On 23 of them FOLIO also replaces slashes with `\|` | Transformer | #3825, `(UkLW)` prefix, own identifier and slashes | ✅ |
| 39 | FOLIO has a path, production has none | Transformer: FOLIO falls back to the b-number as the own identifier | #3825, own identifier | ✅ |
| 18 | Production host keeps the `(UkLW)` prefix | Transformer: FOLIO strips it | #3825, `(UkLW)` prefix | ✅ |
| 9 | Production has a path, FOLIO has none | Transformer: the host is another organisation's record, such as `(OCoLC)`, which FOLIO ignores | #3825, links to records we do not hold | ✅ |
| 2 | Root path `EPB/T/248` in production, `EPB\|T\|248` in FOLIO | Transformer: slashes inside an identifier become `\|` | #3825, slashes | ✅ |
| 1 | Host is an OCLC number in production and a b-number in FOLIO | Transformer: the 773 has both, and FOLIO uses the Wellcome one | #3825, links to records we do not hold | ✅ |

## Physical description

Reference: [#3627](https://github.com/wellcomecollection/catalogue-pipeline/pull/3627).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 22 | Backslash in production, space in FOLIO | Source: the migration turned backslashes into spaces | | ✅ |

## Notes

Reference: [#3896](https://github.com/wellcomecollection/catalogue-pipeline/pull/3896).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 16 | Backslash in production, space in FOLIO | Source: the migration turned backslashes into spaces | | ✅ |
| 2 | Note text differs | Source: the Sierra record was edited after the migration (22 October and 5 November) | | ✅ |
| 1 | Production note ends with an item barcode, FOLIO's does not | Source: the FOLIO record does not have the barcode in that note | | ✅ |

## Relations

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 8 | Production lists the same series twice, FOLIO once | Source: Sierra stores the title in two Unicode forms in 490 and 830, so they do not deduplicate. FOLIO stores both in NFC | #3628, NFC normalisation | ✅ |

## Title

Reference: [#3628](https://github.com/wellcomecollection/catalogue-pipeline/pull/3628).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 5 | Backslash in production, space in FOLIO | Source: the migration turned backslashes into spaces | | ✅ |
| 1 | `Metamorphosis.` in production, `Metamorphasis.` in FOLIO | Source: the Sierra record was edited on 18 November, after the migration | | ✅ |

## Description

Reference: [#3640](https://github.com/wellcomecollection/catalogue-pipeline/pull/3640).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 5 | Backslash in production, space in FOLIO | Source: the migration turned backslashes into spaces | | ✅ |

## Merge candidates

Leaves aside the candidate every FOLIO work has for its own HRID.

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 2 | FOLIO has an EBSCO candidate, production has none | Transformer: the Sierra 001 is truncated (`24e`). Python reads the full identifier from 035 | | ✅ |
| 1 | FOLIO has a candidate for a digitised Sierra work, production has none | Source: the FOLIO record has a 776 link that the Sierra record, edited on 5 November, does not | | ✅ |
| 1 | Production has a Miro candidate, FOLIO has none | Source: the Sierra record, edited on 28 October, has an 089 that the FOLIO record does not | | ✅ |

## Genres

Reference: [#3667](https://github.com/wellcomecollection/catalogue-pipeline/pull/3667).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 2 | FOLIO gives the Period `Early works to 1800` a date range, production does not | Transformer: Python parses a period Scala does not | #3667, period parser | ✅ |
| 1 | Production has `Hindi language - Dictionaries`, FOLIO `Hindi language` | Transformer: a 655 with two ǂa, where Python takes the first | #3667, two ǂa | ✅ |

## Current frequency

Reference: [#3632](https://github.com/wellcomecollection/catalogue-pipeline/pull/3632).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 2 | Double space in production | Transformer: Scala joins subfields with an extra space | #3632 | ✅ |

## Format

Reference: [#3897](https://github.com/wellcomecollection/catalogue-pipeline/pull/3897).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 1 | Books in production, Journals in FOLIO | Source: the Sierra material type was changed on 29 October, after the migration | | ✅ |

## Lettering

Reference: [#3786](https://github.com/wellcomecollection/catalogue-pipeline/pull/3786).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 1 | Trailing space in production | Transformer: Python strips whitespace | #3786 | ✅ |

## Fields with no differences

| Field | Note |
|---|---|
| Edition | 9 pairs differ before normalisation, all by Unicode form |
| Other identifiers | Compared as sets, leaving out each system's own identifiers (`sierra-system-number`, `sierra-identifier`, `folio-instance`, `folio-instance-hrid`) |
| Designation | |
| Former frequency | |
| Work type | |

## Open questions

| Field | Works | Question |
|---|---:|---|
| Subjects, contributors | 50 | **Two ǂ0 in one field.** The work loses its authority identifier in FOLIO. 49 works are affected in subjects and 3 in contributors, and 2 of them are in both. Either the records are fixed at source, or the transformer picks the ǂ0 that looks like an authority identifier. |
| Production | 47 | **Truncated years read as three-digit years.** A catalogued date such as `[199]` or `905` becomes the year 199 or 905 in FOLIO, which affects date filters and sorting. Production avoids it on 28 of them by taking the year from 008. The period parser could refuse a bare three-digit year when 008 has a four-digit one. |
| Languages | 864 | **Language lost on records with no 008 in Sierra.** The migration gave them an 008 that says `und`, so the work has no language in FOLIO. The Sierra language is still in 998 ǂf. Either the 008 is corrected at source, or the transformer falls back to 998 ǂf when 008 is uninformative. |

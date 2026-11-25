# FOLIO pipeline against production: field differences

Round 1 comparison for [platform#6586](https://github.com/wellcomecollection/platform/issues/6586).

## What was compared

- **Production:** `works-identified` on pipeline `2026-09-30` (Scala transformer on Sierra records).
- **FOLIO:** `works-identified` on pipeline `2026-10-20` (Python transformer on FOLIO records), after the full reindex of 18 November 2026.
- **Population:** 1,039,077 pairs of works with the same canonical id. 738,858 pairs are Visible on both sides, and 300,215 are suppressed on both sides, which means Deleted works with no data. The other 4 pairs have a different type on each side, and only the type is compared.
- **Normalisation:** differences that are expected and accepted are removed before counting. The rules are in [the normaliser](../configs/6586_folio_sierra_identified_normalise.py) and the ignored fields in [the config](../configs/6586_folio_sierra_identified.yaml).

10,476 pairs (1.0%) differ after normalisation. All but 4 of them are Visible pairs, which is 1.4% of the Visible pairs. No suppressed pair differs. Counts ignore the order of values within a field, which is covered in [Order of values](#order-of-values). Items and holdings are out of scope for this round.

## Sierra works with no FOLIO work

Production has 1,823,753 Sierra works. 1,039,077 of them are compared in this report. The other 784,676 have no FOLIO work with the same canonical id, so they are not compared:

- 456,769 are Deleted: 448,151 were deleted in Sierra and 8,618 are suppressed.
- 4 are Invisible, because the Scala transformer could not transform the record. None of them has a FOLIO record.
- 327,903 are Visible. They are in formats that were not migrated to FOLIO:

| Format | Works |
|---|---:|
| Archives and manuscripts | 241,084 |
| Pictures | 76,140 |
| Videos | 6,182 |
| Audio | 2,108 |
| Film | 2,070 |
| Digital Images | 166 |
| 3-D Objects | 152 |
| Journals | 1 |

The one journal is BMJ (b2043277x), which has 586 items in Sierra. FOLIO has a BMJ record with 586 items (`in00008808112`), but it has no Sierra number, so its work has a new canonical id. The record holds only a title and a language.

## FOLIO works with no production work

19 FOLIO works have a canonical id that production does not have:

- 11 are new records created in FOLIO, with no Sierra number.
- 4 are Sierra records that are unsuppressed in Sierra but have no work in production (b20646525, b2882068x, b10282993, b17268941).
- 2 are suppressed.
- 1 is a test record.
- 1 is the BMJ record described above.

## How to read the tables

Each row is one kind of difference, with the number of works it affects.

**Cause** says where the difference comes from:

- **Transformer:** the Sierra and FOLIO records hold the same data, and the two transformers handle it differently.
- **Source:** the FOLIO record differs from the Sierra record.
- **Stale production:** production holds a value from before a Scala fix, and would match after the Sierra work is transformed again.

**Reference** points to the PR description that names the difference, where there is one.

**Status** says whether the difference needs attention:

| Status | Meaning |
|:---:|---|
| ✅ | Explained and needs no action |
| ⚠️ | Needs investigation or a decision |
| 📝 | Changed in Sierra but not in FOLIO. Cataloguers may want to make the same change in FOLIO |

## Work type

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 3 | Visible in production, Deleted in FOLIO as suppressed (`pg58x66j`, `aqhcpsvh`, `anycpaqb`) | Source: the records were suppressed in Sierra on 28 September, migrated as suppressed, and unsuppressed in Sierra on 22 October, after the final migration. They are still suppressed in FOLIO (checked in both adapter stores on 23 November) | | 📝 |
| 1 | Invisible in production, Visible in FOLIO (`hzg28phe`) | Transformer: the record repeats a subfield ǂa that should appear once. Scala refuses to transform it, and Python uses the first | | ✅ |

## Contributors

Reference: [#3787](https://github.com/wellcomecollection/catalogue-pipeline/pull/3787).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 259 | Identifier scheme is `nlm-mesh` in production and `lc-names` in FOLIO. On 10 of them the contributor is also an Agent in production and a Person in FOLIO | Transformer: Scala reads the second indicator of an analytical 7xx entry as a thesaurus | #3787, analytical added entries | ✅ |
| 101 | `primary` flag differs | Source: FOLIO records were edited to keep one main entry where Sierra has several | | ✅ |
| 63 | Type differs, for example Concept in production and Person in FOLIO | Transformer: the same name is a contributor and a subject, with a different type on each. Scala gives the contributor the subject's type. Python gives both the more specific type | #3787, ontology type harmonisation | ✅ |
| 55 | Contributor has a different canonical id. Its identifier is made from the label, which has a backslash in production and a space in FOLIO | Source: the migration turned backslashes into spaces | | ✅ |
| 29 | Contributor has a different canonical id. Its identifier is made from the label, which has a double space in production | Transformer: Python strips whitespace around subfield values | #3787, subfield whitespace | ✅ |
| **3** | **Production has an `lc-names` identifier, FOLIO a label-derived one** | **Source: the Sierra ǂ9 local code became a second ǂ0 in FOLIO, and the transformer drops the identifier when a field has two** | | ⚠️ |
| 3 | Trailing full stop missing from the FOLIO label, for example `Kail, Robert V.` in production and `Kail, Robert V` in FOLIO | Source: the full stop was added in Sierra between 6 and 8 October and is not in FOLIO | | 📝 |
| 3 | Production has a contributor with an empty or `.` label | Source: the FOLIO record has no such field | | ✅ |
| 2 | Production has one more contributor, a duplicate with an `nlm-mesh` identifier | Transformer: a knock-on of the analytical entry misreading, which stops Scala merging the duplicate | #3787 names the misreading but not this effect | ✅ |

## Subjects

Reference: [#3766](https://github.com/wellcomecollection/catalogue-pipeline/pull/3766).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 80 | Label-derived identifier of a name concept differs, for example `lara rivera, jorge, (1961-)` in production and `lara rivera, jorge,` in FOLIO | Transformer: the sole concept of a name heading keeps its own identifier in Python | #3766, name concept identifiers | ✅ |
| 63 | Type differs, for example Concept in production and Person in FOLIO. These are the same 63 works as in contributors | Transformer: the same name is a contributor and a subject, with a different type on each. Scala keeps the subject's type. Python gives both the more specific type | #3787, ontology type harmonisation | ✅ |
| **49** | **Production has an `lc-subjects`, `lc-names` or `nlm-mesh` identifier, FOLIO a label-derived one** | **Source: the Sierra ǂ9 local code became a second ǂ0 in FOLIO, and the transformer drops the identifier when a field has two** | **#3766 calls it a data quality issue and leaves it open** | ⚠️ |
| 8 | Production label repeats the author's dates after the title, for example `Foucault, Michel, 1926-1984. Folie et déraison, 1926-1984.` | Transformer: Python drops a repeated subfield that duplicates an earlier value | #3766, duplicated subfield | ✅ |
| 1 | Production identifier reads `inuenza epidemic, 1918-1919`, and the Period has no range | Transformer: Scala strips `fl` from inside `Influenza` | #3766, period identifiers | ✅ |
| 1 | Both concepts of a subject have the identifier `otology - ear` in production. In FOLIO they have `otology` and `ear` | Transformer: a 650 with two ǂa, where Python gives each concept its own identifier | #3766, two ǂa in one 650 | ✅ |
| 1 | Subject has a different canonical id. Its identifier is made from the label, which has a backslash in production and a space in FOLIO | Source: the migration turned backslashes into spaces | | ✅ |

## Alternative titles

References: [#3646](https://github.com/wellcomecollection/catalogue-pipeline/pull/3646), [#3754](https://github.com/wellcomecollection/catalogue-pipeline/pull/3754).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 756 | Production title includes a control subfield. On 721 of them it is an LC Names URI, and on 35 a `880-` linkage at the start. On 3 of them FOLIO has one title fewer, because two titles become identical once the URI is removed | Stale production: these Sierra works were last transformed before the Scala fix of 6 October | #3754 | ✅ |
| 4 | Production title ends with `OCoLCm` | Source: the Sierra ǂ9 local code became a ǂ0 in FOLIO, which the transformer leaves out | | ✅ |

## Production

Reference: [#3729](https://github.com/wellcomecollection/catalogue-pipeline/pull/3729).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 1,723 | Date range differs, for example `1538 [colophon 1537]` is 1538 in production and 1537 to 1538 in FOLIO | Transformer: expected. Python uses a new period parser, which reads more dates and reads them more accurately than Scala's. A few forms are still read wrongly, such as a Hijri year next to the AD year (at least 27 works) | #3704, #3729 | ✅ |
| 950 | FOLIO has a date range, production has the date label only, for example `1975,` | Transformer: Python parses a catalogued date Scala cannot | #3729, period parser | ✅ |
| 459 | FOLIO has a date, production has none | Transformer: Python reads 008 dates that Scala rejects, with unknown digits such as `19uu` or a second date that does not fit the date type | #3729, 008 date parser | ✅ |
| 9 | The record has two date subfields (ǂc) in one 260 or 264, for example `[s.n.],` and `[199?]`. Production has only the first, with the range from 008. FOLIO has both, each with the range parsed from its own text, if any | Transformer: Scala cannot parse either date, so it falls back to 008. Python can parse at least one, so it keeps both | | ✅ |
| 47 | FOLIO date range starts before the year 1000, for example `[199]` becomes the year 199 and `905` the year 905. On 28 of them production has the right year from 008 | Transformer: expected. The catalogued date is truncated or mistyped, and Python reads it as written, where Scala declines it and falls back to 008. The parser cannot tell these apart: 3 are short Hebrew years, such as 686 for 5686, and one, `400?`, is a genuine early date. The records can be corrected in FOLIO | #3704 | ✅ |
| 23 | Production has a date range, FOLIO has the date label only, for example `[1584-1566?]` | Transformer: Python declines backwards ranges and ambiguous comma pairs | #3729, period parser | ✅ |
| 12 | FOLIO has a production event with a year, production has none | Source: the Sierra record has no 008, and the migration created one with the year | | ✅ |
| 7 | Production has a date `9999`, FOLIO has none | Transformer: Python does not read `9999` in 008 as a year | | ✅ |
| 1 | Production has `1981-1974`, FOLIO `1974-1981` | Transformer: Python reorders a backwards 008 range | | ✅ |

## Languages

Reference: [#3639](https://github.com/wellcomecollection/catalogue-pipeline/pull/3639).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 3,853 | Production has a language, FOLIO has none | Transformer: FOLIO reads 008, which names no language. Production reads the Sierra LANG field, which has a language | #3639, 008 uninformative | ✅ |
| **864** | **Production has a language, FOLIO has none** | **Transformer: Python reads 008, and the Sierra record has none. The migration created one that says `und`. Production reads the Sierra LANG field, which says English on 823 of them and another language on 41** | **#3639 flagged the earlier placeholder 008, which said `eng`** | ⚠️ |
| 573 | The two have different languages | Transformer: 008 and the Sierra LANG field name different languages | #3639, both set | ✅ |
| 382 | FOLIO has a language, production has none | Transformer: 008 has a language, the Sierra LANG field names none | #3639, LANG blank | ✅ |
| 14 | Production lists a language twice, once under an obsolete code (`scc` on 13, `snh` on 1) | Transformer: Python keeps one language per label and prefers the current code | | ✅ |
| 1 | FOLIO has Hebrew, production has none | Source: the Sierra record has no 008, and the migration created one that says `heb` | | ✅ |

## Collection path

Reference: [#3825](https://github.com/wellcomecollection/catalogue-pipeline/pull/3825).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 61 | Own segment differs, for example `IDC20007/25203` in production and `IDC20007/b20241951` in FOLIO | Transformer: FOLIO takes the own identifier from 099 or the b-number, Scala from 001 | #3825, own identifier | ✅ |
| 56 | Own segment differs, on works where the production host has the `(UkLW)` prefix. On 23 of them FOLIO also replaces slashes with `\|` | Transformer | #3825, own identifier and slashes | ✅ |
| 39 | FOLIO has a path, production has none | Transformer: FOLIO falls back to the b-number as the own identifier | #3825, own identifier | ✅ |
| 9 | Production has a path, FOLIO has none | Transformer: the host is another organisation's record, such as `(OCoLC)`, which FOLIO ignores | #3825, links to records we do not hold | ✅ |
| 2 | Root path `EPB/T/248` in production, `EPB\|T\|248` in FOLIO | Transformer: slashes inside an identifier become `\|` | #3825, slashes | ✅ |
| 1 | Host is an OCLC number in production and a b-number in FOLIO | Transformer: the 773 has both, and FOLIO uses the Wellcome one | #3825, links to records we do not hold | ✅ |

## Notes

Reference: [#3896](https://github.com/wellcomecollection/catalogue-pipeline/pull/3896).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 2 | Note text differs. Sierra has a correction that FOLIO lacks | Source: the Sierra record was edited after the migration (22 October and 5 November) | | 📝 |
| 1 | Production note ends with an item barcode, FOLIO's does not | Source: the FOLIO record does not have the barcode in that note | | ✅ |

## Relations

Reference: [#3775](https://github.com/wellcomecollection/catalogue-pipeline/pull/3775).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 8 | Production lists the same series twice, FOLIO once | Source: Sierra stores the title in two Unicode forms in 490 and 830, so they do not deduplicate. FOLIO stores both in NFC | #3775 describes the mixed Unicode forms but not this effect | ✅ |

## Title

Reference: [#3628](https://github.com/wellcomecollection/catalogue-pipeline/pull/3628).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 1 | `Metamorphosis.` in production, `Metamorphasis.` in FOLIO | Source: the spelling was corrected in Sierra on 18 November, after the migration | | 📝 |

## Merge candidates

Leaves aside the candidate every FOLIO work has for its own HRID.

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 2 | FOLIO has an EBSCO candidate, production has none | Transformer: the Sierra 001 is truncated (`24e`). Python reads the full identifier from 035 | | ✅ |
| 1 | FOLIO has a candidate for a digitised Sierra work, production has none | Source: the 776 link was removed from the Sierra record on 5 November. It links two different books, and the FOLIO record still has it | | 📝 |
| 1 | Production has a Miro candidate, FOLIO has none | Source: an 089 was added to the Sierra record on 28 October. It was added to the FOLIO record on the same day, but is no longer there | | 📝 |

## Genres

Reference: [#3667](https://github.com/wellcomecollection/catalogue-pipeline/pull/3667).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 1 | Production has `Hindi language - Dictionaries`, FOLIO `Hindi language` | Transformer: a 655 with two ǂa, where Python takes the first | #3667, two ǂa | ✅ |

## Format

Reference: [#3897](https://github.com/wellcomecollection/catalogue-pipeline/pull/3897).

| Works | Difference | Cause | Reference | Status |
|---:|---|---|---|:---:|
| 1 | Books in production, Journals in FOLIO | Source: the Sierra material type was changed on 29 October, after the migration. FOLIO reads 998 ǂd, which still holds the old value | | 📝 |

## Order of values

The comparison ignores the order of values within a field. On 31 works a field has the same values on both sides in a different order. These are not counted in the tables above.

| Works | Field | Difference | Cause | Status |
|---:|---|---|---|:---:|
| 22 | Languages | A different language comes first, for example Turkish then English in production, and English then Turkish in FOLIO | Transformer: the first language is the Sierra LANG field in production and the 008 language in FOLIO, and the two name different languages | ✅ |
| 9 | Notes | A copyright note and a related material note are in the opposite order | Source: both transformers list notes in the order of the record's fields. The Sierra record has field 787 before 542, and the FOLIO record has 542 before 787 | ✅ |

## After merging

A second comparison covers `works-denormalised`, the output of the matcher and merger, for all works in both pipelines, as fetched on 24 November 2026.

- **Population:** 2,268,882 works are in both pipelines. 789,162 are only in production, mostly [Sierra works with no FOLIO work](#sierra-works-with-no-folio-work). 273 are only in FOLIO.
- **Earlier differences are excluded:** a difference that already exists at `works-identified` is not counted, so what remains comes from merging. Parts of TEI manuscripts become works only in the merger, so all their differences are counted here.
- **Rules:** see [the config](../configs/6586_folio_prod_denormalised.yaml) and [the normaliser](../configs/6586_folio_prod_merged_normalise.py). Items, holdings and availabilities are ignored. Works of different types, and redirected works, are compared on type and redirect target only.
- **To reproduce:** run the commands listed in [merged_differences_6586.py](../merged_differences_6586.py).

349,214 works (15.4%) differ. Nearly all of them differ because the Sierra records for archives, pictures and audiovisual material were not migrated to FOLIO. The archive, METS and Miro works that merge with those records in production have nothing to merge with in FOLIO.

### Archive works

In production an archive work merges with the Sierra record for the same item. The Sierra record is also what connects it to its digitised copy (a METS work) and to Miro images.

| Works | Difference | Cause | Status |
|---:|---|---|:---:|
| 197,402 | The archive work no longer merges with a Sierra work. Nothing else in its merge group changes (194,579 Axiell, 2,751 Calm, 72 TEI) | The Sierra record was not migrated. It contributed items, which are out of scope here, and on some works the b number | ✅ |
| 29,622 | A Calm work loses its digitised copy, and the METS work becomes Invisible. Example: `a3js8n64` (RET/3/2/5/3, METS b24955590) | A METS work links to a b number. Axiell works had theirs imported ([platform#6525](https://github.com/wellcomecollection/platform/issues/6525)). These are the third-party archives kept in Calm, which have no b number and are not in Axiell | ⚠️ |
| 9,250 | An Axiell work loses its Miro images, which become 9,427 separate Visible works. In production they redirect to it through its Sierra record. Example: `b9qv8p7q` (Miro M0009975) | The Sierra record (b33331777) links to both works, and was not migrated | ⚠️ |
| 383 | An Axiell work loses its digitised copy, and the METS work becomes Invisible | The Axiell work and the METS work merge through the Sierra record. Without it they merge only if the Axiell record lists the b number, and these do not | ⚠️ |
| 101 | A METS work becomes Invisible, so the digitised copy is not in the catalogue. In production it redirects to its Sierra work, an archive record with no Axiell or Calm work | The Sierra work was not migrated | ⚠️ |

Most Axiell works still list the b number as an identifier, because it is in the Axiell record itself. 54,585 works no longer list it, so a b number search does not find them: all 32,373 Calm works, 22,138 Axiell works with no b number in the record, and 74 TEI works. 5,978 of the works that lose a digitised copy or Miro image also lose their thumbnail. 18 Axiell works lose both their digitised copy and their Miro images, and are counted once under each.

### Pictures and audiovisual works

These Sierra records are due to move to Axiell. Whether the Axiell records will carry the b number, which the METS merges depend on, is an open question in [RFC 090](https://github.com/wellcomecollection/docs/tree/main/rfcs/090-axiell-folio-sync#open-questions). The Miro merges depend on a link in the Sierra record, which the RFC does not cover.

| Works | Difference | Cause | Status |
|---:|---|---|:---:|
| 61,600 | A Miro work becomes a separate Visible work. In production it redirects to its Sierra work | The Sierra work was not migrated | ⚠️ |
| 8,401 | A METS work becomes Invisible, so the digitised copy is not in the catalogue. In production it redirects to its Sierra work | The Sierra work was not migrated | ⚠️ |

### Other differences

| Works | Difference | Cause | Status |
|---:|---|---|:---:|
| 2,016 | 672 physical records have two or more digitised records, and each pipeline merges a different one. The one that is not merged stays a separate Visible work | The merger takes the first digitised record that links to the physical record. The order comes from an unordered set, so the choice is arbitrary. The new Python merger sorts the works first, so its choice is stable ([platform#6861](https://github.com/wellcomecollection/platform/issues/6861)) | ✅ |
| 413 | TEI works differ in notes or physical description (412 parts of manuscripts, and 1 manuscript that was transformed again between the two fetches) | Stale FOLIO pipeline: its TEI works were transformed before the TEI transformer changes of 12 to 16 November | ✅ |
| 351 | Works with the same links in both pipelines merge differently. 141 of them are METS works that merge in FOLIO and stay Invisible in production | Matcher bug: the matcher graph is inconsistent or incomplete for these groups, in production for 338 works, in FOLIO for 8 and in both for 5. The new Python merger does not have this issue ([platform#6861](https://github.com/wellcomecollection/platform/issues/6861)) | ✅ |
| 129 | A work merges with a METS or TEI work that has a different canonical id in each pipeline. It is the same record on both sides | Expected: the two pipelines have separate id minter databases, so each minted its own id for a new record | ✅ |
| 37 | Two works are linked through a Sierra record that is Deleted in production and has no work in FOLIO. They are matched in FOLIO and not in production. 17 of them are METS works that become digitised copies of archive or library works | In production the Sierra record is Deleted, and the matcher never matches through a Deleted work. In FOLIO the record does not exist, so the two works match through its identifier. Are these merges wanted? | ⚠️ |
| 34 | The works, links and matcher graph are the same in both pipelines, but a METS work merges into a different library work (30), or a different thumbnail is chosen (4) | Two works qualify equally and the merger takes the first, from the same unordered set as in the first row | ✅ |
| 24 | A merge group includes a work that is in only one pipeline, so the rest of the group merges differently | 22 works are in groups with a Sierra work that was not migrated. 2 are in a group with a FOLIO work that has no work in production | ✅ |
| 16 | A work merges differently because of a difference described above | BMJ, `hzg28phe` and b17268941, the three suppressed works, the two EBSCO candidates, the Miro link on b20461896 and the 776 link on b12720604 | ✅ |
| 10 | A part of a TEI manuscript belongs to a different manuscript in each pipeline, for example `a4b3d3d9` to `manuscript_15781` in production and `manuscript_15782` in FOLIO | Source: in each case two TEI manuscripts use the same part id (`WMS_Arabic_134` in the example), so both produce the same work and one overwrites the other | ⚠️ |
| 4 | Two library works share a Miro image, and production gives one work's digitised copy to the other | Stale production: the works were last merged on 3 October, before the merger change of 14 October ([#3768](https://github.com/wellcomecollection/catalogue-pipeline/pull/3768)) | ✅ |
| 3 | A Sierra work redirects to a different TEI work in each pipeline (`gwzjvxhb`) | Source: two TEI manuscripts, `Tamil_3` and `Tamil_ MS 3`, link to the same b number | ⚠️ |
| 3 | A contributor on a part of a TEI manuscript has a different canonical id (`af9gjz5y`, `ahrmmhjn`, `anhw29e2`) | Expected: separate id minter databases, as above | ✅ |
| 1 | A part of a TEI manuscript is Deleted in production and Visible in FOLIO (`wwqjyg5n`) | Known merger limitation: another manuscript once used this part id and still lists it as removed, so the part is Deleted or Visible depending on which manuscript was merged last | ✅ |

## Open questions

| Field | Works | Question |
|---|---:|---|
| Subjects, contributors | 50 | **Two ǂ0 in one field.** The work loses its authority identifier in FOLIO. The records could be corrected in FOLIO, or the transformer could pick the ǂ0 that looks like an authority identifier. |
| Languages | 864 | **No language on records with no 008 in Sierra.** The migration gave them an 008 that says `und`, so the work has no language in FOLIO. This may be deliberate. Sierra's LANG field says English on 823 of them, which looks like a default: 710 have no cataloguing date. The other 41 have another language, such as Italian or Latin, and could be corrected in 008 by hand. To confirm with the migration team. Falling back to 998 ǂf in the transformer is not a good fix, because nobody maintains 998 in FOLIO, so a language removed from 008 would still show on the work. |
| Several | 12 | **Changes made in Sierra that are not in FOLIO.** These are the 📝 rows: three works unsuppressed (b16641437, b33972230, b33972199), a title spelling (b33853137), two notes (b19689718, b14587452), a material type (b16082497), a wrong 776 link removed (b12720604), a Miro link added (b20461896) and three trailing full stops (b10823803, b10001566, b1217967x). Should the same changes be made in FOLIO? |
| Sierra works with no FOLIO work | 1 | **A journal that lost its record in the migration.** The FOLIO record for BMJ (`in00008808112`, created on 14 October) has its 586 items but only a title and a language. It has no Sierra number (b2043277x), so the work gets a new canonical id and the current work page would stop working. Can the full record be loaded again? |
| Other identifiers | all | **Seven-digit Sierra identifier.** Every Sierra work lists it (for example `1211199`) and no FOLIO work does. A FOLIO work lists only the full system number (`b12111995`). The seven-digit number is searchable in production today: searching for `1211199` returns that work first. Archive works that merge with a Sierra work list it as well, and lose it in FOLIO. This difference is removed by normalisation, so it is in none of the tables above. Is the seven-digit form still needed? |
| After merging | 29,622 | **Third-party archives lose their digitised copy.** They are kept in Calm, with no b number for the METS work to link to, and are not in Axiell. How should they get one? |
| After merging | 9,250 | **Archive works lose their Miro images.** Only the Sierra record holds the link. Where should it be kept? |
| After merging | 70,001 | **Pictures and audiovisual works.** 61,600 Miro images are separate works and 8,401 digitised copies are not in the catalogue. Is that acceptable until these records are in Axiell, and how will the links be restored then? |

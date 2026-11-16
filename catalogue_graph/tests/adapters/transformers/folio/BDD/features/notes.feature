Feature: notes (MARC 5xx and 787)
  Notes come from the standard MARC note fields, plus the local notes in 591
  and 593 and the related material in 787. Each field becomes one note whose
  contents are its subfields joined with a space, leaving out ǂ5. A ǂu that is
  a URL becomes a link.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (SierraNotesTest.scala), including its test data, so the two
  can be compared directly.

  https://www.loc.gov/marc/bibliographic/bd5xx.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: Notes are extracted from all note fields
    Given the MARC record has a 500 field with subfield "a" value "general note a"
    And the MARC record has a 501 field with subfield "a" value "general note b"
    And the MARC record has a 502 field with subfield "a" value "dissertation note"
    And the MARC record has a 504 field with subfield "a" value "bib info a"
    And the MARC record has a 505 field with subfield "a" value "contents note"
    And the MARC record has a 506 field with subfield "a" value "typical terms of use"
    And the MARC record has a 508 field with subfield "a" value "credits note a"
    And the MARC record has a 510 field with subfield "a" value "references a"
    And the MARC record has a 511 field with subfield "a" value "credits note b"
    And the MARC record has a 514 field with subfield "a" value "Completeness:"
    And the MARC record has a 518 field with subfield "a" value "time and place note"
    And the MARC record has a 524 field with subfield "a" value "cite as note"
    And the MARC record has a 533 field with subfield "a" value "reproduction a"
    And the MARC record has a 534 field with subfield "a" value "reproduction b"
    And the MARC record has a 535 field with subfield "a" value "location of original note"
    And the MARC record has a 536 field with subfield "a" value "funding information"
    And the MARC record has a 540 field with subfield "a" value "terms of use"
    And the MARC record has a 542 field with subfield "a" value "copyright a"
    And the MARC record has a 544 field with subfield "a" value "related material a"
    And the MARC record has a 545 field with subfield "a" value "bib info b"
    And the MARC record has a 546 field with subfield "a" value "Marriage certificate: German; Fraktur."
    And the MARC record has a 547 field with subfield "a" value "general note c"
    And the MARC record has a 562 field with subfield "a" value "general note d"
    And the MARC record has a 563 field with subfield "a" value "binding info note"
    And the MARC record has a 581 field with subfield "a" value "publications b"
    And the MARC record has a 585 field with subfield "a" value "exhibitions"
    And the MARC record has a 586 field with subfield "a" value "awards"
    And the MARC record has a 591 field with subfield "a" value "A general, unspecified note"
    And the MARC record has a 593 field with subfield "a" value "copyright b"
    When I transform the MARC record
    Then the work has 29 notes with contents:
      | general note a                         |
      | general note b                         |
      | dissertation note                      |
      | bib info a                             |
      | contents note                          |
      | typical terms of use                   |
      | credits note a                         |
      | references a                           |
      | credits note b                         |
      | Completeness:                          |
      | time and place note                    |
      | cite as note                           |
      | reproduction a                         |
      | reproduction b                         |
      | location of original note              |
      | funding information                    |
      | terms of use                           |
      | copyright a                            |
      | related material a                     |
      | bib info b                             |
      | Marriage certificate: German; Fraktur. |
      | general note c                         |
      | general note d                         |
      | binding info note                      |
      | publications b                         |
      | exhibitions                            |
      | awards                                 |
      | A general, unspecified note            |
      | copyright b                            |
    And the work has 29 notes with note_type.label:
      | Notes                      |
      | Notes                      |
      | Dissertation note          |
      | Bibliographic information  |
      | Contents                   |
      | Terms of use               |
      | Creator/production credits |
      | References note            |
      | Creator/production credits |
      | Lettering note             |
      | Time and place note        |
      | Reference                  |
      | Reproduction note          |
      | Reproduction note          |
      | Location of original       |
      | Funding information        |
      | Terms of use               |
      | Copyright note             |
      | Related material           |
      | Biographical note          |
      | Language note              |
      | Notes                      |
      | Notes                      |
      | Binding detail             |
      | Publications note          |
      | Exhibitions note           |
      | Awards note                |
      | Notes                      |
      | Copyright note             |

  Scenario: Every repeat of a note field gives a note
    Given the MARC record has a 500 field with subfield "a" value "note a"
    And the MARC record has another 500 field with subfield "a" value "note b"
    When I transform the MARC record
    Then the work has 2 notes with contents:
      | note a |
      | note b |

  Scenario: Fields that are not note fields give no notes
    Given the MARC record has a 100 field with subfield "a" value "not a note"
    And the MARC record has a 530 field with subfield "a" value "not a note"
    When I transform the MARC record
    Then there are no notes

  Scenario: HTML in a note is preserved
    Given the MARC record has a 500 field with subfield "a" value "<p>note</p>"
    When I transform the MARC record
    Then the only note has the contents "<p>note</p>"

  Scenario: The subfields of one field are joined into one note
    Given the MARC record has a 500 field with subfield "a" value "1st part." and subfield "b" value "2nd part." and subfield "c" value "3rd part."
    When I transform the MARC record
    Then the only note has the contents "1st part. 2nd part. 3rd part."

  Scenario: Separate fields are not joined
    Given the MARC record has a 500 field with subfield "a" value "1st note."
    And the MARC record has another 500 field with subfield "a" value "2nd note."
    When I transform the MARC record
    Then the work has 2 notes with contents:
      | 1st note. |
      | 2nd note. |

  Scenario: The first indicator of 535 picks the location note type
    Given the MARC record has a 535 field with indicators "1" "" with subfield "a" value "The originals are in Oman"
    And the MARC record has another 535 field with indicators "2" "" with subfield "a" value "The duplicates are in Denmark"
    When I transform the MARC record
    Then there are 2 notes
    And the 1st note has the note_type.label "Location of original"
    And the 1st note has the contents "The originals are in Oman"
    And the 2nd note has the note_type.label "Location of duplicates"
    And the 2nd note has the contents "The duplicates are in Denmark"

  Scenario: An ownership note needs a 561 first indicator of 1
    Given the MARC record has a 561 field with indicators "1" "" with subfield "a" value "Provenance: one plate in the set of plates"
    And the MARC record has another 561 field with indicators "0" "" with subfield "a" value "Purchased from John Smith on 01/01/2001"
    And the MARC record has another 561 field with subfield "a" value "Private contact details for John Smith"
    When I transform the MARC record
    Then the only note has the note_type.label "Ownership note"
    And the only note has the contents "Provenance: one plate in the set of plates"

  # The Scala builds every shared note tag with and without a ǂ5 and asserts
  # the notes are the same, without setting indicators, so its 561 gives no
  # note either way. Here each tag asserts its contents instead, and the first
  # indicator is "1" so that 561 gives a note to assert on too.
  Scenario Outline: Subfield ǂ5 is left out of every note field
    Given the MARC record has a <tag> field with indicators "1" "" with subfield "a" value "Main bit." and subfield "5" value "UkLW"
    When I transform the MARC record
    Then the only note has the contents "Main bit."

    Examples:
      | tag |
      | 500 |
      | 501 |
      | 502 |
      | 504 |
      | 505 |
      | 506 |
      | 508 |
      | 510 |
      | 511 |
      | 514 |
      | 515 |
      | 518 |
      | 524 |
      | 525 |
      | 533 |
      | 534 |
      | 535 |
      | 536 |
      | 540 |
      | 542 |
      | 544 |
      | 545 |
      | 546 |
      | 547 |
      | 550 |
      | 561 |
      | 562 |
      | 563 |
      | 580 |
      | 581 |
      | 585 |
      | 586 |
      | 588 |

  Scenario: The barcode in 591 ǂ9 is left out
    Given the MARC record has a 591 field with subfield "z" value "Copy 1." and subfield "e" value "Note: The author's presentation inscription on verso of 2nd leaf." and subfield "9" value "X8253"
    When I transform the MARC record
    Then the only note has the note_type.label "Notes"
    And the only note has the contents "Copy 1. Note: The author's presentation inscription on verso of 2nd leaf."

  Rule: 787 is related material, with catalogue references turned into search links

    Scenario: A 787 gives a related material note
      # This example is based on b33032440
      Given the MARC record has a 787 field with subfield "t" value "Daily telegraph." and subfield "g" value "1989"
      When I transform the MARC record
      Then the only note has the note_type.label "Related material"
      And the only note has the contents "Daily telegraph. 1989"

    Scenario: A b-number in ǂw becomes a search link
      # This example is based on b33039136
      Given the MARC record has a 787 field with subfield "i" value "Complemented by (work):" and subfield "t" value "Depression ain't the sads." and subfield "w" value "(UkLW)b33039112"
      When I transform the MARC record
      Then the only note has the contents "Complemented by (work): Depression ain't the sads. (<a href="https://wellcomecollection.org/search/works?query=b33039112">b33039112</a>)"

    Scenario: A ǂw that is not a b-number is left as text
      # This example is based on b15900976
      Given the MARC record has a 787 field with subfield "s" value "Times (London, England :  1788)." and subfield "g" value "May 27, 2004." and subfield "w" value "(OCoLC)6967919"
      When I transform the MARC record
      Then the only note has the contents "Times (London, England :  1788). May 27, 2004. (OCoLC)6967919"

  # Scala's "non-visual material" case; any material type other than "k" behaves the same

  Scenario: A 514 is a lettering note
    Given the MARC record has a 514 field with subfield "a" value "Lettering continues: Comment va  le malade? Hélas Monsieur, il est mort ce matin à six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."
    And the MARC record has a 246 field with indicators "" "6" with subfield "a" value "Le médecin et la garde malade. H.D. ..."
    When I transform the MARC record
    Then the only note has the note_type.label "Lettering note"
    And the only note has the contents "Lettering continues: Comment va  le malade? Hélas Monsieur, il est mort ce matin à six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."

  # Known divergences from the Scala.

  # The Scala suppressed 591 ǂ9, which held the item barcode in Sierra. The
  # FOLIO migration moved every 591 ǂ9 to ǂ0, so both are left out.
  Scenario: The barcode in 591 ǂ0 is left out
    Given the MARC record has a 591 field with subfield "z" value "Copy 1." and subfield "e" value "Note: The author's presentation inscription on verso of 2nd leaf." and subfield "0" value "X8253"
    When I transform the MARC record
    Then the only note has the contents "Copy 1. Note: The author's presentation inscription on verso of 2nd leaf."

  # The Scala gave no note from 514 on Pictures (Sierra material type "k"),
  # where the 514 continued the lettering instead. Pictures are not in FOLIO.
  # See wellcomecollection/platform#6570.
  Scenario: A 514 on visual material is still a lettering note
    Given the MARC record has a 998 field with subfield "d" value "k  "
    And the MARC record has a 514 field with subfield "a" value "Lettering continues: Comment va  le malade? Hélas Monsieur, il est mort ce matin à six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."
    And the MARC record has a 246 field with indicators "" "6" with subfield "a" value "Le médecin et la garde malade. H.D. ..."
    When I transform the MARC record
    Then the only note has the note_type.label "Lettering note"

  # The Scala left a 787 ǂu as text.
  Scenario: A 787 ǂu that is a URL becomes a link
    Given the MARC record has a 787 field with subfield "t" value "Daily telegraph." and subfield "u" value "https://example.org/telegraph"
    When I transform the MARC record
    Then the only note has the contents "Daily telegraph. <a href="https://example.org/telegraph">https://example.org/telegraph</a>"

  # The Scala removed five sentences about the Codebreakers digitisation
  # programme. They occur only on Sierra archive bibs, none of which is in FOLIO.
  Scenario: A Codebreakers sentence is kept
    Given the MARC record has a 535 field with indicators "1" "" with subfield "a" value "Keep me. This catalogue is held by the Wellcome Library as part of Codebreakers: Makers of Modern Genetics. And keep me too."
    When I transform the MARC record
    Then the only note has the contents "Keep me. This catalogue is held by the Wellcome Library as part of Codebreakers: Makers of Modern Genetics. And keep me too."

  # Not in the Scala tests.

  # The Sierra transformer never produced notes from 541, 583 or 584, although
  # the shared MARC extractor does for Axiell.
  Scenario: 541, 583 and 584 give no notes
    Given the MARC record has a 541 field with indicators "1" "" with subfield "a" value "Donation; DrugScope; 2016; 2266 (SA/DRS)."
    And the MARC record has a 583 field with indicators "1" "" with subfield "a" value "Appraised"
    And the MARC record has a 584 field with indicators "1" "" with subfield "a" value "Further accruals are expected"
    When I transform the MARC record
    Then there are no notes

  Scenario: Surrounding whitespace is stripped from a note
    Given the MARC record has a 591 field with subfield "z" value "Copy 1" and subfield "e" value "Note: 1912 printing "
    When I transform the MARC record
    Then the only note has the contents "Copy 1 Note: 1912 printing"

  Scenario: Notes keep record order across shared and local note fields
    Given the MARC record has a 500 field with subfield "a" value "first"
    And the MARC record has a 591 field with subfield "a" value "second"
    And the MARC record has a 593 field with subfield "a" value "third"
    And the MARC record has a 787 field with subfield "t" value "fourth"
    And the MARC record has another 500 field with subfield "a" value "fifth"
    When I transform the MARC record
    Then the work has 5 notes with contents:
      | first  |
      | second |
      | third  |
      | fourth |
      | fifth  |

  Scenario: The UkLW prefix in 787 ǂw is matched regardless of case
    Given the MARC record has a 787 field with subfield "w" value "(ukLw) b33039112"
    When I transform the MARC record
    Then the only note has the contents "(<a href="https://wellcomecollection.org/search/works?query=b33039112">b33039112</a>)"

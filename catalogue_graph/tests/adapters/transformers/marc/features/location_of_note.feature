Feature: Extract Location notes from MARC 535
  Indicator 1 determines note type: "2" yields "Location of duplicates",
  anything else yields "Location of original".

  Background:
    Given a valid MARC record

  Rule: Indicator 1 determines the note type
    Scenario: Default indicator produces Location of original
      Given the MARC record has a 535 field with subfield "a" value "British Library, London."
      When I transform the MARC record
      Then the only note has the note_type.label "Location of original"
      And the only note has the contents "British Library, London."

    Scenario: Indicator 1 = "1" produces Location of original
      Given the MARC record has a 535 field with indicators "1" "" with subfield "a" value "Wellcome Collection, London."
      When I transform the MARC record
      Then the only note has the note_type.label "Location of original"
      And the only note has the contents "Wellcome Collection, London."

    Scenario: Indicator 1 = "2" produces Location of duplicates
      Given the MARC record has a 535 field with indicators "2" "" with subfield "a" value "National Archives, Kew."
      When I transform the MARC record
      Then the only note has the note_type.label "Location of duplicates"
      And the only note has the contents "National Archives, Kew."

  Rule: Standard note behaviour applies
    Scenario: No 535 fields present
      When I transform the MARC record
      Then there are no notes

    Scenario: Multiple 535 fields with different indicators
      Given the MARC record has a 535 field with indicators "1" "" with subfield "a" value "Original held at Wellcome Collection."
      And the MARC record has another 535 field with indicators "2" "" with subfield "a" value "Duplicate at British Library."
      When I transform the MARC record
      Then there are 2 notes
      And the 1st note has the note_type.label "Location of original"
      And the 1st note has the contents "Original held at Wellcome Collection."
      And the 2nd note has the note_type.label "Location of duplicates"
      And the 2nd note has the contents "Duplicate at British Library."

    Scenario: Globally suppressed subfield $5 is excluded
      Given the MARC record has a 535 field with subfield "a" value "Wellcome Collection." and subfield "5" value "UkLW"
      When I transform the MARC record
      Then the only note has the contents "Wellcome Collection."

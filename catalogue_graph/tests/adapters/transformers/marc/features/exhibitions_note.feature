Feature: Extract Exhibitions notes from MARC 585 $a
  notes are derived from every non-empty 585 subfield in order of appearance.

  Background:
    Given a valid MARC record

  Rule: When there are no valid 585 fields, there are no notes
    Scenario: No 585 fields present
      When I transform the MARC record
      Then there are no notes

    Scenario: Subfield contents are concatenated
      Given the MARC record has a 585 field with subfields:
        | code | value        |
        | a    | First note   |
        | b    | Second note  |
        | c    | Another note |
      When I transform the MARC record
      Then the only note has the contents "First note Second note Another note"

    Scenario: Empty $a subfields are ignored
      Given the MARC record has a 585 field with subfield "a" value ""
      And the MARC record has another 585 field with subfield "a" value "   "
      When I transform the MARC record
      Then there are no notes

    Scenario: Globally suppressed subfield $5 is ignored
      Given the MARC record has a 585 field with subfield "a" value "Some note" and subfield "5" value "Suppressed"
      When I transform the MARC record
      Then the only note has the contents "Some note"

  Rule: an exhibitions note is created for each valid 585 field
    Scenario: Single 585 $a
      Given the MARC record has a 585 field with subfield "a" value "Exhibited at the Royal Academy, London, 2020."
      When I transform the MARC record
      Then the only note has the contents "Exhibited at the Royal Academy, London, 2020."

    Scenario: Multiple Exhibitions notes
      Given the MARC record has a 585 field with subfield "a" value "Exhibited at the Royal Academy, London, 2020."
      And the MARC record has another 585 field with subfield "a" value "Shown at the Metropolitan Museum of Art, New York, 2019."
      And the MARC record has another 585 field with subfield "a" value "Displayed at the Louvre, Paris, 2018."
      When I transform the MARC record
      Then the work has 3 notes with contents:
        | Exhibited at the Royal Academy, London, 2020.            |
        | Shown at the Metropolitan Museum of Art, New York, 2019. |
        | Displayed at the Louvre, Paris, 2018.                    |

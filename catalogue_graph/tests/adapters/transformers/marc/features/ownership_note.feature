Feature: Extract Ownership notes from MARC 561
  An ownership note is only produced when indicator 1 is "1" (not private).

  Background:
    Given a valid MARC record

  Rule: Indicator 1 must be "1" to produce a note
    Scenario: Indicator 1 = "1" produces an ownership note
      Given the MARC record has a 561 field with indicators "1" "" with subfield "a" value "Some ownership note."
      When I transform the MARC record
      Then the only note has the note_type.label "Ownership note"
      And the only note has the contents "Some ownership note."

    Scenario: No indicator produces no note
      Given the MARC record has a 561 field with subfield "a" value "Private provenance."
      When I transform the MARC record
      Then there are no notes

    Scenario: Indicator 1 = "0" produces no note
      Given the MARC record has a 561 field with indicators "0" "" with subfield "a" value "Private provenance."
      When I transform the MARC record
      Then there are no notes

    Scenario: Indicator 1 = " " produces no note
      Given the MARC record has a 561 field with indicators " " "" with subfield "a" value "Some provenance."
      When I transform the MARC record
      Then there are no notes
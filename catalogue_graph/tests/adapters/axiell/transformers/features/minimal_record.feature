Feature: The bare minimum MARC record for an item
  A minimal MARC record from Axiell is expected to have an identifier,
  a modified time, and a title.
  Axiell records generate Invisible works in the catalogue pipeline.

  Scenario: A minimal MARC record from Axiell
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 005 field with the value 20260101094530.0
    And the MARC record has a 245 field with subfield "a" value "How to avoid huge ships"
    When I transform the MARC record
    Then the work is invisible
    And the work has the identifier "Work[axiell-priref/abc123]"
    And the work's source modified time is 2026-01-01T09:45:30Z
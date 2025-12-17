Feature: The bare minimum MARC record for an item

  Scenario: Extracting the identifier from the 001 field
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 005 field with the value 20260101094530.0
    When I transform the MARC record
    Then the work is invisible
    And the work has the identifier "Work[mimsy-reference/abc123]"
    And the work's source modified time is 2026-01-01T09:45:30Z
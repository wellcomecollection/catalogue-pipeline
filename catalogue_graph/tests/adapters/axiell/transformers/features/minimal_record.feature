Feature: The bare minimum MARC record for an item

  Scenario: Extracting the identifier from the 001 field
    Given a MARC record with field 001 "abc123"
    When I transform the MARC record
    Then the work is invisible
    And the work has the identifier "Work[mimsy-reference/abc123]"
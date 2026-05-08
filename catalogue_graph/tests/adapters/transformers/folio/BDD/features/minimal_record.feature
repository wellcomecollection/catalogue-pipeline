Feature: The bare minimum MARC record for an item
  A minimal MARC record from Folio is expected to have an identifier.
  Folio records generate Visible works in the catalogue pipeline.

  Scenario: A minimal MARC record from Folio
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 907 field with subfield "a" value "def456"
    When I transform the MARC record
    Then the work is visible
    And the work has the identifier "Work[folio-instance/abc123]"
    And the work has the predecessor identifier "Work[sierra-system-number/def456]"
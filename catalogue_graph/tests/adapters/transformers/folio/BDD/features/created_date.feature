Feature: created date
  No FOLIO work has a created date.

  Only the Miro transformer sets a work's created date, from the artwork date
  of V-collection images. The Sierra transformer this replaces never sets it,
  and the merger keeps the target work's data, so a Sierra work that absorbs
  a Miro image has no created date either. The website does not display the
  field. The dates of creation a MARC record carries (008 Date1, 260 and
  264 ǂc) already reach the work as production events.

  https://github.com/wellcomecollection/platform/issues/6563

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A minimal record has no created date
    When I transform the MARC record
    Then the work's created date is absent

  Scenario: A date of production in 264 ǂc gives a production event, not a created date
    Given the MARC record has a 264 field with indicators " " "0" with subfield "c" value "1805"
    When I transform the MARC record
    Then the work's created date is absent
    And there is 1 production

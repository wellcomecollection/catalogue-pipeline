Feature: FOLIO item identifiers
  Each FOLIO item is identified by its item UUID. An item carried over from
  Sierra keeps its Sierra item number in formerIds, and that number becomes the
  item's predecessor so the id-minter gives it the canonical id the Sierra item
  held (platform#6616).

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"
    And a FOLIO item "a"

  Scenario: An item is identified by its FOLIO item UUID
    When I transform the MARC record
    Then item "a" has the source identifier "Item[folio-item/a]"

  Scenario: An item migrated from Sierra has its Sierra item number as predecessor
    Given that item has the former ids "i16858153"
    When I transform the MARC record
    Then item "a" has the predecessor "Item[sierra-system-number/i16858153]"

  Scenario: An item repeating its Sierra item number has it as predecessor
    Given that item has the former ids "i1685813x, i1685813x"
    When I transform the MARC record
    Then item "a" has the predecessor "Item[sierra-system-number/i1685813x]"

  Scenario: An item created in FOLIO has no predecessor
    When I transform the MARC record
    Then item "a" has no predecessor

  Scenario: An item with more than one Sierra item number fails the transform
    Given that item has the former ids "i16858153, i16858128"
    Then transforming the record with its items raises ValueError "Multiple distinct instances of formerIds: ['i16858128', 'i16858153']"

  Scenario: An item whose former id is not a Sierra item number fails the transform
    Given that item has the former ids "b13119382"
    Then transforming the record with its items raises ValueError "Predecessor identifier does not match Sierra item number format: 'b13119382'"

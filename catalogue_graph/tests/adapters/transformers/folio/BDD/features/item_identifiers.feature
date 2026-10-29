Feature: FOLIO item identifiers
  Each FOLIO item is identified by its item UUID. An item carried over from
  Sierra keeps its Sierra item number in formerIds. That number is emitted as an
  other identifier so the item can still be found by it (platform#6614), and as
  the item's predecessor so the id-minter gives it the canonical id the Sierra
  item held (platform#6616).

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"
    And a FOLIO item "a"

  Scenario: An item created in FOLIO is identified by its item UUID alone
    When I transform the MARC record
    Then the only item has the source identifier value "a"
    And it has the source identifier type "folio-item"
    And it has the source identifier ontology type "Item"
    And it has 0 other identifiers
    And it has no predecessor identifier

  Scenario Outline: An item migrated from Sierra carries its Sierra item number
    Given that item has the former ids "<former_ids>"
    When I transform the MARC record
    Then the only item has the source identifier value "a"
    And it has the predecessor identifier type "sierra-system-number"
    And it has the predecessor identifier value "<item_number>"
    And it has 1 other identifier
    And its only other identifier has the identifier type "sierra-system-number"
    And it has the value "<item_number>"

    Examples:
      | former_ids           | item_number |
      | i16858153            | i16858153   |
      | i1685813x, i1685813x | i1685813x   |

  Scenario Outline: An item whose former ids are not one Sierra item number fails the transform
    Given that item has the former ids "<former_ids>"
    Then transforming the record raises ValueError "<message>"

    Examples:
      | former_ids           | message                                                                         |
      | i16858153, i16858128 | Multiple distinct instances of formerIds: ['i16858128', 'i16858153']            |
      | b13119382            | Predecessor identifier does not match Sierra item number format: 'b13119382' |

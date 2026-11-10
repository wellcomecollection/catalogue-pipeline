Feature: uncatalogued instances (MARC 590)
  Ported from the "awaiting cataloguing" cases of the Scala SierraItemsOnOrder
  transformer (SierraItemsOnOrderTest.scala). Sierra builds the placeholder
  item from order records, which were not migrated to FOLIO. Instead, Collection
  Information added a 590 note starting "UNCATALOGUED" to every acquisitions bib
  with no items before migration, and removes it on cataloguing (platform#6778).

  Deliberate divergences: the received date and order status are not available,
  so every uncatalogued instance gets "Awaiting cataloguing" rather than
  "Ordered on DATE"; there is no CAT DATE guard, as the note's removal plays
  that role; and the item is dropped as soon as the instance has any item,
  including one suppressed from discovery, so a stale note cannot bring it back.

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  # Taken from Sierra b3393020x, FOLIO 3c17c9a9-8115-5647-b2fa-e9d847778e4b.

  Scenario Outline: An instance with the note and no items is awaiting cataloguing
    Given the MARC record has a 590 field with subfield "a" value "<note>"
    When I transform the MARC record
    Then the only item has the id type "Unidentifiable"
    And it has no title
    And its only location has the location type "on-order"
    And it has the label "Awaiting cataloguing for Wellcome Collection"
    And it has 0 access conditions

    Examples:
      | note                                |
      | UNCATALOGUED: Printed and published |
      | UNCATALOGUED: Ephemera              |
      | UNCATALOGUED: Zine                  |
      | UNCATALOGUED: EPB                   |

  Scenario: The note is matched after stripping whitespace
    Given the MARC record has a 590 field with subfield "a" value " UNCATALOGUED: Zine "
    When I transform the MARC record
    Then the only item has the id type "Unidentifiable"
    And it has no title
    And its only location has the location type "on-order"
    And it has the label "Awaiting cataloguing for Wellcome Collection"
    And it has 0 access conditions

  Scenario: An instance with the note and no inventory record is awaiting cataloguing
    Given the MARC record has a 590 field with subfield "a" value "UNCATALOGUED: Zine"
    And the instance has no inventory record
    When I transform the MARC record
    Then the only item has the id type "Unidentifiable"
    And its only location has the location type "on-order"
    And it has the label "Awaiting cataloguing for Wellcome Collection"

  Scenario: An instance with the note and an item shows only the item
    Given the MARC record has a 590 field with subfield "a" value "UNCATALOGUED: Zine"
    And a FOLIO item "a"
    When I transform the MARC record
    Then the only item has the id type "Identifiable"

  Scenario: An instance with the note and only a suppressed item shows nothing
    Given the MARC record has a 590 field with subfield "a" value "UNCATALOGUED: Zine"
    And a FOLIO item "a"
    And that item is suppressed from discovery
    When I transform the MARC record
    Then there are no items

  Scenario: An instance without a 590 has no items
    When I transform the MARC record
    Then there are no items

  Scenario Outline: A 590 that is not the uncatalogued note gives no item
    Given the MARC record has a 590 field with subfield "a" value "<note>"
    When I transform the MARC record
    Then there are no items

    Examples:
      | note                                     |
      | Stub record.                             |
      | Preliminary record                       |
      | Uncatalogued: Zine                       |
      | Was UNCATALOGUED: Zine until catalogued  |

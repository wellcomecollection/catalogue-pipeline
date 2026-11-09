Feature: FOLIO item titles
  An item's title tells it apart from the other items on the work. A
  cataloguer-written volume title is preferred. Without one, the title is
  generated from the copy number as "Copy N", as the Sierra transformer does
  (platform#6610).

  A generated title only helps if it distinguishes items, so once every item
  on the work is built, identical generated titles are removed from all of
  them. A lone item is the simplest case: its "Copy 1" says nothing.

  Mirrors the title cases in SierraItemsTest. Sierra could hold several volume
  titles on one item and took the first with a warning. The migration joined
  those into one FOLIO volume, separated by semicolons, which is shown whole.

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A volume title is preferred to the copy number
    Given a FOLIO item "a"
    And that item has the volume "Vol. 1"
    And that item has the copy number "1"
    When I transform the MARC record
    Then the only item has the title "Vol. 1"

  Scenario: An item with neither a volume nor a copy number has no title
    Given a FOLIO item "a"
    When I transform the MARC record
    Then the only item has the source identifier value "a"
    And it has no title

  Scenario: A lone item loses its generated title
    Given a FOLIO item "a"
    And that item has the copy number "1"
    When I transform the MARC record
    Then the only item has the source identifier value "a"
    And it has no title

  Scenario: Identical generated titles on every item are removed
    Given a FOLIO item "a"
    And that item has the copy number "1"
    And a FOLIO item "b"
    And that item has the copy number "1"
    And a FOLIO item "c"
    And that item has the copy number "1"
    When I transform the MARC record
    Then the work has 3 items with no title

  Scenario: Differing generated titles are kept
    Given a FOLIO item "a"
    And that item has the copy number "1"
    And a FOLIO item "b"
    And that item has the copy number "2"
    And a FOLIO item "c"
    And that item has the copy number "4"
    When I transform the MARC record
    Then the work has 3 items with title:
      | Copy 1 |
      | Copy 2 |
      | Copy 4 |

  Scenario: The same volume title on every item is kept
    Given a FOLIO item "a"
    And that item has the volume "Impression"
    And a FOLIO item "b"
    And that item has the volume "Impression"
    And a FOLIO item "c"
    And that item has the volume "Impression"
    When I transform the MARC record
    Then the work has 3 items with title:
      | Impression |
      | Impression |
      | Impression |

  Scenario: A mix of volume and generated titles is left alone
    Given a FOLIO item "a"
    And that item has the volume "Vol. 1"
    And a FOLIO item "b"
    And that item has the copy number "1"
    And a FOLIO item "c"
    And that item has the copy number "1"
    When I transform the MARC record
    Then the work has 3 items with title:
      | Vol. 1 |
      | Copy 1 |
      | Copy 1 |

  Scenario: A suppressed item does not count when removing generated titles
    Given a FOLIO item "a"
    And that item has the copy number "1"
    And a FOLIO item "b"
    And that item has the volume "Vol. 1"
    And that item is suppressed from discovery
    When I transform the MARC record
    Then the only item has the source identifier value "a"
    And it has no title

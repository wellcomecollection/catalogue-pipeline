Feature: FOLIO items on order
  Ported from the Scala transformer (SierraItemsOnOrderTest.scala).
  Sierra builds an on-order item from an order record. In FOLIO the order
  creates the item itself, which starts at status On order, moves to In process
  when it is received and becomes Available when it is checked in
  (platform#6798). FOLIO items do not carry an order date, so the "Ordered" label
  never includes a date.

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"
    And a FOLIO item "a"

  Scenario Outline: An item at <status> is shown as on order
    Given that item is in the location "ACQL" named "Acquisitions Temporary Location" in the library "Acquisitions"
    And that item has the status "<status>"
    When I transform the MARC record
    Then the only item has the source identifier value "a"
    And its only location has the location type "on-order"
    And it has the label "<label>"
    And it has no shelfmark
    And it has 0 access conditions

    Examples:
      | status     | label                                        |
      | On order   | Ordered for Wellcome Collection              |
      | In process | Awaiting cataloguing for Wellcome Collection |

  Scenario: An item on order that is suppressed from discovery is not shown
    Given that item has the status "On order"
    And that item is suppressed from discovery
    When I transform the MARC record
    Then the work has no item "a"

  Scenario Outline: An item at <status> is not on order
    "In process (non-requestable)" is used for digitisation, not acquisitions.
    Given that item has the status "<status>"
    When I transform the MARC record
    Then the only item has the source identifier value "a"
    And its only location has the location type "closed-stores"
    And it has the shelfmark "EPB/B/1"
    And item "a" has the access method "<method>", the status "<access status>" and <note>

    Examples:
      | status                       | method         | access status          | note                   |
      | Available                    | OnlineRequest  | Open                   | no note                |
      | In process (non-requestable) | NotRequestable | TemporarilyUnavailable | the "digitisation" note |

  # Everything below is a deliberate divergence from the Scala implementation.

  Scenario: An item on order is shown alongside catalogued items
    A Sierra order is a separate record from the item it becomes, so
    SierraItemsOnOrder ignores orders on any bib with items or a CAT DATE, to
    avoid showing the same copy twice. A FOLIO item is one record whose status
    changes, so an item on order is shown next to the others.
    Given a FOLIO item "b"
    And that item has the status "In process"
    When I transform the MARC record
    Then item "a" has the shelfmark "EPB/B/1"
    And item "a" has the access method "OnlineRequest", the status "Open" and no note
    And item "b" has a location of type "on-order" labelled "Awaiting cataloguing for Wellcome Collection"

  Scenario: Every item on order is shown
    Sierra's order items have no identifier, so identical ones were merged. Each
    FOLIO item is a copy with its own identifier, so none is merged.
    Given that item has the status "On order"
    And a FOLIO item "b"
    And that item has the status "On order"
    When I transform the MARC record
    Then item "a" has a location of type "on-order" labelled "Ordered for Wellcome Collection"
    And item "b" has a location of type "on-order" labelled "Ordered for Wellcome Collection"

  Scenario: An item on order keeps its identifier, title and display note
    SierraItemsOnOrder builds its item from the order record, which has no item
    record, title or note, so the Sierra item has no identifier, title or note.
    Given that item has the status "On order"
    And that item has the volume "Vol. 3"
    And that item has the display note "Signed by the author"
    When I transform the MARC record
    Then item "a" has a location of type "on-order" labelled "Ordered for Wellcome Collection"
    And the only item has the source identifier value "a"
    And the only item has the title "Vol. 3"
    And item "a" has the note "Signed by the author"

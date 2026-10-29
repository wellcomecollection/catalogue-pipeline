Feature: FOLIO items on order
  An item created by a FOLIO order starts at status On order, moves to In
  process when it is received and becomes Available when it is checked in
  (tested by EBSCO on FOLIO-56). Until then the item is shown as the Sierra
  transformer shows an order: at the on-order location, labelled by status
  (platform#6798). FOLIO items carry no order date, so the "Ordered" label has
  none, as when Sierra lacks the date.

  "In process (non-requestable)" is a different status, used for digitisation,
  and keeps its location and access condition.

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"
    And a FOLIO item "a"

  Scenario Outline: An item at <status> is shown as on order
    Given that item has the status "<status>"
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

  Scenario: An item in process for digitisation keeps its location and access condition
    Given that item has the status "In process (non-requestable)"
    When I transform the MARC record
    Then the only item has the source identifier value "a"
    And its only location has the location type "closed-stores"
    And it has the shelfmark "EPB/B/1"
    And item "a" has the access method "NotRequestable", the status "TemporarilyUnavailable" and the "digitisation" note

  Scenario: An available item keeps its location and access condition
    Given that item has the status "Available"
    When I transform the MARC record
    Then the only item has the source identifier value "a"
    And its only location has the location type "closed-stores"
    And it has the shelfmark "EPB/B/1"
    And item "a" has the access method "OnlineRequest", the status "Open" and no note

  Scenario: An item on order replaces the awaiting-cataloguing placeholder
    Given the MARC record has a 590 field with subfield "a" value "UNCATALOGUED: Zine"
    And that item has the status "On order"
    When I transform the MARC record
    Then the only item has the id type "Identifiable"
    And its only location has the label "Ordered for Wellcome Collection"

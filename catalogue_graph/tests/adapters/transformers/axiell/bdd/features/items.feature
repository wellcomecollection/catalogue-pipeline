Feature: Items from Axiell MARC records
  Axiell records always produce exactly one item, placed in Closed stores.
  An optional access condition is derived from MARC 506 $f (status value) or 506 $g
  (closed-until date). A future closed-until date produces a Closed status when no
  recognised $f value is present.
  - https://www.loc.gov/marc/bibliographic/bd506.html

  Background:
    Given a valid MARC record

  Scenario: No 506 field — item has no access conditions
    When I transform the MARC record
    Then there is 1 item
    And the item is in closed stores
    And the item has no access conditions

  Scenario Outline: Recognised 506 $f values map to access condition statuses
    Given the MARC record has a 506 field with subfield "f" value "<status_value>"
    When I transform the MARC record
    Then there is 1 item
    And the item is in closed stores
    And the item has 1 access condition with status "<status_type>"
    Examples:
      | status_value       | status_type        |
      | OPEN               | Open               |
      | OPENWITHADVISORY   | OpenWithAdvisory   |
      | RESTRICTED         | Restricted         |
      | RESTRICTIONSAPPLY  | Restricted         |
      | PERMISSIONREQUIRED | PermissionRequired |
      | DEACCESSIONED      | Unavailable        |
      | MISSING            | Unavailable        |
      | SAFEGUARDED        | Safeguarded        |
      | BYAPPOINTMENT      | ByAppointment      |

  Scenario: Unrecognised 506 $f value — item has no access conditions
    Given the MARC record has a 506 field with subfield "f" value "UNKNOWN"
    When I transform the MARC record
    Then there is 1 item
    And the item has no access conditions

  Scenario: Future closed-until date — item has Closed access condition
    Given the MARC record has a 506 field with subfield "g" value "2030-01-01"
    When I transform the MARC record
    Then there is 1 item
    And the item has 1 access condition with status "Closed"

  Scenario: Past closed-until date — item has no access conditions
    Given the MARC record has a 506 field with subfield "g" value "2020-01-01"
    When I transform the MARC record
    Then there is 1 item
    And the item has no access conditions

  Scenario: Recognised 506 $f value takes precedence over future closed-until date
    Given the MARC record has a 506 field with subfield "f" value "OPEN" and subfield "g" value "2030-01-01"
    When I transform the MARC record
    Then there is 1 item
    And the item has 1 access condition with status "Open"

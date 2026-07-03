Feature: Physical description extraction from Axiell MARC records
  Physical description is derived from MARC 300 (Physical Description).
  Subfields $a (extent), $b (other physical details), $c (dimensions), and
  $e (accompanying material) are joined with a space. Multiple 300 fields are
  joined with "<br/>".
  - https://www.loc.gov/marc/bibliographic/bd300.html

  Background:
    Given a valid MARC record

  Scenario: No 300 field — physical description is absent
    When I transform the MARC record
    Then the work's physical_description is absent

  Scenario: Single 300 field with $a only
    Given the MARC record has a 300 field with subfield "a" value "42 leaves"
    When I transform the MARC record
    Then the work's physical_description is "42 leaves"

  Scenario: Single 300 field with multiple subfields joined by space
    Given the MARC record has a 300 field with subfields:
      | code | value              |
      | a    | 1 folder           |
      | b    | ill.               |
      | c    | 30 cm              |
      | e    | + 1 map            |
    When I transform the MARC record
    Then the work's physical_description is "1 folder ill. 30 cm + 1 map"

  Scenario: Multiple 300 fields are joined with <br/>
    Given the MARC record has a 300 field with subfield "a" value "42 leaves"
    And the MARC record has another 300 field with subfield "a" value "1 map"
    When I transform the MARC record
    Then the work's physical_description is "42 leaves<br/>1 map"

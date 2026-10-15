Feature: Work type extraction from Axiell MARC records
  Work type is derived from MARC 351 $c (Organization and Arrangement of Materials — Hierarchical level).
  - https://www.loc.gov/marc/bibliographic/bd351.html

  Background:
    Given a valid MARC record

  Scenario Outline: Each hierarchical level maps to the correct work type
    Given the MARC record's only 351 field with subfield "c" value "<level>"
    When I transform the MARC record
    Then the work's work_type is "<work_type>"
    Examples:
      | level       | work_type  |
      | collection  | Collection |
      | section     | Section    |
      | sub-section | Section    |
      | series      | Series     |
      | sub-series  | Series     |
      | item        | Standard   |
      | item part   | Standard   |

  Scenario: Level matching is case-insensitive
    Given the MARC record's only 351 field with subfield "c" value "CoLlECtIon"
    When I transform the MARC record
    Then the work's work_type is "Collection"

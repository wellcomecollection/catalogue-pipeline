Feature: Production event extraction from Axiell MARC records
  Production events are derived from MARC 264 and 046 fields.
  - https://www.loc.gov/marc/bibliographic/bd264.html
  - https://www.loc.gov/marc/bibliographic/bd046.html

  Background:
    Given a valid MARC record

  Scenario: No 264 field — no production events
    When I transform the MARC record
    Then there are no productions

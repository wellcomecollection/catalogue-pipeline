Feature: Production event extraction from Axiell MARC records
  Production events are derived from MARC 264 and 046 fields.
  - https://www.loc.gov/marc/bibliographic/bd264.html
  - https://www.loc.gov/marc/bibliographic/bd046.html

  Background:
    Given a valid MARC record

  Scenario: No 264 field — no production events
    When I transform the MARC record
    Then there are no productions

  Scenario: Empty 264 $c subfield — no production events
    Given the MARC record has a 264 field with subfield "c" value ""
    When I transform the MARC record
    Then there are no productions

  Scenario: Single 264 $c, no 046 — period parsed from label
    Given the MARC record has a 264 field with subfield "c" value "1985"
    When I transform the MARC record
    Then there is 1 production
    And the only production has the label "1985"
    And it has 1 date
    And its only date has the range.from_time "1985-01-01T00:00:00Z"

  Scenario: Single 264 $c with both 046 $k and $l — period derived from 046 dates
    Given the MARC record has a 264 field with subfield "c" value "1985"
    And the MARC record has a 046 field with subfield "k" value "1985-06-15" and subfield "l" value "1985-09-30"
    When I transform the MARC record
    Then there is 1 production
    And the only production has the label "1985"
    And its only date has the range.from_time "1985-06-15T00:00:00Z"
    And it has the range.to_time "1985-09-30T23:59:59.999999999Z"

  Scenario: Single 264 $c with 046 $k but no $l — falls back to parsing label
    Given the MARC record has a 264 field with subfield "c" value "1985"
    And the MARC record has a 046 field with subfield "k" value "1985-06-15"
    When I transform the MARC record
    Then there is 1 production
    And the only production has the label "1985"
    And its only date has the range.from_time "1985-01-01T00:00:00Z"
    And it has the range.to_time "1985-12-31T23:59:59.999999999Z"

  Scenario: Single 264 $c with 046 $l but no $k — falls back to parsing label
    Given the MARC record has a 264 field with subfield "c" value "1985"
    And the MARC record has a 046 field with subfield "l" value "1985-09-30"
    When I transform the MARC record
    Then there is 1 production
    And the only production has the label "1985"
    And its only date has the range.from_time "1985-01-01T00:00:00Z"
    And it has the range.to_time "1985-12-31T23:59:59.999999999Z"

  Scenario: Multiple 264 $c fields — labels joined, one period per label
    Given the MARC record has a 264 field with subfield "c" value "1985"
    And the MARC record has another 264 field with subfield "c" value "1990"
    When I transform the MARC record
    Then there is 1 production
    And the only production has the label "1985 1990"
    And it has 2 dates

  Scenario: Multiple 264 $c with 046 dates — 046 dates not used, labels parsed individually
    Given the MARC record has a 264 field with subfield "c" value "1985"
    And the MARC record has another 264 field with subfield "c" value "1990"
    And the MARC record has a 046 field with subfield "k" value "1985-06-15" and subfield "l" value "1985-09-30"
    When I transform the MARC record
    Then there is 1 production
    And the only production has the label "1985 1990"
    And it has 2 dates

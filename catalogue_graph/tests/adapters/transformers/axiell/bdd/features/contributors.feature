Feature: Contributors extraction from Axiell MARC records
  Contributors are derived from MARC 720 $a (Added Entry—Uncontrolled Name).
  - https://www.loc.gov/marc/bibliographic/bd720.html

  Background:
    Given a valid MARC record

  Scenario: No 720 field — no contributors
    When I transform the MARC record
    Then there are no contributors

  Scenario: Empty $a is ignored
    Given the MARC record has a 720 field with subfield "a" value ""
    When I transform the MARC record
    Then there are no contributors

  Scenario: Single contributor
    Given the MARC record has a 720 field with subfield "a" value "Smith, John"
    When I transform the MARC record
    Then there is 1 contributor
    And the only contributor has the agent.label "Smith, John"
    And the only contributor has the agent.type "Agent"

  Scenario: Trailing comma in name is stripped
    Given the MARC record has a 720 field with subfield "a" value "Smith, John,"
    When I transform the MARC record
    Then the only contributor has the agent.label "Smith, John"

  Scenario: Multiple contributors preserve order
    Given the MARC record has a 720 field with subfield "a" value "Smith, John"
    And the MARC record has another 720 field with subfield "a" value "Jones, Mary"
    When I transform the MARC record
    Then there are 2 contributors
    And the 1st contributor has the agent.label "Smith, John"
    And the 2nd contributor has the agent.label "Jones, Mary"

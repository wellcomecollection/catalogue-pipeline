Feature: Production event extraction from Axiell MARC records
  Production events are derived from MARC 260, 264, and 008 fields.
  260 $a = place, $b = agent, $c = date. All subfields are joined as the label.
  264 ind2 sets the function: 0=Production, 1=Publication, 2=Distribution, 3=Manufacture.
  008/07-10 supplies the date when no $c is present; 008/15-17 supplies the place of production.
  - https://www.loc.gov/marc/bibliographic/bd260.html
  - https://www.loc.gov/marc/bibliographic/bd264.html
  - https://www.loc.gov/marc/bibliographic/bd008a.html

  Background:
    Given a valid MARC record

  Scenario: No 260 or 264 field — no production events
    When I transform the MARC record
    Then there are no productions

  Scenario: 260 field — label is all subfields joined by space
    Given the MARC record has a 260 field with subfield "a" value "London :" and subfield "b" value "Wellcome Library," and subfield "c" value "1995"
    When I transform the MARC record
    Then there is 1 production
    And the only production has the label "London : Wellcome Library, 1995"

  Scenario: 260 $a yields a place concept
    Given the MARC record has a 260 field with subfield "a" value "London :"
    When I transform the MARC record
    Then the only production has the label "London :"
    And it has 1 place
    And its only place has the label "London"

  Scenario: 260 $b yields an agent concept
    Given the MARC record has a 260 field with subfield "b" value "Wellcome Library,"
    When I transform the MARC record
    Then the only production has the label "Wellcome Library,"
    And it has 1 agent
    And its only agent has the label "Wellcome Library"

  Scenario: 264 ind2=1 yields function "Publication"
    Given the MARC record has a 264 field with indicators " " "1" with subfield "a" value "London" and subfield "b" value "Wellcome Library"
    When I transform the MARC record
    Then there is 1 production
    And the only production has the function.label "Publication"

  Scenario: 264 ind2=0 yields function "Production"
    Given the MARC record has a 264 field with indicators " " "0" with subfield "a" value "London" and subfield "b" value "Wellcome Library"
    When I transform the MARC record
    Then the only production has the function.label "Production"

  Scenario: Multiple 260 fields produce multiple production events
    Given the MARC record has a 260 field with subfield "a" value "London :" and subfield "b" value "Wellcome Library," and subfield "c" value "1900"
    And the MARC record has another 260 field with subfield "a" value "New York :" and subfield "b" value "Columbia University Press," and subfield "c" value "2000"
    When I transform the MARC record
    Then there are 2 productions
    And the 1st production has the label "London : Wellcome Library, 1900"
    And the 2nd production has the label "New York : Columbia University Press, 2000"

  Scenario: 008 alone — date range becomes a production event
    Given the MARC record's only 008 field with the value "||||||s1925    enk"
    When I transform the MARC record
    Then there is 1 production
    And the only production has the label "1925"
    And it has 1 place
    And its only place has the label "England"

  Scenario: 260 with no date uses 008 date
    Given the MARC record has a 260 field with subfield "a" value "London :" and subfield "b" value "Wellcome Library,"
    And the MARC record's only 008 field with the value "||||||s1925    enk"
    When I transform the MARC record
    Then there is 1 production
    And the only production has the label "London : Wellcome Library,"

Feature: edition (MARC 250)
  The edition statement comes from MARC 250 ǂa. 250 is repeatable but ǂa is
  not, so at most one ǂa is taken from each 250 and the values are joined
  with a single space.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (MarcEditionTest.scala, SierraEditionTest.scala), including its
  test data, so the two can be compared directly.

  https://www.loc.gov/marc/bibliographic/bd250.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  # Ported from MarcEditionTest.scala and SierraEditionTest.scala.

  Scenario: The edition statement is taken from 250 ǂa
    Given the MARC record has a 250 field with subfield "a" value "First!"
    When I transform the MARC record
    Then the work's edition is "First!"

  Scenario: Repeated 250 fields are concatenated into a single string
    Given the MARC record has a 250 field with subfield "a" value "Cyntaf"
    And the MARC record has another 250 field with subfield "a" value "Ail"
    When I transform the MARC record
    Then the work's edition is "Cyntaf Ail"

  Scenario: A record with no 250 field has no edition
    Given the MARC record has a 251 field with subfield "a" value "1st edition."
    When I transform the MARC record
    Then the work's edition is absent

  Scenario: A 250 with no ǂa has no edition
    Given the MARC record has a 250 field with subfield "b" value "1st edition."
    When I transform the MARC record
    Then the work's edition is absent

  Scenario: A 250 ǂa devoid of useful content has no edition
    Given the MARC record has a 250 field with subfield "a" value " "
    When I transform the MARC record
    Then the work's edition is absent

  Scenario: Subfields other than ǂa are ignored
    Given the MARC record has a 250 field with subfield "a" value "Større utgave" and subfield "7" value "HP"
    When I transform the MARC record
    Then the work's edition is "Større utgave"

  # A deliberate improvement on the Scala, which keeps the empty values in the
  # join and so returns a string padded with stray spaces.

  Scenario: Empty 250 fields are dropped rather than padding the result
    Given the MARC record has a 250 field with subfield "a" value ""
    And the MARC record has another 250 field with subfield "a" value "             "
    And the MARC record has another 250 field with subfield "7" value "Bloke down the pub"
    And the MARC record has another 250 field with subfield "a" value "This one" and subfield "7" value "Bloke down the pub"
    When I transform the MARC record
    Then the work's edition is "This one"

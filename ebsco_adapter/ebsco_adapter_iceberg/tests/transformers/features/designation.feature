Feature: MARC record designation extraction
  The designation field in the output is derived
  from the MARC 362 field in the input.

  Background:
    Given a valid MARC record with 001 and 245 fields

  Scenario: No designation present
    When I transform the MARC record
    Then there is no designation

  Scenario: A single designation
    Given the MARC record has a 362 field with subfield "a" value "Tertiary adjunct of unimatrix zero one"
    When I transform the MARC record
    Then the only designation is "Tertiary adjunct of unimatrix zero one"

  Scenario: A designation with extraneous whitespace
    Given the MARC record has a 362 field with subfield "a" value "    hello, I'm in space!     "
    When I transform the MARC record
    Then the only designation is "hello, I'm in space!"

  Scenario: Multiple designations
    Given the MARC record has a 362 field with subfield "a" value "Seven of Nine"
    And the MARC record has another 362 field with subfield "a" value "Tertiary adjunct of unimatrix zero one"
    When I transform the MARC record
    Then the first designation is "Seven of Nine"
    And the second designation is "Tertiary adjunct of unimatrix zero one"
    And there are exactly 2 designations

  Scenario: Subfield z
    Given the MARC record has a 362 field with subfield "a" value "Tertiary adjunct of unimatrix zero one"
    And the MARC record has another 362 field with subfield "z" value "Memory Alpha"
    When I transform the MARC record
    Then the only designation is "Tertiary adjunct of unimatrix zero one"

  Scenario: Empty or whitespace-only subfields
      Given the MARC record has a 362 field with subfield "a" value ""
      And the MARC record has another 362 field with subfield "a" value "             "
      And the MARC record has another 362 field with subfield "z" value "Bloke down the pub"
      And the MARC record has another 362 field with subfield "a" value "This one" and subfield "z" value "Bloke down the pub"
      When I transform the MARC record
      Then the only designation is "This one"
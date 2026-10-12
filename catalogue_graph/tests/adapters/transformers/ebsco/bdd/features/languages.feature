Feature: Extract the language from MARC 008/35-37
  EBSCO works carry a single language, the one coded in 008/35-37. Additional
  languages in 041 are not read.

  Background:
    Given a valid MARC record

  Scenario: Only the 008 language is used
    Given the MARC record's only 008 field with the value "900716s1991    maub    ob    001 0 lat  "
    And the MARC record has a 041 field with subfield "a" value "ger"
    When I transform the MARC record
    Then the only language has the label "Latin"

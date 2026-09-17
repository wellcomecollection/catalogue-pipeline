Feature: duration (MARC 306)
  The duration in seconds comes from MARC 306 ǂa, read as hhmmss. Only the
  first 306 ǂa on the record is used. The value must be exactly six digits.
  Minutes and seconds of 60 are allowed, as an exact hour is catalogued
  as 006000.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (SierraDurationTest.scala), including its test data, so the two
  can be compared directly.

  https://www.loc.gov/marc/bibliographic/bd306.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: Duration in seconds is extracted from 306
    Given the MARC record has a 306 field with subfield "a" value "011012"
    When I transform the MARC record
    Then the work's duration is 4212

  Scenario: The first duration is used when multiple are defined
    Given the MARC record has a 306 field with subfield "a" value "001000"
    And the MARC record has another 306 field with subfield "a" value "001132"
    When I transform the MARC record
    Then the work's duration is 600

  Scenario: A badly formatted 306 gives no duration
    Given the MARC record has a 306 field with subfield "a" value "01xx1012"
    When I transform the MARC record
    Then the work's duration is absent

  Scenario: A duration in the wrong field is ignored
    Given the MARC record has a 500 field with subfield "a" value "011012"
    When I transform the MARC record
    Then the work's duration is absent

  Scenario: A duration in the wrong subfield is ignored
    Given the MARC record has a 306 field with subfield "b" value "011012"
    When I transform the MARC record
    Then the work's duration is absent

  Scenario: Sixty minutes is a valid way to catalogue an exact hour
    Given the MARC record has a 306 field with subfield "a" value "006000"
    When I transform the MARC record
    Then the work's duration is 3600

  # A deliberate divergence from the Scala implementation, which splits the value
  # into pairs and only checks that there are three of them, so an invalid five-digit
  # value like 00500 is read as 00h 50m 0s (i.e. the missing digit is interpreted
  # as a trailing zero).
  #
  # At the time of writing, there are 23 Sierra works which store a five-digit value.
  # 20 of them also state the duration in words (in 300 ǂa), which contradicts the
  # Scala reading, showing that the omitted digit is a missing *leading* zero in all
  # 20 cases.
  #
  # The Python pipeline rejects five-digit values instead of trying to interpret
  # them.

  Scenario: A five-digit 306 gives no duration
    Given the MARC record has a 306 field with subfield "a" value "00500"
    When I transform the MARC record
    Then the work's duration is absent

  # A deliberate divergence from the Scala implementation, which does not trim
  # the value, so a trailing space makes a fourth pair and the duration is dropped.
  # The Python pipeline strips whitespace before checking for six digits.

  Scenario: Surrounding whitespace in a 306 is ignored
    Given the MARC record has a 306 field with subfield "a" value "000400 "
    When I transform the MARC record
    Then the work's duration is 240

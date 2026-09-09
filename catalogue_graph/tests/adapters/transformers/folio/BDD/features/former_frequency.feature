Feature: former frequency (MARC 321)
  Former frequency is a list with one entry per 321 field, each built by
  joining that field's ǂa and ǂb with a space. 321 is repeatable, and we
  do have records that use it more than once. Subfields and fields with no
  content are dropped.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (SierraFormerFrequencyTest.scala), including its test data, so
  the two can be compared directly.

  https://www.loc.gov/marc/bibliographic/bd321.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  # Ported from SierraFormerFrequencyTest.scala.

  Scenario: A record that doesn't use MARC 321 has an empty list
    When I transform the MARC record
    Then there are no former frequencies

  Scenario: 321 ǂa and ǂb are combined
    # This is based on b16803930
    Given the MARC record has a 321 field with subfield "a" value "Weekly," and subfield "b" value "<Aug. 1, 1988->"
    When I transform the MARC record
    Then the only former frequency is "Weekly, <Aug. 1, 1988->"

  Scenario: Multiple instances of 321 each become their own entry
    # This is based on b1312092x
    Given the MARC record has a 321 field with subfield "a" value "Former frequency: Monthly, 1809-1819, 1822-1827"
    And the MARC record has another 321 field with subfield "a" value "Former frequency: Weekly, 1801-1808"
    When I transform the MARC record
    Then there are 2 former frequencies
    And the 1st former frequency is "Former frequency: Monthly, 1809-1819, 1822-1827"
    And the 2nd former frequency is "Former frequency: Weekly, 1801-1808"

  # Deliberate divergences from the Scala, which trims nothing and keeps an
  # entry for a 321 with no ǂa or ǂb content. Neither occurs in the data.

  Scenario: Surrounding whitespace is stripped from each subfield before joining
    Given the MARC record has a 321 field with subfield "a" value "Weekly, " and subfield "b" value "<Aug. 1, 1988->"
    When I transform the MARC record
    Then the only former frequency is "Weekly, <Aug. 1, 1988->"

  Scenario: A subfield with no content is dropped rather than joined
    Given the MARC record has a 321 field with subfield "a" value "Weekly," and subfield "b" value " " and subfield "a" value "1801-1808"
    When I transform the MARC record
    Then the only former frequency is "Weekly, 1801-1808"

  Scenario: A 321 with no content becomes no entry at all
    Given the MARC record has a 321 field with subfield "a" value " "
    And the MARC record has another 321 field with subfield "a" value "Weekly,"
    When I transform the MARC record
    Then the only former frequency is "Weekly,"

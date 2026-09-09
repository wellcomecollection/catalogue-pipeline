Feature: current frequency (MARC 310)
  The current frequency is built from MARC 310 subfields ǂa and ǂb, joined with
  a space in the order they appear in the field. 310 is repeatable, and all of a
  record's 310s are flattened into one string, again joined with a space and with

  no separator marking the field boundary. Subfields and fields with no content
  are dropped. A record with no usable 310 has no current frequency.

  These scenarios mirror the unit tests of the Scala transformers this replaces
  (MarcCurrentFrequencyTest.scala and SierraCurrentFrequencyTest.scala),
  including their test data, so the two can be compared directly.

  https://www.loc.gov/marc/bibliographic/bd310.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A record that doesn't use MARC 310 has no current frequency
    Given the MARC record has a 250 field with subfield "a" value "First!"
    When I transform the MARC record
    Then the work's current frequency is absent

  Scenario: A 310 with no ǂa or ǂb gives no current frequency
    Given the MARC record has a 310 field with subfield "1" value "Cyntaf!" and subfield "6" value "Ail!"
    When I transform the MARC record
    Then the work's current frequency is absent

  Scenario: The content of ǂa is used
    Given the MARC record has a 310 field with subfield "a" value "Unwaith"
    When I transform the MARC record
    Then the work's current frequency is "Unwaith"

  Scenario: The content of ǂb is used
    Given the MARC record has a 310 field with subfield "b" value "Unwaith"
    When I transform the MARC record
    Then the work's current frequency is "Unwaith"

  Scenario: ǂa and ǂb are joined with a space
    Given the MARC record has a 310 field with subfield "a" value "Every Nowruz" and subfield "b" value "2024-03-20"
    When I transform the MARC record
    Then the work's current frequency is "Every Nowruz 2024-03-20"

  Scenario: Multiple 310s are flattened into one string
    Given the MARC record has a 310 field with subfield "a" value "Every Nowruz"
    And the MARC record has another 310 field with subfield "a" value "Whenever I feel like it"
    When I transform the MARC record
    Then the work's current frequency is "Every Nowruz Whenever I feel like it"

  Scenario: 310 ǂa and ǂb are combined
    Given the MARC record has a 310 field with subfield "a" value "Annual," and subfield "b" value "2007-2012"
    When I transform the MARC record
    Then the work's current frequency is "Annual, 2007-2012"

  Scenario: Multiple 310 fields are combined into a single string
    Given the MARC record has a 310 field with subfield "a" value "Annual," and subfield "b" value "2007-2012"
    And the MARC record has another 310 field with subfield "a" value "Solsticial," and subfield "b" value "2004-2006"
    When I transform the MARC record
    Then the work's current frequency is "Annual, 2007-2012 Solsticial, 2004-2006"

  # Deliberate divergences from the Scala, which joins empty subfields and
  # fields and so emits stray whitespace. Only the first occurs in the data.

  Scenario: Surrounding whitespace is stripped from each subfield before joining
    Given the MARC record has a 310 field with subfield "a" value "6 no. a year, " and subfield "b" value "<Feb. 1981->"
    When I transform the MARC record
    Then the work's current frequency is "6 no. a year, <Feb. 1981->"

  Scenario: A subfield with no content is dropped rather than joined
    Given the MARC record has a 310 field with subfield "a" value "Annual," and subfield "b" value " "
    And the MARC record has another 310 field with subfield "a" value "Solsticial,"
    When I transform the MARC record
    Then the work's current frequency is "Annual, Solsticial,"

  Scenario: A leading 310 with no content is dropped rather than joined
    Given the MARC record has a 310 field with subfield "a" value " "
    And the MARC record has another 310 field with subfield "a" value "Annual,"
    When I transform the MARC record
    Then the work's current frequency is "Annual,"

  Scenario: A 310 with no content between two others is dropped rather than joined
    Given the MARC record has a 310 field with subfield "a" value "Annual,"
    And the MARC record has another 310 field with subfield "a" value " "
    And the MARC record has another 310 field with subfield "a" value "Solsticial,"
    When I transform the MARC record
    Then the work's current frequency is "Annual, Solsticial,"

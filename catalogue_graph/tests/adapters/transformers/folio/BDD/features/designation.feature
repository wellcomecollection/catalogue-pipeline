Feature: designation (MARC 362)
  Designation is a list with one entry per 362 field, taken from that field's
  trimmed ǂa. 362 is repeatable, and we do have records that use it more than
  once. ǂa should not repeat within a field; a field without a usable ǂa
  contributes no entry.

  These scenarios mirror the unit tests of the Scala transformers this
  replaces (MarcDesignationTest.scala and SierraDesignationTest.scala),
  including their test data, so the two can be compared directly.

  https://www.loc.gov/marc/bibliographic/bd362.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  # Ported from MarcDesignationTest.scala.

  Scenario: A record that doesn't use MARC 362 has an empty list
    When I transform the MARC record
    Then there are no designations

  Scenario: A 362 with no ǂa contributes no entry
    Given the MARC record has a 362 field with subfield "z" value "not me!"
    When I transform the MARC record
    Then there are no designations

  Scenario: The designation comes from 362 ǂa
    Given the MARC record has a 362 field with subfield "a" value "TWKE-4"
    When I transform the MARC record
    Then the only designation is "TWKE-4"

  Scenario: Surrounding whitespace is trimmed
    Given the MARC record has a 362 field with subfield "a" value "   GSV	"
    When I transform the MARC record
    Then the only designation is "GSV"

  Scenario: Multiple instances of 362 each become their own entry
    Given the MARC record has a 362 field with subfield "a" value "VFP"
    And the MARC record has another 362 field with subfield "a" value "GCU"
    When I transform the MARC record
    Then there are 2 designations
    And the 1st designation is "VFP"
    And the 2nd designation is "GCU"

  # Deliberate divergence: the Scala makes the whole work invisible, we log and
  # take the first.

  Scenario: The first ǂa is used when a 362 erroneously repeats it
    Given the MARC record has a 362 field with subfield "a" value "Cyntaf" and subfield "a" value "Ail"
    When I transform the MARC record
    Then an error "Repeated non-repeating subfield $a" is logged with tag "362"
    And the only designation is "Cyntaf"

  # Ported from SierraDesignationTest.scala.

  Scenario: ǂz is ignored alongside ǂa
    # This is based on b14974708
    Given the MARC record has a 362 field with subfield "a" value "Began in 1955; ceased with v. 49, no. 4 (Dec. 2003). " and subfield "z" value "Cf.National Library of Australia catalogue."
    When I transform the MARC record
    Then the only designation is "Began in 1955; ceased with v. 49, no. 4 (Dec. 2003)."

  Scenario: Each of several 362 fields contributes an entry in order
    # This is based on b14975853
    Given the MARC record has a 362 field with subfield "a" value "Vol. 51, no. 2, 3 (summer 1988)-"
    And the MARC record has another 362 field with subfield "a" value "Ceased with v. 59, no. 1 published in 1998."
    When I transform the MARC record
    Then there are 2 designations
    And the 1st designation is "Vol. 51, no. 2, 3 (summer 1988)-"
    And the 2nd designation is "Ceased with v. 59, no. 1 published in 1998."

Feature: format (MARC 998 ǂd)
  The format comes from the material type code, looked up in the same code
  table as the Sierra transformer this replaces. The Sierra transformer reads
  the code from the MAT TYPE fixed field. FOLIO has no MAT TYPE: the code
  reached it only as MARC 998 ǂd, in the export of Sierra's fixed fields made
  at migration, which FOLIO neither updates nor creates for records catalogued
  in FOLIO. So 998 ǂd is an interim source until Collections Information's
  maintained field exists, and the resolver tries its sources in order so that
  field can be put first.

  Only the first 998 is the migration export. Any later 998s were already on
  the Sierra record, such as earlier copies of the fixed fields with the
  material type as it was then, and can carry a different ǂd.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (SierraFormatTest.scala and SierraMaterialTypesTest.scala),
  including their test data, so the two can be compared directly.

  https://github.com/wellcomecollection/platform/issues/6570
  https://github.com/wellcomecollection/platform/issues/6555

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  # Ported from SierraFormatTest.scala.

  Scenario: The format comes from the material type code
    Given the MARC record has a 998 field with subfield "d" value "a"
    When I transform the MARC record
    Then the work's format.id is "a"
    And the work's format.label is "Books"

  # Ported from SierraMaterialTypesTest.scala.

  Scenario: A code is looked up in the format table
    Given the MARC record has a 998 field with subfield "d" value "w"
    When I transform the MARC record
    Then the work's format.id is "w"
    And the work's format.label is "Student dissertations"

  Scenario: An e-resource code gives the format it links to
    # v is E-books, which links to Books
    Given the MARC record has a 998 field with subfield "d" value "v"
    When I transform the MARC record
    Then the work's format.id is "a"
    And the work's format.label is "Books"

  Scenario: An unrecognised code gives no format
    Given the MARC record has a 998 field with subfield "d" value "?"
    When I transform the MARC record
    Then a warning "Unrecognised material type code" is logged
    And the work's format is absent

  Scenario: An empty code gives no format
    Given the MARC record has a 998 field with subfield "d" value ""
    When I transform the MARC record
    Then a warning "Material type code is not a single character" is logged
    And the work's format is absent

  Scenario: A code of more than one character gives no format
    Given the MARC record has a 998 field with subfield "d" value "XXX"
    When I transform the MARC record
    Then a warning "Material type code is not a single character" is logged
    And the work's format is absent

  # Not in the Scala tests. Sierra holds the code padded to three characters
  # and the Scala trims it as it decodes the API response.

  Scenario: A padded 998 ǂd is trimmed
    Given the MARC record has a 998 field with subfield "d" value "a  "
    When I transform the MARC record
    Then the work's format.id is "a"
    And the work's format.label is "Books"

  # FOLIO-specific behaviour, verified against the FOLIO data.

  Scenario: The first 998 is used when there are several
    Given the MARC record has a 998 field with subfield "d" value "v  "
    And the MARC record has another 998 field with subfield "d" value "m"
    When I transform the MARC record
    Then the work's format.id is "a"
    And the work's format.label is "Books"

  Scenario: A record with no 998 has no format
    When I transform the MARC record
    Then the work's format is absent

  Scenario: A 998 with no ǂd gives no format
    Given the MARC record has a 998 field with subfield "a" value "stax"
    When I transform the MARC record
    Then the work's format is absent

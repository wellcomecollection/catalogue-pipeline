Feature: Collection path and reference number extraction from Axiell MARC records
  The collection path is built from the calm-ref-no (MARC 035 "(Calm RefNo)<value>") and
  the optional calm-altref-no (MARC 035 "(AltRefNo)<value>") other identifiers.
  The reference number mirrors the collection path label.
  - https://www.loc.gov/marc/bibliographic/bd035.html

  In "part_of" mode the path is instead built from the object number (calm-altref-no)
  and the parent link in local field 982 ($a parent priref, $b parent object number).
  Each key is "axiell:" plus an object number with "/" encoded as "|".

  Background:
    Given a valid MARC record

  Scenario: Collection path path comes from calm-ref-no
    When I transform the MARC record
    Then the work's collection_path.path is "TestRefNo"

  Scenario: No calm-altref-no — label and reference number are absent
    When I transform the MARC record
    Then the work's collection_path.label is absent
    And the work's reference_number is absent

  Scenario: calm-altref-no sets the collection path label and reference number
    Given the MARC record has a 035 field with subfield "a" value "(AltRefNo)PP/MIA/1"
    When I transform the MARC record
    Then the work's collection_path.label is "PP/MIA/1"
    And the work's reference_number is "PP/MIA/1"

  Scenario: RefNo mode ignores the 982 parent link
    Given the Axiell collection path source is "refno"
    And the MARC record has a 035 field with subfield "a" value "(AltRefNo)PP/MIA/1"
    And the MARC record has a 982 field with subfield "a" value "110000001" and subfield "b" value "PP/MIA"
    When I transform the MARC record
    Then the work's collection_path.path is "TestRefNo"

  Scenario: part_of mode, a record with no 982 is a collection root
    Given the Axiell collection path source is "part_of"
    And the MARC record has a 035 field with subfield "a" value "(AltRefNo)PP/MIA"
    When I transform the MARC record
    Then the work's collection_path.path is "axiell:PP|MIA"
    And the work's collection_path.label is "PP/MIA"
    And the work's reference_number is "PP/MIA"

  Scenario: part_of mode, the path points from the parent to the record
    Given the Axiell collection path source is "part_of"
    And the MARC record has a 035 field with subfield "a" value "(AltRefNo)PP/MIA/1"
    And the MARC record has a 982 field with subfield "a" value "110000001" and subfield "b" value "PP/MIA"
    When I transform the MARC record
    Then the work's collection_path.path is "axiell:PP|MIA/axiell:PP|MIA|1"
    And the work's collection_path.label is "PP/MIA/1"
    And the work's reference_number is "PP/MIA/1"

  Scenario: part_of mode keeps a trailing slash in an object number
    Given the Axiell collection path source is "part_of"
    And the MARC record has a 035 field with subfield "a" value "(AltRefNo)SA/CB/2/41/43/"
    And the MARC record has a 982 field with subfield "a" value "110000001" and subfield "b" value "SA/CB/2/41/"
    When I transform the MARC record
    Then the work's collection_path.path is "axiell:SA|CB|2|41|/axiell:SA|CB|2|41|43|"
    And the work's collection_path.label is "SA/CB/2/41/43/"

  Scenario: part_of mode does not need a RefNo
    Given the Axiell collection path source is "part_of"
    And the MARC record's only 035 field with subfield "a" value "(AltRefNo)PP/MIA/1"
    And the MARC record has a 982 field with subfield "a" value "110000001" and subfield "b" value "PP/MIA"
    When I transform the MARC record
    Then the work's collection_path.path is "axiell:PP|MIA/axiell:PP|MIA|1"

  Scenario: part_of mode uses the first of several 982 parent links
    Given the Axiell collection path source is "part_of"
    And the MARC record has a 035 field with subfield "a" value "(AltRefNo)PP/MIA/1"
    And the MARC record has a 982 field with subfield "a" value "110000001" and subfield "b" value "PP/MIA"
    And the MARC record has another 982 field with subfield "a" value "110000002" and subfield "b" value "PP/OTHER"
    When I transform the MARC record
    Then the work's collection_path.path is "axiell:PP|MIA/axiell:PP|MIA|1"
    And a warning "Record has more than one 982 parent link; using the first" is logged

  Scenario: part_of mode requires an object number
    Given the Axiell collection path source is "part_of"
    Then transforming the MARC record raises ValueError "Missing object number on work 'test001'."

  Scenario: part_of mode rejects a 982 without a parent object number
    Given the Axiell collection path source is "part_of"
    And the MARC record has a 035 field with subfield "a" value "(AltRefNo)PP/MIA/1"
    And the MARC record has a 982 field with subfield "a" value "110000001"
    Then transforming the MARC record raises ValueError "982 without a parent object number on work 'test001'."

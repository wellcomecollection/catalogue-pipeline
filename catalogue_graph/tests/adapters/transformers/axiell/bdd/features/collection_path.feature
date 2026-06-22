Feature: Collection path and reference number extraction from Axiell MARC records
  The collection path is built from the calm-ref-no (MARC 035 "(Calm RefNo)<value>") and
  the optional calm-altref-no (MARC 035 "(AltRefNo)<value>") other identifiers.
  The reference number mirrors the collection path label.
  - https://www.loc.gov/marc/bibliographic/bd035.html

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

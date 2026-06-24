Feature: Subjects extraction from Axiell MARC records
  Subjects are derived from MARC 653 $a (Index Term—Uncontrolled).
  - https://www.loc.gov/marc/bibliographic/bd653.html

  Background:
    Given a valid MARC record

  Scenario: No 653 field — no subjects
    When I transform the MARC record
    Then there are no subjects

  Scenario: Empty $a is ignored
    Given the MARC record has a 653 field with subfield "a" value ""
    When I transform the MARC record
    Then there are no subjects

  Scenario: Single subject
    Given the MARC record has a 653 field with subfield "a" value "Radiology"
    When I transform the MARC record
    Then the only subject has the label "Radiology"
    And the only subject has the type "Subject"
    And it has 1 concept
    And its only concept has the label "Radiology"
    And it has the type "Concept"

  Scenario: Trailing period is stripped from label
    Given the MARC record has a 653 field with subfield "a" value "Surgery."
    When I transform the MARC record
    Then the only subject has the label "Surgery"

  Scenario: Multiple subjects preserve order
    Given the MARC record has a 653 field with subfield "a" value "Radiology"
    And the MARC record has another 653 field with subfield "a" value "Oncology"
    And the MARC record has another 653 field with subfield "a" value "Pathology"
    When I transform the MARC record
    Then there are 3 subjects
    And the 1st subject has the label "Radiology"
    And the 2nd subject has the label "Oncology"
    And the 3rd subject has the label "Pathology"

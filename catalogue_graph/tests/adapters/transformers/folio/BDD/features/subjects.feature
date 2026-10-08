Feature: subjects (MARC 6xx)
  Subjects are built from the 600, 610, 611, 648, 650 and 651 headings, as for
  EBSCO records, but FOLIO keeps every heading whose second indicator names a
  thesaurus itself and drops every heading sourced from ǂ2 (second indicator 7),
  the adopted local vocabularies included.

  These scenarios mirror the second-indicator unit tests of the Scala Sierra
  transformer this replaces (SierraConceptSubjectsTest.scala,
  SierraPersonSubjectsTest.scala, SierraOrganisationSubjectsTest.scala),
  including their test data, so the two can be compared directly. Where the
  behaviour differs, the scenario says so.

  https://www.loc.gov/marc/bibliographic/bd650.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A heading with second indicator 7 is ignored
    Given the MARC record has a 650 field with indicators "" "7" with subfield "a" value "absence" and subfield "0" value "lcsh/123"
    And the MARC record has another 650 field with indicators "" "2" with subfield "a" value "abolition" and subfield "0" value "mesh/456"
    When I transform the MARC record
    Then the only subject has the label "abolition"
    And it has the source identifier value "mesh/456"
    And it has the source identifier type "nlm-mesh"
    And its only concept has the label "abolition"
    And it has the source identifier value "mesh/456"

  Scenario: A heading with second indicator 7 and no ǂ0 is ignored
    Given the MARC record has a 650 field with indicators "" "7" with subfield "a" value "abolition"
    When I transform the MARC record
    Then there are no subjects

  Scenario: A heading from an adopted local vocabulary is ignored too
    Unlike for EBSCO, a Homosaurus heading is dropped with every other ǂ2-sourced heading
      Given the MARC record has a 650 field with indicators "" "7" with subfield "a" value "Lesbian history" and subfield "2" value "homoit"
      When I transform the MARC record
      Then there are no subjects

  Scenario: A heading whose second indicator does not name a supported scheme is kept
    Based on Sierra b1000046x. Second indicator 4 means the source is not specified
      Given the MARC record has a 648 field with indicators "" "4" with subfield "a" value "19th century."
      When I transform the MARC record
      Then the only subject has the label "19th century"
      And it has the source identifier type "label-derived"
      And its only concept has the type "Period"

  Scenario: No identifier is taken from a personal name heading whose second indicator is not 0
    The Scala transformer gives this subject a label-derived identifier. Python reads
    second indicator 2 as MeSH for every heading type, so the ǂ0 is kept as a MeSH identifier
      Given the MARC record has a 600 field with indicators "" "2" with subfield "a" value "Gerry the Garlic" and subfield "0" value "mesh/456"
      When I transform the MARC record
      Then the only subject has the label "Gerry the Garlic"
      And its only concept has the label "Gerry the Garlic"
      And it has the source identifier type "nlm-mesh"
      And it has the source identifier value "mesh/456"

  Scenario: A blank second indicator does not imply LC Names
    The Scala Sierra transformer assumes LC Names when the second indicator is blank.
    Python leaves the identifier label-derived, so the ǂ0 is not used
      Given the MARC record has a 610 field with subfield "a" value "ACME Corp" and subfield "0" value "n81290903210"
      When I transform the MARC record
      Then the only subject has the label "ACME Corp"
      And it has the source identifier type "label-derived"
      And it has the source identifier value "acme corp"

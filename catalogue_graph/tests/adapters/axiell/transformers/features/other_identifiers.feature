Feature: Other Identifiers extraction from MARC records
  Other Identifiers are derived from the MARC 035 field
  - https://www.loc.gov/marc/bibliographic/bd035.html

  Background:
    Given a valid MARC record

  Scenario Outline: A single other identifier
    Given the MARC record has a 035 field with subfield "a" value "<alternative_number>"
    When I transform the MARC record
    Then the only other identifier has the value "<other_identifier>"
    And the only other identifier has the identifier_type.id "<scheme>"
    Examples:
      | alternative_number                 | scheme                    | other_identifier |
      | (Bibliographic Number)b11839053    | sierra-system-number      | b11839053        |
      | (Sierra Number)i12056868           | sierra-identifier         | i12056868        |
      | (Mimsy reference)WELL-55           | mimsy-reference           | WELL-55          |
      | (WI number)L0023438                | miro-image-number         | L0023438         |
      | (accession number)172              | wellcome-accession-number | 172              |
      | (Library Reference Number)20385i.3 | iconographic-number       | 20385i.3         |
      | (Library Reference Number)20385i   | iconographic-number       | 20385i           |
      | (Library Reference Number)BA/NA/NA | calm-alt-ref-no           | BA/NA/NA         |

  Scenario: multiple other identifiers
    Given the MARC record has a 035 field with subfield "a" value "(Bibliographic Number)b11839053"
    And the MARC record has a 035 field with subfield "a" value "(Mimsy reference)WELL-55"
    When I transform the MARC record
    Then there are 2 other identifiers

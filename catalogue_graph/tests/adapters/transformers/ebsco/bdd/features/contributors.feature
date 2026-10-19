Feature: Extracting contributors from 1xx and 7xx name fields
  Contributors are derived from the main entry and added entry personal, corporate and
  meeting names: 100, 110, 111, 700, 710 and 711. The rules are shared with the FOLIO
  transformer, whose feature file ports the Scala tests; these scenarios cover rules the
  Scala tests leave unchecked.
  https://www.loc.gov/marc/bibliographic/bd1xx.html
  https://www.loc.gov/marc/bibliographic/bd70x75x.html

  Background:
    Given a valid MARC record

  Scenario: The contributor itself has no identifier, only its agent does
    Given the MARC record has a 100 field with subfield "a" value "Jane Example"
    When I transform the MARC record
    Then the only contributor has the agent.label "Jane Example"
    And it has the id.type "Unidentifiable"
    And it has the agent.id.type "Identifiable"

  Scenario: Only a trailing comma is trimmed from a name, not other punctuation
    Given the MARC record has a 100 field with subfield "a" value "Trailing Period." and subfield "b" value "Comma, and Space ;" and subfield "c" value "Colon:"
    When I transform the MARC record
    Then the only contributor has the agent.label "Trailing Period. Comma, and Space ; Colon:"

  Scenario: Only a Person's identifier derives from the trimmed label
    Given the MARC record has a 100 field with subfield "a" value "SCC,"
    And the MARC record has a 110 field with subfield "a" value "SCC,"
    And the MARC record has a 111 field with subfield "a" value "SCC Congress,"
    When I transform the MARC record
    Then the work has 3 contributors with agent.label:
      | SCC          |
      | SCC          |
      | SCC Congress |
    And the work has 3 contributors with agent.id.source_identifier.value:
      | scc           |
      | scc,          |
      | scc congress, |

  Scenario: Only a trailing full stop is trimmed from a role, not other punctuation
    Given the MARC record has a 700 field with subfield "a" value "Randolph" and subfield "e" value "Editor." and subfield "e" value "Translator:"
    When I transform the MARC record
    Then the only contributor has the agent.label "Randolph"
    And its 1st role has the label "Editor"
    And its 2nd role has the label "Translator:"

Feature: Description extraction from Axiell MARC records
  Description is derived from MARC 520 $a (Summary, etc.).
  Multiple 520 fields are joined with spaces.
  HTML is sanitised via normalise_text.
  - https://www.loc.gov/marc/bibliographic/bd520.html

  Background:
    Given a valid MARC record

  Scenario: No 520 field — description is absent
    When I transform the MARC record
    Then the work's description is absent

  Scenario: Single 520 field
    Given the MARC record has a 520 field with subfield "a" value "A collection of correspondence."
    When I transform the MARC record
    Then the work's description is "A collection of correspondence."

  Scenario: Multiple 520 fields are joined by space
    Given the MARC record has a 520 field with subfield "a" value "First summary."
    And the MARC record has another 520 field with subfield "a" value "Second summary."
    When I transform the MARC record
    Then the work's description is "First summary. Second summary."

  Scenario: Permitted HTML tags are retained
    Given the MARC record has a 520 field with subfield "a" value "Contains <em>important</em> material."
    When I transform the MARC record
    Then the work's description is "Contains <em>important</em> material."

  Scenario: Disallowed HTML tags are stripped but text is kept
    Given the MARC record has a 520 field with subfield "a" value "See <div>attached</div> list."
    When I transform the MARC record
    Then the work's description is "See attached list."

  Scenario: Whitespace-only 520 $a produces no description
    Given the MARC record has a 520 field with subfield "a" value "   "
    When I transform the MARC record
    Then the work's description is absent

Feature: Extracting genres from MARC 655
  The genre list is derived from every non-empty 655 $a subfield in order of appearance.

  Background:
    Given a valid MARC record

  Scenario: No genres
    When I transform the MARC record
    Then there are no genres

  Scenario: A simple genre
    Given the MARC record has a 655 field with subfield "a" value "Disco Polo"
    Then the only genre has the label "Disco Polo"
    And its identifier value is "disco polo"
    And its identifier type is "Genre"
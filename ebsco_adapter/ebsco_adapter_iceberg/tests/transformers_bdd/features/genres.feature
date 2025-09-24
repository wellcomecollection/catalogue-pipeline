Feature: Extracting genres from MARC 655
  The genre list is derived from every non-empty 655 $a subfield in order of appearance.

  Background:
    Given a valid MARC record

  Scenario: No genres
    When I transform the MARC record
    Then there are no genres

  Scenario: A poorly formed genre
  "a" is a non-repeating field. if there is more than one,
  it should be discarded and an error logged
    Given the MARC record has a 655 field with subfield "a" value "Disco Polo" and "a" value "Rominimal"
    When I transform the MARC record
    Then an error "Repeated Non-repeating field $a found in 655 field" is logged
    And there are no genres

  Scenario: A simple genre
    Given the MARC record has a 655 field with subfield "a" value "Disco Polo"
    When I transform the MARC record
    Then the only genre has the label "Disco Polo"
    And the genre has 1 concept
    And the concept has an identifier with value "disco polo"
    And the identifier's ontology type is "Genre"
    And its identifier's identifier type is "label-derived"

  Scenario: Multiple genres
    Given the MARC record has a 655 field with subfield "a" value "Disco Polo"
    And the MARC record has another 655 field with subfield "a" value "Doomcore"
    And the MARC record has another 655 field with subfield "a" value "Nu-Cumbia"
    When I transform the MARC record
    Then there are 3 genres
    Then the 1st genre has the label "Disco Polo"
    Then the 2st genre has the label "Doomcore"
    Then the 3st genre has the label "Nu-Cumbia"

  Scenario: v, x, y, and z subfields
  The label is made from subfields a,v,x,y,z - and each subdivision field
  yields its own concept
    Given the MARC record has a 655 field with subfield "a" value "Disco Polo"
    And the 655 field has a subfield "v" with value "Specimens"
    And the 655 field has a subfield "x" with value "Literature"
    And the 655 field has a subfield "y" with value "1897-1900"
    And the 655 field has a subfield "z" with value "Dublin."
    When I transform the MARC record
    Then the only genre has the label "Disco Polo Specimens Literature 1897-1900 Dublin."
    And the genre has 5 concepts
    And the 1st concept has the label "Disco Polo"
    And the 2nd concept has the label "Specimens"
    And the 3rd concept has the label "Literature"
    And the 4th concept has the label "1897-1900"
    And the 5th concept has the label "Dublin"

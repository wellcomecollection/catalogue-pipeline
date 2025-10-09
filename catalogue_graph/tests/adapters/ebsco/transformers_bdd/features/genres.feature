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
    Given the MARC record has a 655 field with subfield "a" value "Disco Polo" and subfield "a" value "Rominimal"
    When I transform the MARC record
    Then an error "Repeated Non-repeating field $a found in 655 field" is logged
    And there are no genres

  Scenario: A simple genre
    Given the MARC record has a 655 field with subfield "a" value "Disco Polo"
    When I transform the MARC record
    Then the only genre has the label "Disco Polo"
    And that genre has 1 concept
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
    Given the MARC record has a 655 field with subfields:
      | code | value      |
      | a    | Disco Polo |
      | v    | Specimens  |
      | x    | Literature |
      | y    | 1897-1900  |
      | z    | Dublin.    |
    When I transform the MARC record
    Then the only genre has the label "Disco Polo Specimens Literature 1897-1900 Dublin."
    And that genre has 5 concepts
    And the 1st concept has the label "Disco Polo"
    And the 2nd concept has the label "Specimens"
    And the 3rd concept has the label "Literature"
    And the 4th concept has the label "1897-1900"
    And the 5th concept has the label "Dublin"


  Scenario: subfield a comes first
  Regardless of where it is in the subfield list, $a is always the first concept
  and the first part of the label
    Given the MARC record has a 655 field with subfield "v" value "Specimens"
    And that field has a subfield "x" with value "Literature"
    And that field has a subfield "y" with value "1897-1900"
    And that field has a subfield "a" with value "Euskal Reggae"
    And that field has a subfield "z" with value "Dublin."
    When I transform the MARC record
    Then the only genre has a label starting with "Euskal Reggae"
    And the 1st concept has the label "Euskal Reggae"

  Scenario Outline: subdivision fields preserve document order
    Given the MARC record has a 655 field with subfield "a" value "a"
    And that field has a subfield "<code1>" with value "<code1>"
    And that field has a subfield "<code2>" with value "<code2>"
    And that field has a subfield "<code3>" with value "<code3>"
    And that field has a subfield "<code4>" with value "<code4>"
    When I transform the MARC record
    Then the only genre has the label "a <code1> <code2> <code3> <code4>"
    Examples:
      | code1 | code2 | code3 | code4 |
      | x     | y     | z     | v     |
      | z     | y     | x     | x     |
      | y     | z     | v     | y     |

  Scenario Outline: subdivision types
    Given the MARC record has a 655 field with subfield "a" value "Disco Polo"
    And that field has a subfield "<code>" with value "<text>"
    When I transform the MARC record
    Then the only genre has the label "Disco Polo <text>"
    And that genre has 2 concepts
    And that genre's 1st concept has the label "Disco Polo"
    And that genre's 2nd concept has the label "<text>"
    And that genre's 2nd concept has the type "<type>"
    And that genre's 2nd concept has the identifier value "<id>"
    Examples:
      | code | text    | type    | id      |
      | v    | Form    | Concept | form    |
      | x    | General | Concept | general |
      | y    | Chrono  | Period  | chrono  |
      | z    | Geo     | Place   | geo     |


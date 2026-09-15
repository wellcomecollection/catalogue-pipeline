Feature: alternative titles (MARC 130/240/242/246)
  Alternative titles come from MARC 130, 240, 242 and 246, joining all of
  each field's subfields with a single space.

  246 with a second indicator of "6" is a caption title, which populates
  work:lettering rather than work:alternativeTitles.

  ǂ5 UkLW is a Wellcome-internal marker rather than part of the title, so it
  is dropped. Other ǂ5 values are kept.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (MarcAlternativeTitlesTest.scala, SierraAlternativeTitlesTest.scala),
  including its test data, so the two can be compared directly.

  https://www.loc.gov/marc/bibliographic/bd130.html
  https://www.loc.gov/marc/bibliographic/bd240.html
  https://www.loc.gov/marc/bibliographic/bd242.html
  https://www.loc.gov/marc/bibliographic/bd246.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A record with no 130, 240, 242 or 246 has no alternative titles
    Given the MARC record has a 251 field with subfield "a" value "Xigua"
    When I transform the MARC record
    Then there are no alternative titles

  Scenario: An empty field yields no alternative title
    Given the MARC record has a 130 field with subfield "a" value ""
    When I transform the MARC record
    Then there are no alternative titles

  Scenario: A field whose content is entirely filtered out yields no alternative title
    Given the MARC record has a 246 field with subfield "5" value "UkLW"
    When I transform the MARC record
    Then there are no alternative titles

  Scenario: A 246 with second indicator 6 is a caption title and is ignored
    Given the MARC record has a 246 field with indicators " " "6" with subfield "a" value "I am a caption"
    When I transform the MARC record
    Then there are no alternative titles

  Scenario Outline: An alternative title is extracted from <tag>
    Given the MARC record has a <tag> field with subfield "a" value "mafeesh"
    When I transform the MARC record
    Then the only alternative title is "mafeesh"

    Examples:
      | tag |
      | 130 |
      | 240 |
      | 242 |
      | 246 |

  Scenario: All subfields of 130 are concatenated in document order
    Given the MARC record has a 130 field with subfields:
      | code | value |
      | a    | A     |
      | d    | D     |
      | f    | F     |
      | g    | G     |
      | h    | H     |
      | k    | K     |
      | l    | L     |
      | m    | M     |
      | n    | N     |
      | o    | O     |
      | p    | P     |
      | r    | R     |
      | s    | S     |
      | t    | T     |
      | 0    | 0     |
      | 1    | 1     |
      | 2    | 2     |
      | 7    | 7     |
      | 8    | 8     |
    When I transform the MARC record
    Then the only alternative title is "A D F G H K L M N O P R S T 0 1 2 7 8"

  Scenario: All subfields of 240 are concatenated in document order
    Given the MARC record has a 240 field with subfields:
      | code | value |
      | a    | A     |
      | d    | D     |
      | f    | F     |
      | g    | G     |
      | h    | H     |
      | k    | K     |
      | l    | L     |
      | m    | M     |
      | n    | N     |
      | o    | O     |
      | p    | P     |
      | r    | R     |
      | s    | S     |
      | 0    | 0     |
      | 1    | 1     |
      | 2    | 2     |
      | 7    | 7     |
      | 8    | 8     |
    When I transform the MARC record
    Then the only alternative title is "A D F G H K L M N O P R S 0 1 2 7 8"

  Scenario: All subfields of 242 are concatenated in document order
    Given the MARC record has a 242 field with subfields:
      | code | value |
      | a    | A     |
      | b    | B     |
      | c    | C     |
      | h    | H     |
      | n    | N     |
      | p    | P     |
      | y    | Y     |
    When I transform the MARC record
    Then the only alternative title is "A B C H N P Y"

  Scenario: All subfields of 246 are concatenated in document order
    Given the MARC record has a 246 field with subfields:
      | code | value |
      | a    | A     |
      | b    | B     |
      | f    | F     |
      | g    | G     |
      | h    | H     |
      | i    | I     |
      | n    | N     |
      | p    | P     |
      | 5    | 5     |
      | 7    | 7     |
      | 8    | 8     |
    When I transform the MARC record
    Then the only alternative title is "A B F G H I N P 5 7 8"

  Scenario: A 246 ǂ5 of UkLW is dropped and a sibling ǂ5 is kept
    Given the MARC record has a 246 field with subfields:
      | code | value    |
      | a    | Pinakes  |
      | 5    | UkLW     |
      | 5    | Mouseion |
    When I transform the MARC record
    Then the only alternative title is "Pinakes Mouseion"

  Scenario: A ǂ5 whose content is not UkLW is kept
    Given the MARC record has a 246 field with indicators " " "1" with subfields:
      | code | value   |
      | a    | Apples  |
      | 5    | Oranges |
      | 5    | Carrots |
    When I transform the MARC record
    Then the only alternative title is "Apples Oranges Carrots"

  Scenario: Titles are returned in the order their fields appear in the record
    Given the MARC record has a 130 field with subfield "a" value "Bananas"
    And the MARC record has a 240 field with subfield "a" value "Apples"
    And the MARC record has a 246 field with subfield "a" value "Cherries"
    When I transform the MARC record
    Then the work has 3 alternative titles:
      | Bananas  |
      | Apples   |
      | Cherries |

  Scenario: Repeated fields with the same tag each contribute a title
    Given the MARC record has a 240 field with subfield "a" value "Apples"
    And the MARC record has another 240 field with subfield "a" value "Durian"
    When I transform the MARC record
    Then the work has 2 alternative titles:
      | Apples |
      | Durian |

  Scenario: Titles are extracted from all relevant fields
    Given the MARC record has a 130 field with subfield "a" value "I'm very well acquainted too"
    And the MARC record has a 240 field with subfield "a" value "with matters mathematical"
    And the MARC record has a 246 field with subfield "a" value "I understand equations"
    And the MARC record has another 246 field with subfield "a" value "both simple"
    And the MARC record has another 240 field with subfield "a" value "and quadratical"
    And the MARC record has another 130 field with subfield "a" value "About binomial theorem I am teeming with a lot o' news"
    And the MARC record has a 242 field with subfield "a" value "Ikh hob a klugn kop un ikh farshtey Einstein's teyoriye"
    When I transform the MARC record
    Then the work has 7 alternative titles:
      | I'm very well acquainted too                            |
      | with matters mathematical                               |
      | I understand equations                                  |
      | both simple                                             |
      | and quadratical                                         |
      | About binomial theorem I am teeming with a lot o' news  |
      | Ikh hob a klugn kop un ikh farshtey Einstein's teyoriye |

  Scenario: Duplicate alternative titles are not returned
    Given the MARC record has a 130 field with subfield "a" value "With many cheerful facts about the square of the hypotenuse"
    And the MARC record has a 240 field with subfield "a" value "With many cheerful facts about the square of the hypotenuse"
    And the MARC record has a 246 field with subfield "a" value "With many cheerful facts about the square of the hypotenuse"
    And the MARC record has a 242 field with subfield "a" value "With many cheerful facts about the square of the hypoten-potenuse"
    And the MARC record has another 246 field with subfield "a" value "With many cheerful facts about the square of the hypoten-potenuse"
    When I transform the MARC record
    Then the work has 2 alternative titles:
      | With many cheerful facts about the square of the hypotenuse       |
      | With many cheerful facts about the square of the hypoten-potenuse |

  Scenario: Only 246 filters on a second indicator of 6
    Given the MARC record has a 130 field with indicators " " "6" with subfield "a" value "I am not a caption"
    And the MARC record has a 246 field with indicators " " "6" with subfield "a" value "I am a caption"
    And the MARC record has a 240 field with indicators " " "6" with subfield "a" value "Nor am I"
    And the MARC record has a 242 field with indicators " " "6" with subfield "a" value "Heller ikkje meg"
    When I transform the MARC record
    Then the work has 3 alternative titles:
      | I am not a caption |
      | Nor am I           |
      | Heller ikkje meg   |

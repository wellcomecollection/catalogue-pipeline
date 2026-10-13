Feature: parents (MARC 440, 490, 773 and 830)
  A work's parents are the series it belongs to. They are read from four
  MARC fields, each of which yields one Series relation with its title taken
  from a single subfield: 440 ǂa, 490 ǂa, 773 ǂt (falling back to ǂa, then ǂs)
  and 830 ǂt (falling back to ǂa). 830 ǂs means Version, so it is never used
  as a title. Trailing separators (";" and ",") and whitespace are removed,
  empty titles are dropped, and duplicate titles are kept once.

  These scenarios mirror the unit tests of the Scala transformers this
  replaces (MarcParentsTest.scala, SierraParentsTest.scala), including their
  test data, so the two can be compared directly. Deliberate divergences are
  grouped at the end.

  https://www.loc.gov/marc/bibliographic/bd4xx.html
  https://www.loc.gov/marc/bibliographic/bd773.html
  https://www.loc.gov/marc/bibliographic/bd830.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  # Ported from MarcParentsTest.scala.

  Scenario Outline: The title is taken from the one title subfield of each field
    Given the MARC record has a <tag> field with subfield "<code>" value "A title from <tag>ǂ<code>"
    When I transform the MARC record
    Then the only ancestor has the title "A title from <tag>ǂ<code>"
    And it has the work type "Series"

    Examples:
      | tag | code |
      | 440 | a    |
      | 490 | a    |
      | 773 | t    |
      | 773 | a    |
      | 773 | s    |
      | 830 | t    |
      | 830 | a    |

  Scenario: An 830 with both ǂt and ǂa uses ǂt
    Given the MARC record has a 830 field with subfield "t" value "A title from 830ǂt" and subfield "a" value "A title from 830ǂa"
    When I transform the MARC record
    Then the only ancestor has the title "A title from 830ǂt"

  Scenario: An 830 with both ǂa and ǂs uses ǂa, because 830 ǂs is a version
    Given the MARC record has a 830 field with subfield "a" value "A title from 830ǂa" and subfield "s" value "A title from 830ǂs"
    When I transform the MARC record
    Then the only ancestor has the title "A title from 830ǂa"

  Scenario: Each relevant field contributes a parent, in record order
    Given the MARC record has a 440 field with subfield "a" value "A title from 440ǂa"
    And the MARC record has a 490 field with subfield "a" value "A title from 490ǂa"
    When I transform the MARC record
    Then the work has 2 ancestors with title:
      | A title from 440ǂa |
      | A title from 490ǂa |

  Scenario: A field with no title subfield yields no parent
    Given the MARC record has a 830 field with subfield "x" value "9999-9999"
    When I transform the MARC record
    Then there are no ancestors
    And a warning "No Series relationship found" is logged

  # Ported from SierraParentsTest.scala. The Scala test for "prefers titles
  # from a subfield over field content" has no equivalent, as MARC XML fields
  # have no content outside their subfields. Its "no relevant MARC tags" test
  # uses a 999 field, which here identifies the record, so a 500 is used.

  Scenario: A record with no relevant MARC tags has no parents
    Given the MARC record has a 500 field with subfield "a" value "banana"
    When I transform the MARC record
    Then there are no ancestors

  Scenario Outline: A 773 Host Item Entry takes its title from ǂt, ǂa or ǂs
    Given the MARC record has a 773 field with subfield "<code>" value "A Series"
    When I transform the MARC record
    Then the only ancestor has the title "A Series"

    Examples:
      | code |
      | t    |
      | a    |
      | s    |

  Scenario: A 773 with several title subfields uses ǂt
    Given the MARC record has a 773 field with subfield "t" value "The Series" and subfield "a" value "A Series" and subfield "s" value "Some Series"
    When I transform the MARC record
    Then the only ancestor has the title "The Series"

  Scenario Outline: Every relevant field can take its title from ǂa
    Given the MARC record has a <tag> field with subfield "a" value "A Series"
    When I transform the MARC record
    Then the only ancestor has the title "A Series"

    Examples:
      | tag |
      | 440 |
      | 490 |
      | 773 |
      | 830 |

  Scenario: The 830 ǂs subfield is not a title
    Given the MARC record has a 830 field with subfield "t" value "A Series" and subfield "s" value "A Version"
    When I transform the MARC record
    Then the only ancestor has the title "A Series"

  Scenario Outline: A repeated relevant field yields a parent for each occurrence
    Given the MARC record has a <tag> field with subfield "a" value "A Series"
    And the MARC record has another <tag> field with subfield "a" value "Another Series"
    And the MARC record has another <tag> field with subfield "a" value "A Host"
    And the MARC record has another <tag> field with subfield "a" value "Yet Another Series"
    When I transform the MARC record
    Then the work has 4 ancestors with title:
      | A Series |
      | Another Series |
      | A Host |
      | Yet Another Series |

    Examples:
      | tag |
      | 440 |
      | 490 |
      | 773 |
      | 830 |

  Scenario: Duplicate titles are kept once
    Given the MARC record has a 440 field with subfield "a" value "A Series"
    And the MARC record has a 773 field with subfield "t" value "A Series"
    And the MARC record has a 830 field with subfield "t" value "Another Series"
    When I transform the MARC record
    Then the work has 2 ancestors with title:
      | A Series |
      | Another Series |

  Scenario: Fields with no content are ignored
    Given the MARC record has a 440 field with subfield "a" value "A Series"
    And the MARC record has a 490 field with subfield "a" value ""
    And the MARC record has another 490 field with subfield "a" value " ;"
    And the MARC record has a 830 field with no subfields
    And the MARC record has another 830 field with subfield "t" value "Another Series"
    When I transform the MARC record
    Then the work has 2 ancestors with title:
      | A Series |
      | Another Series |

  Scenario: Subfields giving a part name or number are ignored
    Given the MARC record has a 773 field with subfield "t" value "A Host" and subfield "w" value "page 2"
    And the MARC record has a 830 field with subfield "t" value "A Big Series" and subfield "v" value "vol. 2"
    When I transform the MARC record
    Then the work has 2 ancestors with title:
      | A Host |
      | A Big Series |

  Scenario Outline: A trailing subfield separator is removed from the title
    Given the MARC record has a 830 field with subfield "t" value "Published papers<suffix> (Wellcome Chemical Research Laboratories) <suffix>"
    When I transform the MARC record
    Then the only ancestor has the title "Published papers<suffix> (Wellcome Chemical Research Laboratories)"

    Examples:
      | suffix |
      | ;      |
      | ,      |

  # Not in the Scala tests. The Scala searches ǂt, then ǂa, then ǂs regardless
  # of their order in the field; these scenarios pin that order down, since a
  # 773 normally carries the host's author in ǂa ahead of its title in ǂt.

  Scenario: A 773 with ǂa before ǂt uses the title from ǂt
    Given the MARC record has a 773 field with subfield "a" value "Munk, William, 1816-1898." and subfield "t" value "The roll of the Royal College of Physicians of London"
    When I transform the MARC record
    Then the only ancestor has the title "The roll of the Royal College of Physicians of London"

  Scenario: A 773 with ǂs before ǂa uses the title from ǂa
    Given the MARC record has a 773 field with subfield "s" value "A title from 773ǂs" and subfield "a" value "A title from 773ǂa"
    When I transform the MARC record
    Then the only ancestor has the title "A title from 773ǂa"

  # The Scala transformer keeps parents in the order the fields arrive. In
  # production they arrive from the Sierra API, which lists fields grouped by
  # Sierra field tag, so the 773 host item comes before the 4xx and 830 series
  # fields. The Scala test builds its fields by hand in 440, 490, 773, 830 order
  # and expects that order back. FOLIO records are in MARC tag order, and the
  # FOLIO builder puts the 773 first to match the works it replaces, so this
  # scenario expects the host item first instead.

  Scenario: Several different relevant fields each yield a parent, host item first
    Given the MARC record has a 440 field with subfield "a" value "A Series"
    And the MARC record has a 490 field with subfield "a" value "Another Series"
    And the MARC record has a 773 field with subfield "t" value "A Host"
    And the MARC record has a 830 field with subfield "a" value "Yet Another Series"
    When I transform the MARC record
    Then the work has 4 ancestors with title:
      | A Host |
      | A Series |
      | Another Series |
      | Yet Another Series |

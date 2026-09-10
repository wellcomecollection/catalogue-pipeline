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

  # Ported from MarcAlternativeTitlesTest.scala.

  Scenario: A record with none of the four fields has no alternative titles
    Given the MARC record has a 999 field with subfield "a" value "mafeesh"
    When I transform the MARC record
    Then there are no alternative titles

  Scenario: An alternative title is taken from 130
    Given the MARC record has a 130 field with subfield "a" value "Memoirs of Sundry Transactions from the World in the Moon"
    When I transform the MARC record
    Then the only alternative title is "Memoirs of Sundry Transactions from the World in the Moon"

  Scenario: An alternative title is taken from 240
    Given the MARC record has a 240 field with indicators "1" "0" with subfield "a" value "Velikosvetskie obedy."
    When I transform the MARC record
    Then the only alternative title is "Velikosvetskie obedy."

  Scenario: An alternative title is taken from 246
    Given the MARC record has a 246 field with indicators "3" "1" with subfield "a" value "Poetry of skiing"
    When I transform the MARC record
    Then the only alternative title is "Poetry of skiing"

  Scenario: An alternative title is taken from 242
    Given the MARC record has a 242 field with indicators "0" "0" with subfield "a" value "Morbid changes in the walls of veins in arteriosclerosis"
    When I transform the MARC record
    Then the only alternative title is "Morbid changes in the walls of veins in arteriosclerosis"

  Scenario: All of a field's subfields are joined, in the order they appear
    Given the MARC record has a 130 field with subfield "a" value "What You Will" and subfield "r" value "in G flat Major" and subfield "l" value "with Ayapeneco subtitles"
    When I transform the MARC record
    Then the only alternative title is "What You Will in G flat Major with Ayapeneco subtitles"

  # 242 ǂy is the language code of the translated title. Joining every subfield
  # means it lands in the title text, as it does in the Scala.

  Scenario: 242 ǂy is joined into the title like any other subfield
    Given the MARC record has a 242 field with indicators "0" "0" with subfield "a" value "Ways to prevent live burials." and subfield "y" value "eng"
    When I transform the MARC record
    Then the only alternative title is "Ways to prevent live burials. eng"

  Scenario: A 246 caption title (second indicator "6") is excluded
    Given the MARC record has a 130 field with subfield "a" value "Westminster review (London, England : 1852)"
    And the MARC record has another 246 field with indicators "0" "6" with subfield "a" value "Westminster and foreign quarterly review"
    When I transform the MARC record
    Then the only alternative title is "Westminster review (London, England : 1852)"

  Scenario: A record with nothing but caption titles has no alternative titles
    Given the MARC record has a 246 field with indicators "0" "6" with subfield "a" value "This is a caption"
    And the MARC record has another 246 field with indicators "1" "6" with subfield "a" value "Another caption"
    When I transform the MARC record
    Then there are no alternative titles

  Scenario: ǂ5 UkLW is dropped from the title
    Given the MARC record has a 246 field with indicators "1" " " with subfields:
      | code | value                                  |
      | i    | Previous title, replaced January 2025: |
      | a    | Marseilles: Plague, 1721               |
      | 5    | UkLW                                   |
    When I transform the MARC record
    Then the only alternative title is "Previous title, replaced January 2025: Marseilles: Plague, 1721"

  Scenario: A field whose only content is ǂ5 UkLW yields no alternative title
    Given the MARC record has a 246 field with subfield "5" value "UkLW"
    When I transform the MARC record
    Then there are no alternative titles

  # Only an exact match is dropped. Wellcome's own records hold ǂ5 values
  # belonging to other institutions, which are part of the title as catalogued.

  Scenario: A ǂ5 value other than UkLW is kept
    Given the MARC record has a 246 field with indicators "1" "8" with subfields:
      | code | value                 |
      | a    | Papers on ventilation |
      | 5    | DNLM                  |
    When I transform the MARC record
    Then the only alternative title is "Papers on ventilation DNLM"

  # A deliberate improvement on the Scala, which joins ǂ6 into the title and so
  # emits titles prefixed with a linkage number, e.g. "880-03 Sokohi".

  Scenario: ǂ6, which links to an 880 field, is dropped
    Given the MARC record has a 240 field with indicators "1" "0" with subfields:
      | code | value                 |
      | 6    | 880-02                |
      | a    | Velikosvetskie obedy. |
      | l    | English               |
    When I transform the MARC record
    Then the only alternative title is "Velikosvetskie obedy. English"

  Scenario: An empty field yields no alternative title
    Given the MARC record has a 130 field with subfield "a" value ""
    When I transform the MARC record
    Then there are no alternative titles

  Scenario: A field devoid of useful content yields no alternative title
    Given the MARC record has a 130 field with subfield "a" value "     "
    When I transform the MARC record
    Then there are no alternative titles

  Scenario: Titles are returned in the order their fields appear in the record
    Given the MARC record has a 246 field with indicators "3" "1" with subfield "a" value "Poetry of skiing"
    And the MARC record has another 130 field with subfield "a" value "What You Will"
    And the MARC record has another 242 field with indicators "0" "0" with subfield "a" value "Calomel in dropsies"
    And the MARC record has another 240 field with indicators "0" "0" with subfield "a" value "Your Own Thing"
    When I transform the MARC record
    Then the work has 4 alternative titles:
      | Poetry of skiing    |
      | What You Will       |
      | Calomel in dropsies |
      | Your Own Thing      |

  Scenario: The same title from different fields is deduplicated
    Given the MARC record has a 130 field with subfield "a" value "What You Will"
    And the MARC record has another 240 field with indicators "0" "0" with subfield "a" value "What You Will"
    And the MARC record has another 246 field with indicators "0" "0" with subfield "a" value "What You Will"
    And the MARC record has another 246 field with indicators "0" "0" with subfield "a" value "Motocrossed"
    When I transform the MARC record
    Then the work has 2 alternative titles:
      | What You Will |
      | Motocrossed   |

  # A deliberate improvement on the Scala, which neither trims the joined value
  # nor deduplicates on the trimmed one, and so returns both of these.

  Scenario: Titles differing only by surrounding whitespace are one title
    Given the MARC record has a 130 field with subfield "a" value "Motocrossed"
    And the MARC record has another 246 field with indicators "0" "0" with subfield "a" value "  Motocrossed  "
    When I transform the MARC record
    Then the only alternative title is "Motocrossed"

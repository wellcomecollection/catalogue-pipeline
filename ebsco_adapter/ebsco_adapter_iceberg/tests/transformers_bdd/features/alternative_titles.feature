Feature: MARC record alternative titles extraction
  Alternative Titles are derived from the MARC 130, 240 and 246 fields
  - http://www.loc.gov/marc/bibliographic/bd130.html
  - https://www.loc.gov/marc/bibliographic/bd240.html
  - https://www.loc.gov/marc/bibliographic/bd246.html


  Background:
    Given a valid MARC record

  Scenario: No alternative titles present
    When I transform the MARC record
    Then there are no alternative titles

  Scenario: Single alternative title (130)
    Given the MARC record has a 130 field with subfield "a" value "Memoirs of Sundry Transactions from the World in the Moon"
    When I transform the MARC record
    Then there are 1 alternative titles
    And the 1st alternative title is "Memoirs of Sundry Transactions from the World in the Moon"

  Scenario: Excludes caption titles (246 with 2nd indicator 6)
    Given the MARC record has a 130 field with subfield "a" value "Westminster review (London, England : 1852)"
    And the MARC record has another 246 field with indicators "0" "6" with subfield "a" value "Westminster and foreign quarterly review"
    When I transform the MARC record
    Then there are 1 alternative titles
    And the 1st alternative title is "Westminster review (London, England : 1852)"

  Scenario: Only caption titles (should yield none)
    Given the MARC record has a 246 field with indicators "0" "6" with subfield "a" value "This is a caption"
    And the MARC record has another 246 field with indicators "1" "6" with subfield "a" value "Another caption"
    When I transform the MARC record
    Then there are 0 alternative titles

  Scenario: Whitespace-only title should be ignored
    Given the MARC record has a 130 field with subfield "a" value "     "
    When I transform the MARC record
    Then there are 0 alternative titles

  Scenario: Multiple alternative titles across fields
    Given the MARC record has a 245 field with subfield "a" value "Twelfth Night"
    And the MARC record has another 130 field with subfield "a" value "What You Will"
    And the MARC record has another 240 field with indicators "0" "0" with subfield "a" value "Your Own Thing"
    And the MARC record has another 246 field with indicators "0" "6" with subfield "a" value "THIS IS A CAPTION"
    And the MARC record has another 246 field with indicators "0" "0" with subfield "a" value "Just One of the Guys"
    And the MARC record has another 246 field with indicators "0" "0" with subfield "a" value "Motocrossed"
    When I transform the MARC record
    Then there are 4 alternative titles
    And the 1st alternative title is "What You Will"
    And the 2nd alternative title is "Your Own Thing"
    And the 3rd alternative title is "Just One of the Guys"
    And the 4th alternative title is "Motocrossed"

  Scenario: Deduplicates alternative titles
    Given the MARC record has a 130 field with subfield "a" value "What You Will"
    And the MARC record has another 240 field with indicators "0" "0" with subfield "a" value "What You Will"
    And the MARC record has another 246 field with indicators "0" "0" with subfield "a" value "What You Will"
    And the MARC record has another 246 field with indicators "0" "0" with subfield "a" value "Motocrossed"
    When I transform the MARC record
    Then there are 2 alternative titles
    And the 1st alternative title is "What You Will"
    And the 2nd alternative title is "Motocrossed"

  Scenario: Duplicate titles differing only by surrounding spaces
    Given the MARC record has a 130 field with subfield "a" value "Motocrossed"
    And the MARC record has another 246 field with indicators "0" "0" with subfield "a" value "  Motocrossed  "
    When I transform the MARC record
    Then there are 1 alternative titles
    And the 1st alternative title is "Motocrossed"

  Scenario: Alternative title constructed from multiple subfields
    Given the MARC record has a 130 field with subfield "a" value "What You Will" with subfield "r" value "in G flat Major" with subfield "l" value "with Ayapeneco subtitles"
    When I transform the MARC record
    Then there are 1 alternative titles
    And the 1st alternative title is "What You Will in G flat Major with Ayapeneco subtitles"
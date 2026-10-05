Feature: production (MARC 260, 264 and 008)
  Production events come from MARC 260 and 264. ǂa gives places, ǂb gives
  agents and ǂc gives dates; the event label is every subfield joined with
  a space. A 260 with ǂe, ǂf or ǂg adds those as further places, agents and
  dates and marks the event as Manufacture. A 264's second indicator gives
  the function: 0 Production, 1 Publication, 2 Distribution, 3 Manufacture.
  Copyright statements (indicator 4) and 264s with a blank indicator are
  ignored. When a record has both 260 and 264, the 264s are used. When the
  chosen events have no parseable date, the 008 supplies the date range, and
  a record with neither 260 nor 264 gets one event from the 008 alone.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (SierraProductionTest.scala), including its test data, so the two
  can be compared directly. Deliberate divergences are grouped at the end.

  https://www.loc.gov/marc/bibliographic/bd260.html
  https://www.loc.gov/marc/bibliographic/bd264.html
  https://www.loc.gov/marc/bibliographic/bd008a.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: Neither 260 nor 264 gives no production events
    When I transform the MARC record
    Then there are no productions

  Scenario: 260 ǂa gives places
    Given the MARC record has a 260 field with subfield "a" value "Paris" and subfield "a" value "London"
    When I transform the MARC record
    Then the only production has the label "Paris London"
    And it has 2 places
    And its 1st place has the label "Paris"
    And its 2nd place has the label "London"

  Scenario: 260 ǂb gives agents
    Given the MARC record has a 260 field with subfield "b" value "Gauthier-Villars ;" and subfield "b" value "Vogue"
    When I transform the MARC record
    Then the only production has the label "Gauthier-Villars ; Vogue"
    And it has 2 agents
    And its 1st agent has the label "Gauthier-Villars ;"
    And its 2nd agent has the label "Vogue"

  Scenario: 260 ǂc gives dates
    Given the MARC record has a 260 field with subfield "c" value "1955" and subfield "c" value "1984" and subfield "c" value "1999"
    When I transform the MARC record
    Then the only production has the label "1955 1984 1999"
    And it has 3 dates
    And its 1st date has the label "1955"
    And its 2nd date has the label "1984"
    And its 3rd date has the label "1999"
    And its 3rd date has the range.from_time "1999-01-01T00:00:00Z"
    And its 3rd date has the range.to_time "1999-12-31T23:59:59.999999999Z"

  Scenario: A 260 with only ǂa, ǂb and ǂc has no function
    Given the MARC record has a 260 field with subfield "a" value "New York" and subfield "b" value "Xerox Films" and subfield "c" value "1973"
    When I transform the MARC record
    Then the only production has the label "New York Xerox Films 1973"
    And it has no function

  Scenario: 260 ǂe adds places and makes the function Manufacture
    Given the MARC record has a 260 field with subfield "a" value "New York, N.Y." and subfield "e" value "Reston, Va." and subfield "e" value "[Philadelphia]"
    When I transform the MARC record
    Then the only production has the function.label "Manufacture"
    And it has 3 places
    And its 1st place has the label "New York, N.Y."
    And its 2nd place has the label "Reston, Va."
    And its 3rd place has the label "[Philadelphia]"

  Scenario: 260 ǂf adds agents and makes the function Manufacture
    Given the MARC record has a 260 field with subfield "b" value "Macmillan" and subfield "f" value "Sussex Tapes" and subfield "f" value "US Dept of Energy"
    When I transform the MARC record
    Then the only production has the function.label "Manufacture"
    And it has 3 agents
    And its 1st agent has the label "Macmillan"
    And its 2nd agent has the label "Sussex Tapes"
    And its 3rd agent has the label "US Dept of Energy"

  Scenario: 260 ǂg adds dates and makes the function Manufacture
    Given the MARC record has a 260 field with subfield "c" value "1981" and subfield "g" value "April 15, 1977" and subfield "g" value "1973 printing"
    When I transform the MARC record
    Then the only production has the function.label "Manufacture"
    And it has 3 dates
    And its 1st date has the label "1981"
    And its 2nd date has the label "April 15, 1977"
    And its 3rd date has the label "1973 printing"

  Scenario: Every 260 gives a production event
    Given the MARC record has a 260 field with subfields:
      | code | value                           |
      | a    | London :                        |
      | b    | Arts Council of Great Britain,  |
      | c    | 1976;                           |
      | e    | Twickenham :                    |
      | f    | CTD Printers,                   |
      | g    | 1974                            |
    And the MARC record has another 260 field with subfields:
      | code | value                                                                      |
      | a    | Bethesda, Md. :                                                            |
      | b    | Toxicology Information Program, National Library of Medicine [producer] ;  |
      | a    | Springfield, Va. :                                                         |
      | b    | National Technical Information Service [distributor],                      |
      | c    | 1974-                                                                      |
    When I transform the MARC record
    Then there are 2 productions
    And the 1st production has the label "London : Arts Council of Great Britain, 1976; Twickenham : CTD Printers, 1974"
    And it has the function.label "Manufacture"
    And its 1st place has the label "London"
    And its 2nd place has the label "Twickenham"
    And its 1st agent has the label "Arts Council of Great Britain"
    And its 2nd agent has the label "CTD Printers"
    And its 1st date has the label "1976;"
    And its 2nd date has the label "1974"
    And the 2nd production has the label "Bethesda, Md. : Toxicology Information Program, National Library of Medicine [producer] ; Springfield, Va. : National Technical Information Service [distributor], 1974-"
    And it has no function
    And its 1st place has the label "Bethesda, Md."
    And its 2nd place has the label "Springfield, Va."
    And its 1st agent has the label "Toxicology Information Program, National Library of Medicine [producer] ;"
    And its 2nd agent has the label "National Technical Information Service [distributor]"
    And its 1st date has the label "1974-"

  Scenario: 260 place and date labels lose their trailing punctuation
    Given the MARC record has a 260 field with subfield "a" value "Paris  : " and subfield "a" value "London :" and subfield "c" value "1984 . " and subfield "c" value "1999."
    When I transform the MARC record
    Then the only production has the label "Paris  :  London : 1984 .  1999."
    And its 1st place has the label "Paris"
    And its 2nd place has the label "London"
    And its 1st date has the label "1984"
    And its 2nd date has the label "1999"

  Scenario: 264 ǂa gives places
    Given the MARC record has a 264 field with indicators " " "1" with subfield "a" value "Boston" and subfield "a" value "Cambridge"
    When I transform the MARC record
    Then the only production has the label "Boston Cambridge"
    And its 1st place has the label "Boston"
    And its 2nd place has the label "Cambridge"

  Scenario: 264 ǂb gives agents
    Given the MARC record has a 264 field with indicators " " "1" with subfield "b" value "ABC Publishers" and subfield "b" value "Iverson Company"
    When I transform the MARC record
    Then the only production has the label "ABC Publishers Iverson Company"
    And its 1st agent has the label "ABC Publishers"
    And its 2nd agent has the label "Iverson Company"

  Scenario: 264 ǂc gives dates
    Given the MARC record has a 264 field with indicators " " "1" with subfield "c" value "2002" and subfield "c" value "1983" and subfield "c" value "copyright 2005"
    When I transform the MARC record
    Then the only production has the label "2002 1983 copyright 2005"
    And its 1st date has the label "2002"
    And its 2nd date has the label "1983"
    And its 3rd date has the label "copyright 2005"

  Scenario: A 264 copyright statement (second indicator 4) is ignored
    Given the MARC record has a 264 field with indicators " " "4" with subfield "c" value "copyright 2005"
    And the MARC record has another 264 field with indicators " " "3" with subfield "a" value "Cambridge :" and subfield "b" value "Kinsey Printing Company"
    When I transform the MARC record
    Then the only production has the label "Cambridge : Kinsey Printing Company"
    And it has the function.label "Manufacture"
    And its 1st place has the label "Cambridge"
    And its 1st agent has the label "Kinsey Printing Company"
    And it has 0 dates

  Scenario: A 264 with a blank second indicator is ignored
    Given the MARC record has a 264 field with indicators " " " " with subfield "c" value "copyright 2005"
    And the MARC record has another 264 field with indicators " " "3" with subfield "a" value "London :" and subfield "b" value "Wellcome Collection Publishing"
    When I transform the MARC record
    Then the only production has the label "London : Wellcome Collection Publishing"
    And it has the function.label "Manufacture"
    And its 1st place has the label "London"
    And its 1st agent has the label "Wellcome Collection Publishing"
    And it has 0 dates

  Scenario: Every 264 gives a production event
    Given the MARC record has a 264 field with indicators " " "1" with subfield "a" value "Columbia, S.C. :" and subfield "b" value "H.W. Williams Co.," and subfield "c" value "1982"
    And the MARC record has another 264 field with indicators " " "2" with subfield "a" value "Washington :" and subfield "b" value "U.S. G.P.O.," and subfield "c" value "1981-"
    When I transform the MARC record
    Then there are 2 productions
    And the 1st production has the label "Columbia, S.C. : H.W. Williams Co., 1982"
    And it has the function.label "Publication"
    And its 1st place has the label "Columbia, S.C."
    And its 1st agent has the label "H.W. Williams Co."
    And its 1st date has the label "1982"
    And the 2nd production has the label "Washington : U.S. G.P.O., 1981-"
    And it has the function.label "Distribution"
    And its 1st place has the label "Washington"
    And its 1st agent has the label "U.S. G.P.O."
    And its 1st date has the label "1981-"

  Scenario: 264 place, agent and date labels lose their trailing punctuation
    Given the MARC record has a 264 field with indicators " " "1" with subfield "a" value "Boston:" and subfield "a" value "Cambridge : " and subfield "b" value "ABC Publishers," and subfield "b" value "Iverson Ltd. , " and subfield "c" value "2002." and subfield "c" value "1983 ." and subfield "c" value "copyright 2005."
    When I transform the MARC record
    Then the only production has the label "Boston: Cambridge :  ABC Publishers, Iverson Ltd. ,  2002. 1983 . copyright 2005."
    And its 1st place has the label "Boston"
    And its 2nd place has the label "Cambridge"
    And its 1st agent has the label "ABC Publishers"
    And its 2nd agent has the label "Iverson Ltd."
    And its 1st date has the label "2002"
    And its 2nd date has the label "1983"
    And its 3rd date has the label "copyright 2005"

  Scenario: When both 260 and 264 are present, the 264 is used
    Given the MARC record has a 260 field with subfield "a" value "Paris"
    And the MARC record has a 264 field with indicators " " "0" with subfield "a" value "London"
    When I transform the MARC record
    Then the only production has the label "London"
    And it has the function.label "Production"
    And its 1st place has the label "London"

  Scenario: The 260 is used when the only 264 is a copyright statement
    Given the MARC record has a 260 field with subfield "a" value "Paris"
    And the MARC record has a 264 field with indicators " " "4" with subfield "a" value "London"
    When I transform the MARC record
    Then the only production has the label "Paris"
    And it has no function
    And its 1st place has the label "Paris"

  Scenario: Copyright and blank-indicator 264s beside a valid one are ignored without falling back to the 260
    Given the MARC record has a 260 field with subfield "a" value "Paris"
    And the MARC record has a 264 field with indicators " " "0" with subfield "a" value "London"
    And the MARC record has another 264 field with indicators " " "4" with subfield "a" value "Test" and subfield "b" value "Test" and subfield "c" value "Test"
    And the MARC record has another 264 field with indicators " " " " with subfield "a" value "Berlin"
    When I transform the MARC record
    Then the only production has the label "London"
    And it has the function.label "Production"
    And its 1st place has the label "London"

  Scenario: The 260 is used when the 264 only holds a copyright statement
    Given the MARC record has a 260 field with subfield "a" value "San Francisco :" and subfield "b" value "Morgan Kaufmann Publishers," and subfield "c" value "2004"
    And the MARC record has a 264 field with indicators " " " " with subfield "c" value "©2004"
    When I transform the MARC record
    Then the only production has the label "San Francisco : Morgan Kaufmann Publishers, 2004"
    And it has no function
    And its 1st place has the label "San Francisco"
    And its 1st agent has the label "Morgan Kaufmann Publishers"
    And its 1st date has the label "2004"

  Scenario: The 260 is used when a 264 with a blank indicator repeats it
    Given the MARC record has a 260 field with subfield "a" value "London :" and subfield "b" value "Wellcome Trust," and subfield "c" value "1992"
    And the MARC record has a 264 field with indicators " " " " with subfield "a" value "London :" and subfield "b" value "Wellcome Trust," and subfield "c" value "1992"
    When I transform the MARC record
    Then the only production has the label "London : Wellcome Trust, 1992"
    And it has no function
    And its 1st place has the label "London"
    And its 1st agent has the label "Wellcome Trust"
    And its 1st date has the label "1992"

  # Based on b31500018, as retrieved on 28 March 2019
  Scenario: The 260 is used when the 264 subfields only contain punctuation
    Given the MARC record has a 260 field with subfield "c" value "2019"
    And the MARC record has a 264 field with indicators " " " " with subfield "a" value ":" and subfield "b" value "," and subfield "c" value ""
    When I transform the MARC record
    Then the only production has the label "2019"
    And it has no function
    And it has 0 places
    And it has 0 agents
    And its 1st date has the label "2019"

  Scenario: The 008 alone gives a production event
    Given the MARC record's only 008 field with the value "790922s1757    enk||||      o00||||eng ccam   "
    When I transform the MARC record
    Then the only production has the label "1757"
    And it has no function
    And its 1st place has the label "England"
    And its 1st date has the label "1757"
    And its 1st date has the range.from_time "1757-01-01T00:00:00Z"
    And its 1st date has the range.to_time "1757-12-31T23:59:59.999999999Z"

  Scenario: The 008 is ignored when a 264 has a date
    Given the MARC record's only 008 field with the value "790922s1757    enk||||      o00||||eng ccam   "
    And the MARC record has a 264 field with indicators " " "1" with subfield "c" value "2002" and subfield "a" value "London"
    When I transform the MARC record
    Then the only production has the label "2002 London"
    And it has the function.label "Publication"
    And its 1st place has the label "London"
    And its 1st date has the label "2002"
    And its 1st date has the range.from_time "2002-01-01T00:00:00Z"

  Scenario: The 008 is ignored when a 260 has a date
    Given the MARC record's only 008 field with the value "790922s1757    enk||||      o00||||eng ccam   "
    And the MARC record has a 260 field with subfield "c" value "2002" and subfield "a" value "London"
    When I transform the MARC record
    Then the only production has the label "2002 London"
    And it has no function
    And its 1st place has the label "London"
    And its 1st date has the label "2002"
    And its 1st date has the range.from_time "2002-01-01T00:00:00Z"

  Scenario: The 008 supplies the date when the 260 has none
    Given the MARC record's only 008 field with the value "790922s1757    enk||||      o00||||eng ccam   "
    And the MARC record has a 260 field with subfield "a" value "London"
    When I transform the MARC record
    Then the only production has the label "London"
    And it has no function
    And its 1st place has the label "London"
    And its 1st date has the label "1757"
    And its 1st date has the range.from_time "1757-01-01T00:00:00Z"
    And its 1st date has the range.to_time "1757-12-31T23:59:59.999999999Z"

  # Example is from b28533306, with the 264 date modified so it is never parseable
  Scenario: The 008 supplies the range but the 264 keeps its date label when the date cannot be parsed
    Given the MARC record's only 008 field with the value "160323s1972    enk               ku    d"
    And the MARC record has a 264 field with indicators " " "0" with subfield "a" value "[Netherne, Surrey]," and subfield "c" value "B̷A̴D̸ ̴U̶N̸P̵A̸R̸S̷E̷A̶B̵L̶E̸ ̵N̴O̴N̶S̵E̷N̷S̴E̴"
    When I transform the MARC record
    Then the only production has the label "[Netherne, Surrey], B̷A̴D̸ ̴U̶N̸P̵A̸R̸S̷E̷A̶B̵L̶E̸ ̵N̴O̴N̶S̵E̷N̷S̴E̴"
    And it has the function.label "Production"
    And its 1st place has the label "[Netherne, Surrey],"
    And it has 1 date
    And its 1st date has the label "B̷A̴D̸ ̴U̶N̸P̵A̸R̸S̷E̷A̶B̵L̶E̸ ̵N̴O̴N̶S̵E̷N̷S̴E̴"
    And its 1st date has the range.from_time "1972-01-01T00:00:00Z"
    And its 1st date has the range.to_time "1972-12-31T23:59:59.999999999Z"

  # Everything below is a deliberate divergence from the Scala implementation.

  # The Scala test uses a 264 with no subfields, which gives an event with an
  # empty label. The Python pipeline drops events with an empty label, so
  # these scenarios add a place. The same applies to a 260 whose only
  # subfield is empty, which the Scala keeps as an event with an empty label
  # and an empty date.
  Scenario Outline: The second indicator of a 264 gives the function
    Given the MARC record has a 264 field with indicators " " "<ind2>" with subfield "a" value "London"
    When I transform the MARC record
    Then the only production has the function.label "<function>"

    Examples:
      | ind2 | function     |
      | 0    | Production   |
      | 1    | Publication  |
      | 2    | Distribution |
      | 3    | Manufacture  |

  # The Scala fails the whole record with a CataloguingException. The Python
  # pipeline logs an error and skips the field. Neither Sierra nor FOLIO holds
  # such a record.
  Scenario: A 264 with an unrecognised second indicator is skipped
    Given the MARC record has a 264 field with indicators " " "x" with subfield "a" value "London"
    When I transform the MARC record
    Then there are no productions
    And an error "Unrecognised second indicator for production function" is logged with indicator2 "x"

  Scenario: A 260 whose only subfield is empty gives no production event
    Given the MARC record has a 260 field with subfield "c" value ""
    And the MARC record's only 008 field with the value "040325xx                  engdd         ntduua"
    When I transform the MARC record
    Then there are no productions

  # The Scala ignores the 008 altogether when a record has more than one.
  Scenario: The first of two 008 fields is used
    Given the MARC record's only 008 field with the value "790922s1757    enk||||      o00||||eng ccam   "
    And the MARC record has another 008 field with the value "790922s1999    enk||||      o00||||eng ccam   "
    When I transform the MARC record
    Then the only production has the label "1757"

  # The Scala keeps the empty ǂc label on the date it takes from the 008.
  Scenario: The 008 supplies the date label too when the 264 date is empty
    Given the MARC record's only 008 field with the value "890724s1703    enk"
    And the MARC record has a 264 field with indicators " " "1" with subfield "a" value "[London? :" and subfield "c" value ""
    When I transform the MARC record
    Then the only production has the label "[London? : "
    And it has 1 date
    And its 1st date has the label "1703"
    And its 1st date has the range.from_time "1703-01-01T00:00:00Z"

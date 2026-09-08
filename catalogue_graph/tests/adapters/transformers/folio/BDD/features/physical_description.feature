Feature: physical description (MARC 300)
  Physical description is assembled from MARC 300 ǂa/ǂb/ǂc/ǂe.
  The subfields carry their own punctuation, so they are joined with a
  single space and otherwise left alone. 300 is repeatable, and each
  instance becomes its own line.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (SierraPhysicalDescriptionTest.scala), including its test data,
  so the two can be compared directly.

  https://www.loc.gov/marc/bibliographic/bd300.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  # Ported from SierraPhysicalDescriptionTest.scala.

  Scenario: A record with no 300 field has no physical description
    Given the MARC record has a 563 field with subfield "b" value "The edifying extent of early emus"
    When I transform the MARC record
    Then the work's physical_description is absent

  Scenario: Subfields other than ǂa, ǂb, ǂc and ǂe are ignored
    Given the MARC record has a 300 field with subfield "b" value "Queuing quokkas quarrel about Queen Quince" and subfield "d" value "The edifying extent of early emus"
    When I transform the MARC record
    Then the work's physical_description is "Queuing quokkas quarrel about Queen Quince"

  Scenario: Repeated 300 fields each become their own line
    Given the MARC record has a 300 field with subfield "b" value "The queer quolls quits and quarrels"
    And the MARC record has another 300 field with subfield "b" value "A quintessential quadraped is quick" and subfield "d" value "Egad!  An early eagle is eating the earwig."
    When I transform the MARC record
    Then the work's physical_description is "The queer quolls quits and quarrels<br/>A quintessential quadraped is quick"

  Scenario: Repeated 300 fields on an AV record
    # Based on Sierra record b16768759
    Given the MARC record has a 300 field with subfield "a" value "1 videocassette (VHS) (1 min.) :" and subfield "b" value "sound, color, PAL."
    And the MARC record has another 300 field with subfield "a" value "1 DVD (1 min.) :" and subfield "b" value "sound, color"
    When I transform the MARC record
    Then the work's physical_description is "1 videocassette (VHS) (1 min.) : sound, color, PAL.<br/>1 DVD (1 min.) : sound, color"

  Scenario: A single 300 joins ǂa, ǂb and ǂc with spaces
    Given the MARC record has a 300 field with subfield "a" value "The queer quolls quits and quarrels" and subfield "b" value "A quintessential quadraped is quick" and subfield "c" value "The edifying extent of early emus"
    When I transform the MARC record
    Then the work's physical_description is "The queer quolls quits and quarrels A quintessential quadraped is quick The edifying extent of early emus"

  Scenario: A single 300 joins ǂa, ǂb, ǂc and ǂe with spaces
    Given the MARC record has a 300 field with subfield "a" value "1 photograph :" and subfield "b" value "photonegative, glass ;" and subfield "c" value "glass 10.6 x 8 cm +" and subfield "e" value "envelope"
    When I transform the MARC record
    Then the work's physical_description is "1 photograph : photonegative, glass ; glass 10.6 x 8 cm + envelope"

  # Deliberate improvements on the Scala, which returns a whitespace-only or
  # separator-only string in these cases rather than nothing at all.

  Scenario: Surrounding whitespace is trimmed from each line
    Given the MARC record has a 300 field with subfield "a" value "  1 volume  "
    When I transform the MARC record
    Then the work's physical_description is "1 volume"

  Scenario: A 300 whose subfields are empty has no physical description
    Given the MARC record has a 300 field with subfield "a" value "" and subfield "b" value "   "
    When I transform the MARC record
    Then the work's physical_description is absent

  Scenario: Empty 300 fields do not contribute blank lines
    Given the MARC record has a 300 field with subfield "a" value ""
    And the MARC record has another 300 field with subfield "a" value "1 volume"
    When I transform the MARC record
    Then the work's physical_description is "1 volume"

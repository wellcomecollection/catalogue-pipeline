Feature: Extract designations from MARC 362 $a
  The designation list is derived from every non-empty 362 $a subfield in order of appearance.

  Background:
    Given a valid MARC record

  Rule: When there are no valid 362 fields, there are no designations
    Scenario: No 362 fields present
      When I transform the MARC record
      Then there are no designations

    Scenario: Non-a subfields are ignored
      Given the MARC record has a 362 field with subfield "z" value "Incorrect note"
      And the MARC record has another 362 field with subfield "z" value "Another note"
      When I transform the MARC record
      Then there are no designations

    Scenario: Empty $a subfields are ignored
      Given the MARC record has a 362 field with subfield "a" value ""
      And the MARC record has another 362 field with subfield "a" value "   "
      When I transform the MARC record
      Then there are no designations

  Rule: a designation is created for each valid 362 field
    Scenario: Single 362 $a
      Given the MARC record has a 362 field with subfield "a" value "Vol. 1 (Jan. 1999)-"
      When I transform the MARC record
      Then the only designation is "Vol. 1 (Jan. 1999)-"

    Scenario: Multiple 362 $a fields preserve order
      Given the MARC record has a 362 field with subfield "a" value "Vol. 1 (Jan. 1999)-"
      And the MARC record has another 362 field with subfield "a" value "Vol. 2 (Feb. 1999)-"
      And the MARC record has another 362 field with subfield "a" value "Vol. 3 (Mar. 1999)-"
      When I transform the MARC record
      Then there are 3 designations
      And the 1st designation is "Vol. 1 (Jan. 1999)-"
      And the 2nd designation is "Vol. 2 (Feb. 1999)-"
      And the 3rd designation is "Vol. 3 (Mar. 1999)-"

    Scenario: Mixed useful and ignored subfields in same field
      Given the MARC record has a 362 field with subfield "a" value "Issue 1 (Spring 2000)" and subfield "z" value "Some note"
      And the MARC record has another 362 field with subfield "z" value "Only a note"
      And the MARC record has another 362 field with subfield "a" value "Issue 2 (Summer 2000)"
      When I transform the MARC record
      Then there are 2 designations
      And the 1st designation is "Issue 1 (Spring 2000)"
      And the 2nd designation is "Issue 2 (Summer 2000)"

    Scenario: Blank and whitespace-only $a subfields are ignored
      Given the MARC record has a 362 field with subfield "a" value ""
      And the MARC record has another 362 field with subfield "a" value "    "
      And the MARC record has another 362 field with subfield "a" value "No. 1 (2010)-"
      When I transform the MARC record
      Then the only designation is "No. 1 (2010)-"

    Scenario: Trimming leading/trailing whitespace
      Given the MARC record has a 362 field with subfield "a" value "   Vol. 7 (2005)- "
      And the MARC record has another 362 field with subfield "a" value " Vol. 8 (2006)-"
      When I transform the MARC record
      Then there are 2 designations
      And the 1st designation is "Vol. 7 (2005)-"
      And the 2nd designation is "Vol. 8 (2006)-"

    Scenario: Other tags with $a do not contribute to designation
      Given the MARC record has a 260 field with subfield "a" value "London"
      And the MARC record has a 245 field with subfield "a" value "Some Title"
      When I transform the MARC record
      Then there are no designations

    Scenario: Interleaved 362 and other tags
      Given the MARC record has a 245 field with subfield "a" value "Host Title"
      And the MARC record has another 362 field with subfield "a" value "No. 1 (Jan. 2012)-"
      And the MARC record has a 260 field with subfield "a" value "Oxford"
      And the MARC record has another 362 field with subfield "a" value "No. 2 (Feb. 2012)-"
      When I transform the MARC record
      Then there are 2 designations
      And the 1st designation is "No. 1 (Jan. 2012)-"
      And the 2nd designation is "No. 2 (Feb. 2012)-"
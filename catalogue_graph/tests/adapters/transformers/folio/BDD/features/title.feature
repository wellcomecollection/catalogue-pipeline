Feature: title (MARC 245)
  The title is built from MARC 245 subfields ǂa, ǂb, ǂc, ǂh, ǂn and ǂp,
  joined with a space in the order they appear in the field. Anything in
  square brackets is stripped from ǂh, and a ǂh with nothing after it to
  join to is dropped. Title is mandatory: a record without a usable 245
  fails to transform.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (MarcTitleTest.scala), including its test data, so the two can
  be compared directly.

  http://www.loc.gov/marc/bibliographic/bd245.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"

  # Ported from MarcTitleTest.scala.

  Scenario: The title is built from the 245 field
    Given the MARC record has a 245 field with subfield "a" value "hello"
    When I transform the MARC record
    Then the work's title is "hello"

  Scenario: The first 245 is used if a record erroneously has more than one
    Given the MARC record has a 245 field with subfield "a" value "hello world"
    And the MARC record has another 245 field with subfield "a" value "hello dolly"
    When I transform the MARC record
    Then the work's title is "hello world"

  Scenario: The title is built from subfields ǂa, ǂb, ǂc, ǂh, ǂn and ǂp
    Given the MARC record has a 245 field with subfield "a" value "cyntaf" and subfield "b" value "ail" and subfield "c" value "trydydd" and subfield "h" value "pedwerydd" and subfield "n" value "pumed" and subfield "p" value "chweched" and subfield "k" value "saithfed"
    When I transform the MARC record
    Then the work's title is "cyntaf ail trydydd pedwerydd pumed chweched"

  Scenario: The chosen subfields are joined in document order
    Given the MARC record has a 245 field with subfield "h" value "cyntaf" and subfield "p" value "ail" and subfield "c" value "trydydd" and subfield "n" value "pedwerydd" and subfield "a" value "pumed" and subfield "b" value "chweched" and subfield "k" value "saithfed"
    When I transform the MARC record
    Then the work's title is "cyntaf ail trydydd pedwerydd pumed chweched"

  Scenario: Repeated subfields are all kept
    # Technically only ǂn and ǂp are repeatable, but the transformer is not picky
    Given the MARC record has a 245 field with subfield "n" value "cyntaf" and subfield "p" value "ail" and subfield "n" value "trydydd" and subfield "p" value "pedwerydd" and subfield "h" value "pumed" and subfield "p" value "chweched" and subfield "p" value "saithfed"
    When I transform the MARC record
    Then the work's title is "cyntaf ail trydydd pedwerydd pumed chweched saithfed"

  Scenario: A trailing ǂh is dropped, with nothing to join it to
    Given the MARC record has a 245 field with subfield "a" value "cyntaf" and subfield "h" value "ail" and subfield "p" value "trydydd" and subfield "h" value "ignore me, I'm not here!"
    When I transform the MARC record
    Then the work's title is "cyntaf ail trydydd"

  Scenario: Square bracket content is stripped from ǂh but left in other subfields
    Given the MARC record has a 245 field with subfield "a" value "cyntaf [un]" and subfield "h" value "ail [dau]" and subfield "p" value "trydydd"
    When I transform the MARC record
    Then the work's title is "cyntaf [un] ail trydydd"

  Scenario: A record with no 245 field fails to transform
    Then transforming the record raises ValueError

  Scenario: A 245 with no suitable subfields fails to transform
    Given the MARC record has a 245 field with subfield "7" value "the back of a lorry"
    Then transforming the record raises ValueError

  Scenario: A 245 whose only suitable subfield is ǂh fails to transform
    # A trailing ǂh is discarded, so this is the same as having no subfields
    Given the MARC record has a 245 field with subfield "h" value "the back of a lorry"
    Then transforming the record raises ValueError

  # Deliberate improvements on the Scala, which returns a blank title in the
  # first case and mishandles the pathological second case by its own admission.

  Scenario: A 245 with only whitespace content fails to transform
    Given the MARC record has a 245 field with subfield "a" value "   "
    Then transforming the record raises ValueError

  Scenario: Only the trailing ǂh is dropped when an earlier ǂh has identical content
    Given the MARC record has a 245 field with subfield "a" value "cyntaf" and subfield "h" value "ail" and subfield "p" value "trydydd" and subfield "h" value "ail"
    When I transform the MARC record
    Then the work's title is "cyntaf ail trydydd"

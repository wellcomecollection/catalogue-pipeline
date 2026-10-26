Feature: collection path (MARC 773 and 774)
  A record with a 773 ǂw (link to its host) or a 774 ǂw (link to a constituent)
  is part of a hierarchy and gets a collection path: `host/own` for a part, or
  `own` for a root. The host comes from the first 773 that has a ǂw, with any
  (UkLW) prefix removed, and the 773 ǂg related part is folded into
  the own segment as a token. The record's own identifier is its unique
  identifier in 099 ǂa, falling back to its Sierra b-number in 907 ǂa. A record
  that only links to itself, or has no identifier of its own, gets no path.

  These scenarios mirror SierraCollectionPathTest.scala and
  MarcCollectionPathTest.scala, including their test data, with the record's
  identifier in 099 where the Scala has 001, since in FOLIO 001 holds the
  instance HRID. Deliberate divergences are grouped at the end.

  https://www.loc.gov/marc/bibliographic/bd773.html
  https://www.loc.gov/marc/bibliographic/bd774.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  # Ported from SierraCollectionPathTest.scala. Its "no 773 or 774 fields" test
  # uses a 999 field, which here identifies the record, so a 500 is used. Its
  # "uses 099 when there is no 001" test is the same record as its 774 root
  # test once 001 is out of the picture, and its "773 consisting of ǂw and the
  # own id" test repeats its "773 with no 774" test, so each is ported once.
  # Its (Wcat) prefix is replaced by (UkLW), see the divergences below.

  Scenario: A record with no 773 or 774 fields has no collection path
    Given the MARC record has a 500 field with subfield "a" value "banana"
    When I transform the MARC record
    Then the work's collection path is absent

  Scenario Outline: A 773 or 774 without ǂw gives no collection path
    Given the MARC record has a <tag> field with subfield "t" value "title-only reference to the other document"
    When I transform the MARC record
    Then the work's collection path is absent

    Examples:
      | tag |
      | 773 |
      | 774 |

  Scenario Outline: A record with no identifier of its own has no collection path
    Given the MARC record has a <tag> field with subfield "t" value "A Constituent" and subfield "w" value "This value does not matter, it just matters that it exists"
    When I transform the MARC record
    Then the work's collection path is absent
    And a warning "Cannot create a collection path for a record with no identifier" is logged

    Examples:
      | tag |
      | 773 |
      | 774 |

  Scenario: A 773 that refers to the record itself gives no collection path
    Given the MARC record has a 099 field with subfield "a" value "56789i"
    And the MARC record has a 773 field with subfield "t" value "Myself" and subfield "w" value "56789i"
    When I transform the MARC record
    Then the work's collection path is absent
    And an error "Self-referential host link" is logged

  Scenario: A record with a 774 is a root, so its path is its own identifier
    Given the MARC record has a 099 field with subfield "a" value "12345i"
    And the MARC record has a 774 field with subfield "t" value "A Constituent" and subfield "w" value "This value does not matter, it just matters that it exists"
    When I transform the MARC record
    Then the work's collection path.path is "12345i"

  Scenario: A root with a self-referential 773 still gets its own identifier as path
    Given the MARC record has a 099 field with subfield "a" value "12345i"
    And the MARC record has a 773 field with subfield "t" value "Myself" and subfield "w" value "12345i"
    And the MARC record has a 774 field with subfield "t" value "A Constituent" and subfield "w" value "This value does not matter, it just matters that it exists"
    When I transform the MARC record
    Then the work's collection path.path is "12345i"

  Scenario: A record with both 773 and 774 takes its path from the 773
    Given the MARC record has a 099 field with subfield "a" value "56789i"
    And the MARC record has a 773 field with subfield "t" value "A Host" and subfield "w" value "12345i"
    And the MARC record has a 774 field with subfield "t" value "A Constituent" and subfield "w" value "This value does not matter, it just matters that it exists"
    When I transform the MARC record
    Then the work's collection path.path is "12345i/56789i"

  Scenario: A record with a 773 and no 774 is a part, so its path is host/own
    Given the MARC record has a 099 field with subfield "a" value "56789i"
    And the MARC record has a 773 field with subfield "t" value "A Host" and subfield "w" value "12345i"
    When I transform the MARC record
    Then the work's collection path.path is "12345i/56789i"

  Scenario: Leading and trailing whitespace is ignored in path segments
    Given the MARC record has a 099 field with subfield "a" value " 56789i "
    And the MARC record has a 773 field with subfield "t" value "A Host" and subfield "w" value " 12345i "
    When I transform the MARC record
    Then the work's collection path.path is "12345i/56789i"

  Scenario: The 773 ǂg related part is folded into the own segment
    Given the MARC record has a 099 field with subfield "a" value "56789i"
    And the MARC record has a 773 field with subfield "t" value "A Host" and subfield "w" value "(UkLW)12345i" and subfield "g" value "page 4"
    When I transform the MARC record
    Then the work's collection path.path is "12345i/page_4_56789i"

  Scenario: The first 773 with a ǂw is used when there are several 773s
    Given the MARC record has a 099 field with subfield "a" value "56789i"
    And the MARC record has a 773 field with subfield "t" value "Not this one"
    And the MARC record has another 773 field with subfield "t" value "Or this one" and subfield "g" value "Not this page either"
    And the MARC record has another 773 field with subfield "t" value "This one" and subfield "w" value "(UkLW)12345i" and subfield "g" value "vol. 4"
    When I transform the MARC record
    Then the work's collection path.path is "12345i/vol_4_56789i"

  # Ported from MarcCollectionPathTest.scala. Its other cases (leaf, trimmed
  # identifiers, self reference, root, branch, no identifier) repeat the
  # Sierra tests above.

  Scenario: Punctuation in the 773 ǂg related part is dropped from the token
    Given the MARC record has a 099 field with subfield "a" value "record_id"
    And the MARC record has a 773 field with subfield "w" value "parent_id" and subfield "g" value "Some Qualified Name 1."
    When I transform the MARC record
    Then the work's collection path.path is "parent_id/Some_Qualified_Name_1_record_id"

  # Deliberate divergences from the Sierra transformer, agreed with Collections
  # Information (wellcomecollection/platform#6562).

  # The Scala prefers 001 and falls back to 099. In FOLIO 001 is the instance
  # HRID, which no 773/774 ever references, so the own identifier is 099 and
  # the fallback is the Sierra b-number, which cataloguers use in ǂw when a
  # record has no unique identifier.

  Scenario: The 001 field is never used as the record's own identifier
    Given the MARC record's only 001 field with the value "00001"
    And the MARC record has a 099 field with subfield "a" value "00099"
    And the MARC record has a 774 field with subfield "t" value "A Constituent" and subfield "w" value "This value does not matter, it just matters that it exists"
    When I transform the MARC record
    Then the work's collection path.path is "00099"

  Scenario: A record with no 099 uses its Sierra b-number from 907 as its own identifier
    Given the MARC record has a 907 field with subfield "a" value "b17478285"
    And the MARC record has a 774 field with subfield "t" value "A Constituent" and subfield "w" value "(UkLW)b17478297"
    When I transform the MARC record
    Then the work's collection path.path is "b17478285"

  Scenario: The unique identifier in 099 is preferred to the b-number in 907
    Given the MARC record has a 099 field with subfield "a" value "Z1450"
    And the MARC record has a 907 field with subfield "a" value "b33553592"
    And the MARC record has a 774 field with subfield "t" value "A Constituent" and subfield "w" value "(UkLW)Z1450.1"
    When I transform the MARC record
    Then the work's collection path.path is "Z1450"

  # Cataloguers prefix local control numbers in ǂw with (UkLW), the library's
  # organisation code, and keep it because it says whose number it is when
  # records are exported. The Scala does not strip it, so these never linked.

  Scenario: The (UkLW) prefix is removed from the host identifier
    Given the MARC record has a 099 field with subfield "a" value "Z1450.1"
    And the MARC record has a 773 field with subfield "t" value "holding title." and subfield "w" value "(UkLW)Z1450"
    When I transform the MARC record
    Then the work's collection path.path is "Z1450/Z1450.1"

  Scenario: A part linked to its host by b-number gets the b-number as host segment
    Given the MARC record has a 099 field with subfield "a" value "EPB/T/248.1"
    And the MARC record has a 773 field with subfield "t" value "EPB/T/248." and subfield "w" value "(UkLW)b33055919"
    When I transform the MARC record
    Then the work's collection path.path is "b33055919/EPB|T|248.1"

  # The Scala strips the (Wcat) prefix from i-number links. No FOLIO record has
  # one, since Pictures are not migrated, so the FOLIO transformer does not.

  Scenario: The (Wcat) prefix is not removed from the host identifier
    Given the MARC record has a 099 field with subfield "a" value "56789i"
    And the MARC record has a 773 field with subfield "t" value "A Host" and subfield "w" value "(Wcat)12345i"
    When I transform the MARC record
    Then the work's collection path.path is "(Wcat)12345i/56789i"

  # "/" separates path segments, so a shelfmark containing slashes must have
  # them replaced to stay one segment. "|" is used, as the Axiell transformer does.

  Scenario: Slashes inside identifiers are replaced so each identifier is one segment
    Given the MARC record has a 099 field with subfield "a" value "EPB/T/188.5"
    And the MARC record has a 773 field with subfield "t" value "On the composition and properties of drinking-water" and subfield "w" value "(UkLW)EPB/T/188.1"
    When I transform the MARC record
    Then the work's collection path.path is "EPB|T|188.1/EPB|T|188.5"

  # A 773 may repeat ǂw with both the host's b-number and its unique
  # identifier. The host identifies itself by the unique identifier when it has
  # one, so that is the one to link by, whichever comes first.

  Scenario: When ǂw is repeated, the unique identifier is preferred to the b-number
    Given the MARC record has a 099 field with subfield "a" value "EPB/T/248.1"
    And the MARC record has a 773 field with subfield "t" value "EPB/T/248." and subfield "w" value "(UkLW)b33055919" and subfield "w" value "(UkLW)EPB/T/248"
    When I transform the MARC record
    Then the work's collection path.path is "EPB|T|248/EPB|T|248.1"

  Scenario: When ǂw is repeated with b-numbers only, the first is used
    Given the MARC record has a 099 field with subfield "a" value "MS.MSL.7a"
    And the MARC record has a 773 field with subfield "w" value "(UkLW)b19270501" and subfield "w" value "b19551599"
    When I transform the MARC record
    Then the work's collection path.path is "b19270501/MS.MSL.7a"

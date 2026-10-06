Feature: languages (MARC 998 ǂf and 041)
  The primary language comes from the Sierra LANG fixed field, carried into FOLIO
  as MARC 998 ǂf, falling back to the MARC language code in 008/35-37 when the
  record has no ǂf at all. Additional languages come from 041 ǂa, in document
  order. Codes are trimmed and lowercased, resolved against the MARC language
  code list, and codes that say nothing about the language are suppressed. The
  primary language comes first and the list is deduplicated.

  These scenarios mirror the unit tests of the Scala transformer this replaces
  (SierraLanguagesTest.scala), including its test data, so the two can be
  compared directly. Where the Scala reads the Sierra API's "lang" field, these
  read 998 ǂf.

  https://www.loc.gov/marc/bibliographic/bd008a.html
  https://www.loc.gov/marc/bibliographic/bd041.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  # Ported from SierraLanguagesTest.scala.

  Scenario: A record with no language fields has no languages
    When I transform the MARC record
    Then there are no languages

  Scenario: A single language comes from 998 ǂf
    Given the MARC record has a 998 field with subfield "f" value "fre"
    When I transform the MARC record
    Then the only language has the label "French"

  Scenario: 998 ǂf is combined with 041, and ǂb is ignored
    Given the MARC record has a 998 field with subfield "f" value "fre"
    And the MARC record has a 041 field with subfield "a" value "ger" and subfield "b" value "dut" and subfield "a" value "eng"
    When I transform the MARC record
    Then the work has 3 languages with label:
      | French  |
      | German  |
      | English |

  Scenario: Languages come from multiple instances of 041
    Given the MARC record has a 998 field with subfield "f" value "fre"
    And the MARC record has a 041 field with subfield "a" value "ger"
    And the MARC record has another 041 field with subfield "a" value "eng"
    When I transform the MARC record
    Then the work has 3 languages with label:
      | French  |
      | German  |
      | English |

  Scenario: An unrecognised code in 041 is dropped and logged
    Given the MARC record has a 998 field with subfield "f" value "chi"
    And the MARC record has a 041 field with subfield "a" value "???"
    When I transform the MARC record
    Then an error "Unrecognised language code" is logged with code "???"
    And the only language has the label "Chinese"

  Scenario: The list is deduplicated, with 998 ǂf first
    Given the MARC record has a 998 field with subfield "f" value "ger"
    And the MARC record has a 041 field with subfield "a" value "fre" and subfield "a" value "eng" and subfield "a" value "ger"
    When I transform the MARC record
    Then the work has 3 languages with label:
      | German  |
      | French  |
      | English |

  Scenario: Codes that don't correspond to a language are suppressed
    Given the MARC record has a 998 field with subfield "f" value "chi"
    And the MARC record has a 041 field with subfield "a" value "mul" and subfield "a" value "eng" and subfield "a" value "und" and subfield "a" value "fre" and subfield "a" value "zxx"
    When I transform the MARC record
    Then the work has 3 languages with label:
      | Chinese |
      | English |
      | French  |

  Scenario: Whitespace is stripped from 041 values
    Given the MARC record has a 041 field with subfield "a" value "eng "
    When I transform the MARC record
    Then the only language has the label "English"

  Scenario: 041 values are lowercased
    Given the MARC record has a 041 field with subfield "a" value "ENG" and subfield "a" value "Lat"
    When I transform the MARC record
    Then the work has 2 languages with label:
      | English |
      | Latin   |

  Scenario: An unidentifiable primary code is dropped and logged
    Given the MARC record has a 998 field with subfield "f" value "idk"
    When I transform the MARC record
    Then an error "Unrecognised language code" is logged with code "idk"
    And there are no languages

  Scenario: A primary code of only whitespace gives no languages
    Given the MARC record has a 998 field with subfield "f" value "   "
    When I transform the MARC record
    Then there are no languages

  # FOLIO-specific behaviour, verified against the FOLIO data.

  Scenario: 008/35-37 is used when the record has no 998 ǂf
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 ger  "
    When I transform the MARC record
    Then the only language has the label "German"

  Scenario: 998 ǂf wins over 008/35-37 when both carry a language
    # 008/35-37 is often left as fill characters, "und", or stale, so the curated
    # ǂf is preferred wherever the two disagree
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 eng  "
    And the MARC record has a 998 field with subfield "f" value "fre"
    When I transform the MARC record
    Then the only language has the label "French"

  Scenario: A blank 998 ǂf means no language and does not fall back to 008
    # 998 ǂf is the curated field, so a blank there is a deliberate "no language";
    # falling back would invent a language from a stale or default 008
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 eng  "
    And the MARC record has a 998 field with subfield "f" value "   "
    When I transform the MARC record
    Then there are no languages

  Scenario: Fill characters in 008 mean no language, and are not an error
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 |||  "
    When I transform the MARC record
    Then there are no languages

  Scenario: A code of "n/a" is dropped and logged
    Given the MARC record has a 998 field with subfield "f" value "n/a"
    When I transform the MARC record
    Then an error "Unrecognised language code" is logged with code "n/a"
    And there are no languages

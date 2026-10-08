Feature: languages (MARC 008/35-37 and 041)
  The primary language comes from the MARC language code in 008/35-37, and
  additional languages from 041 ǂa in document order. Codes are trimmed and
  lowercased, resolved against the MARC language code list, and codes that say
  nothing about the language are suppressed. The primary language comes first
  and the list is deduplicated.

  These scenarios mirror the unit tests of the Scala transformer this replaces
  (SierraLanguagesTest.scala), including its test data, so the two can be
  compared directly. Where the Scala reads the Sierra LANG fixed field, these
  read 008/35-37: Folio carries LANG over as 998 ǂf but does not update it when
  a record is edited, so it is not a dependable source.

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

  Scenario: A single language comes from 008/35-37
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 fre  "
    When I transform the MARC record
    Then the only language has the label "French"

  Scenario: 008/35-37 is combined with 041, and ǂb is ignored
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 fre  "
    And the MARC record has a 041 field with subfield "a" value "ger" and subfield "b" value "dut" and subfield "a" value "eng"
    When I transform the MARC record
    Then the work has 3 languages with label:
      | French  |
      | German  |
      | English |

  Scenario: Languages come from multiple instances of 041
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 fre  "
    And the MARC record has a 041 field with subfield "a" value "ger"
    And the MARC record has another 041 field with subfield "a" value "eng"
    When I transform the MARC record
    Then the work has 3 languages with label:
      | French  |
      | German  |
      | English |

  Scenario: An unrecognised code in 041 is dropped and logged
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 chi  "
    And the MARC record has a 041 field with subfield "a" value "???"
    When I transform the MARC record
    Then an error "Unrecognised language code" is logged with code "???"
    And the only language has the label "Chinese"

  Scenario: The list is deduplicated, with the primary language first
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 ger  "
    And the MARC record has a 041 field with subfield "a" value "fre" and subfield "a" value "eng" and subfield "a" value "ger"
    When I transform the MARC record
    Then the work has 3 languages with label:
      | German  |
      | French  |
      | English |

  Scenario: Codes that don't correspond to a language are suppressed
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 chi  "
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
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 idk  "
    When I transform the MARC record
    Then an error "Unrecognised language code" is logged with code "idk"
    And there are no languages

  Scenario: A primary code of only whitespace gives no languages
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0      "
    When I transform the MARC record
    Then there are no languages

  # Folio-specific behaviour, verified against the Folio data.

  Scenario: Fill characters in 008 mean no language, and are not an error
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 |||  "
    When I transform the MARC record
    Then there are no languages

  Scenario: 998 ǂf is not consulted, even when it carries a different language
    # Folio does not maintain ǂf after migration, so 008/35-37 is authoritative
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 ger  "
    And the MARC record has a 998 field with subfield "f" value "fre"
    When I transform the MARC record
    Then the only language has the label "German"

  Scenario: A 041 ǂa packing several codes is split every three characters
    # Folio's own mapping splits ǂa this way. No live record in either system uses
    # the convention (it appears only on suppressed Folio bibs)
    Given the MARC record has a 041 field with subfield "a" value "engger"
    When I transform the MARC record
    Then the work has 2 languages with label:
      | English |
      | German  |

  Scenario: A value that merely looks packed is left whole and logged
    # Only split when every chunk is a real code, so note text in ǂa is not
    # read as a list of languages
    Given the MARC record has a 041 field with subfield "a" value "xxxyyy"
    When I transform the MARC record
    Then an error "Unrecognised language code" is logged with code "xxxyyy"
    And there are no languages

  Scenario: A mistyped primary code is dropped and logged
    # "jap" is not a MARC code; the code for Japanese is "jpn"
    Given the MARC record's only 008 field with the value "140303s1958    enk     s     000 0 jap  "
    When I transform the MARC record
    Then an error "Unrecognised language code" is logged with code "jap"
    And there are no languages

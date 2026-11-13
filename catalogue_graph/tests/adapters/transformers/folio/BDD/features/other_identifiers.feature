Feature: other identifiers
  A FOLIO work's other identifiers are, in order: those classified from MARC
  035, ISBNs from 020 ǂa, ISSNs from 022 ǂa, Wellcome digcodes from 759 ǂa,
  ESTC citation numbers from 510, the instance HRID from 001, and the Sierra
  b number from 907 ǂa where the record was migrated from Sierra.

  The 020, 022, 759 and 510 rules mirror the Sierra transformer this replaces
  (SierraIdentifiers.scala, MarcInternationalStandardIdentifiers.scala).

  https://www.loc.gov/marc/bibliographic/bd020.html
  https://www.loc.gov/marc/bibliographic/bd022.html
  https://www.loc.gov/marc/bibliographic/bd510.html

  Background:
    Given a MARC record with field 001 "in00000000001"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A record with no identifier fields has only its HRID
    When I transform the MARC record
    Then the work has the other identifiers:
      | type                | value         |
      | folio-instance-hrid | in00000000001 |

  Scenario: A record migrated from Sierra also has its b number
    Given the MARC record has a 907 field with subfield "a" value ".b11839053"
    When I transform the MARC record
    Then the work has the other identifiers:
      | type                 | value         |
      | folio-instance-hrid  | in00000000001 |
      | sierra-system-number | b11839053     |

  Scenario: ISBNs and ISSNs are taken from 020 ǂa and 022 ǂa
    Given the MARC record has a 020 field with subfield "a" value "978-1-890159-02-3"
    And the MARC record has a 022 field with subfield "a" value "1890-6729"
    When I transform the MARC record
    Then the work has the other identifiers:
      | type                | value             |
      | isbn                | 978-1-890159-02-3 |
      | issn                | 1890-6729         |
      | folio-instance-hrid | in00000000001     |

  Scenario: ISBNs and ISSNs are trimmed and deduplicated
    Given the MARC record has a 020 field with subfield "a" value " 0140015450"
    And the MARC record has another 020 field with subfield "a" value "0140015450 "
    And the MARC record has a 022 field with subfield "a" value "2193-1216 "
    And the MARC record has another 022 field with subfield "a" value "2193-1216"
    When I transform the MARC record
    Then the work has the other identifiers:
      | type                | value         |
      | isbn                | 0140015450    |
      | issn                | 2193-1216     |
      | folio-instance-hrid | in00000000001 |

  Scenario: Cancelled and invalid numbers are not identifiers
    Given the MARC record has a 020 field with subfield "z" value "0773507558"
    And the MARC record has a 022 field with subfield "y" value "0933-5811"
    And the MARC record has a 022 field with subfield "l" value "0267-1115"
    When I transform the MARC record
    Then the work has the other identifiers:
      | type                | value         |
      | folio-instance-hrid | in00000000001 |

  Scenario: A digcode is the alphabetic run starting "dig", with trailing content dropped
    Given the MARC record has a 759 field with subfield "a" value "digmoh(Channel)"
    And the MARC record has another 759 field with subfield "a" value "digproquest(4) "
    When I transform the MARC record
    Then the work has the other identifiers:
      | type                | value         |
      | wellcome-digcode    | digmoh        |
      | wellcome-digcode    | digproquest   |
      | folio-instance-hrid | in00000000001 |

  Scenario: 759 values that are not digcodes are ignored, and digcodes are deduplicated
    Given the MARC record has a 759 field with subfield "a" value "elecco"
    And the MARC record has another 759 field with subfield "a" value "dig"
    And the MARC record has another 759 field with subfield "a" value "digmhl(cushing)"
    And the MARC record has another 759 field with subfield "a" value "digmhl(nlm)"
    When I transform the MARC record
    Then the work has the other identifiers:
      | type                | value         |
      | wellcome-digcode    | digmhl        |
      | folio-instance-hrid | in00000000001 |

  Scenario: An ESTC citation number is taken from a 510 that is exactly ǂa ESTC and ǂc
    Given the MARC record has a 510 field with subfield "a" value "ESTC" and subfield "c" value "N16242"
    When I transform the MARC record
    Then the work has the other identifiers:
      | type                | value         |
      | bl-estc-citation-no | N16242        |
      | folio-instance-hrid | in00000000001 |

  # In FOLIO these cite other bibliographies under an ESTC heading, such as
  # Foxon's "P730", so the number is not an ESTC number.
  Scenario: A 510 with more than one ǂc has no ESTC identifier
    Given the MARC record has a 510 field with subfield "a" value "ESTC" and subfield "c" value "Foxon" and subfield "c" value "P730"
    When I transform the MARC record
    Then the work has the other identifiers:
      | type                | value         |
      | folio-instance-hrid | in00000000001 |

  Scenario Outline: Other 510s have no ESTC identifier
    Given the MARC record has a 510 field with subfield "a" value "<a>" and subfield "c" value "<c>"
    When I transform the MARC record
    Then the work has the other identifiers:
      | type                | value         |
      | folio-instance-hrid | in00000000001 |
    Examples:
      | a          | c             |
      | ESTC       | Cf. Moore 451 |
      | ESTC       | 360           |
      | Garrison   | T5132         |

  Scenario: Identifiers from every source appear together, in order
    Given the MARC record has a 035 field with subfield "a" value "(Sierra Number)i12056868"
    And the MARC record has a 020 field with subfield "a" value "0140015450"
    And the MARC record has a 022 field with subfield "a" value "2193-1216"
    And the MARC record has a 759 field with subfield "a" value "digmoh(England)"
    And the MARC record has a 510 field with subfield "a" value "ESTC" and subfield "c" value "T5132"
    And the MARC record has a 907 field with subfield "a" value "b11839053"
    When I transform the MARC record
    Then the work has the other identifiers:
      | type                 | value         |
      | sierra-identifier    | i12056868     |
      | isbn                 | 0140015450    |
      | issn                 | 2193-1216     |
      | wellcome-digcode     | digmoh        |
      | bl-estc-citation-no  | T5132         |
      | folio-instance-hrid  | in00000000001 |
      | sierra-system-number | b11839053     |

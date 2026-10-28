Feature: merge candidates
  A FOLIO work names the other works it should merge with, as a Sierra bib
  did. The merger treats a FOLIO work as it treats a Sierra work, and matches
  physical/digitised pairs on the reason "Physical/digitised Sierra work", so
  the reasons are the Sierra ones.

  A physical/digitised pair is linked from 776 ǂw `(UkLW)bNNN`. The b-number
  resolves to the other FOLIO work, because that work was minted with its 907
  b-number as its predecessor identifier.

  Miro images come from 089 ǂa and 962 ǂu, as in Sierra.

  In Sierra, an EBSCO stub bib held the EBSCO e-resource id in 001, flagged by
  003 "EBZ". In FOLIO, 001 is the HRID, and the migrated stub instances carry
  the EBSCO id in 035 ǂa as `(EBZ)ebsNNNe` (wellcomecollection/platform#6763),
  so that is where it is read from.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (SierraMergeCandidatesTest.scala), including its test data, so the
  two can be compared directly.

  Background:
    Given a MARC record with field 001 "in00000001"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A record with no linking fields has no merge candidates
    When I transform the MARC record
    Then there are no merge candidates

  # Physical/digitised pairs, from 776 ǂw

  Scenario: The b-number in 776 ǂw is a merge candidate
    Given the MARC record has a 776 field with subfield "w" value "(UkLW)b21414440"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type                 | value     | reason                         |
      | sierra-system-number | b21414440 | Physical/digitised Sierra work |

  Scenario: Spaces around the b-number are trimmed
    Given the MARC record has a 776 field with subfield "w" value "(UkLW)  b21414440 "
    When I transform the MARC record
    Then the work has the merge candidates:
      | type                 | value     | reason                         |
      | sierra-system-number | b21414440 | Physical/digitised Sierra work |

  Scenario: A 776 with no ǂw has no merge candidates
    Given the MARC record has a 776 field with subfield "a" value "blah blah"
    When I transform the MARC record
    Then there are no merge candidates

  Scenario Outline: The (UkLW) prefix is matched case-insensitively
    Given the MARC record has a 776 field with subfield "w" value "(<prefix>)b21414440"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type                 | value     | reason                         |
      | sierra-system-number | b21414440 | Physical/digitised Sierra work |

    Examples:
      | prefix |
      | UkLW   |
      | uklw   |
      | UkLw   |
      | UKLW   |

  Scenario: A 776 ǂw without the (UkLW) prefix is not a merge candidate
    Given the MARC record has a 776 field with subfield "w" value "(OCoLC)14322288"
    When I transform the MARC record
    Then there are no merge candidates

  Scenario: Two different b-numbers in 776 ǂw are ambiguous, so neither is a merge candidate
    Given the MARC record has a 776 field with subfield "w" value "(UkLW)  b21414440"
    And the MARC record has another 776 field with subfield "w" value "(UkLW)b12345678"
    When I transform the MARC record
    Then there are no merge candidates

  Scenario: Two different b-numbers in the same 776 are ambiguous, so neither is a merge candidate
    Given the MARC record has a 776 field with subfield "w" value "(UkLW)b21414440" and subfield "w" value "(UkLW)b12345678"
    When I transform the MARC record
    Then there are no merge candidates

  Scenario: The same b-number repeated in 776 ǂw gives one merge candidate
    Given the MARC record has a 776 field with subfield "w" value "(UkLW)  b21414440"
    And the MARC record has another 776 field with subfield "w" value "(UkLW)  b21414440"
    And the MARC record has another 776 field with subfield "w" value "(UkLW)b21414440"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type                 | value     | reason                         |
      | sierra-system-number | b21414440 | Physical/digitised Sierra work |

  Scenario: Other prefixes alongside a (UkLW) value are ignored
    Given the MARC record has a 776 field with subfield "w" value "(OCLC)123456789"
    And the MARC record has another 776 field with subfield "w" value "(UkLW)b21414440"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type                 | value     | reason                         |
      | sierra-system-number | b21414440 | Physical/digitised Sierra work |

  Scenario: A (UkLW) value after another prefix in the same 776 is a merge candidate
    Given the MARC record has a 776 field with subfield "w" value "(OCoLC)14322288" and subfield "w" value "(UkLW)b21414440"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type                 | value     | reason                         |
      | sierra-system-number | b21414440 | Physical/digitised Sierra work |

  Scenario: A (UkLW) value that isn't a b-number is ignored
    Given the MARC record has a 776 field with subfield "w" value "(UkLW)bxxxxxxxx"
    And the MARC record has another 776 field with subfield "w" value "(UkLW)b21414440"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type                 | value     | reason                         |
      | sierra-system-number | b21414440 | Physical/digitised Sierra work |
    And a warning "Invalid b-number in 776 ǂw" is logged

  # Miro images, from 089 ǂa and 962 ǂu

  Scenario Outline: A Miro id is taken from a Wellcome Images URL in 962 ǂu
    Given the MARC record has a 962 field with subfield "u" value "<url>"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type              | value     | reason           |
      | miro-image-number | <miro id> | Miro/Sierra work |

    Examples:
      | url                                                                     | miro id    |
      | http://wellcomeimages.org/indexplus/image/A0123456.html                 | A0123456   |
      | http://wellcomeimages.org/indexplus/image/L0041574.html.html            | L0041574   |
      | http://wellcomeimages.org/ixbin/hixclient?MIROPAC=V0000492EB            | V0000492EB |
      | http://wellcomeimages.org/ixbin/hixclient?MIROPAC=V0031553F1            | V0031553F1 |
      | http://wellcomeimages.org/ixbinixclient.exe?MIROPAC=V0010851.html.html  | V0010851   |
      | http://wellcomeimages.org/ixbinixclient.exe?image=M0009946.html         | M0009946   |

  Scenario: Each Miro URL in 962 ǂu gives a merge candidate
    Given the MARC record has a 962 field with subfield "u" value "http://wellcomeimages.org/indexplus/image/A0123456.html"
    And the MARC record has another 962 field with subfield "u" value "http://wellcomeimages.org/indexplus/image/B0000001.html"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type              | value    | reason           |
      | miro-image-number | A0123456 | Miro/Sierra work |
      | miro-image-number | B0000001 | Miro/Sierra work |

  Scenario: URLs for the same Miro id give one merge candidate
    Given the MARC record has a 962 field with subfield "u" value "http://wellcomeimages.org/indexplus/image/A0123456.html"
    And the MARC record has another 962 field with subfield "u" value "http://wellcomeimages.org/ixbin/hixclient?MIROPAC=A0123456"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type              | value    | reason           |
      | miro-image-number | A0123456 | Miro/Sierra work |

  Scenario: An unrecognised URL in 962 ǂu is not a merge candidate
    Given the MARC record has a 962 field with subfield "u" value "http://film.wellcome.ac.uk:15151/mediaplayer.html?fug_7340-1&pw=524ph=600.html"
    When I transform the MARC record
    Then there are no merge candidates

  Scenario: A Miro id in 089 ǂa is padded to seven digits
    Given the MARC record has a 089 field with subfield "a" value "V 13889"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type              | value    | reason           |
      | miro-image-number | V0013889 | Miro/Sierra work |

  Scenario: Each Miro id in 089 ǂa gives a merge candidate
    Given the MARC record has a 089 field with subfield "a" value "V 13889"
    And the MARC record has another 089 field with subfield "a" value "V 12"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type              | value    | reason           |
      | miro-image-number | V0013889 | Miro/Sierra work |
      | miro-image-number | V0000012 | Miro/Sierra work |

  Scenario: Miro ids come from both 089 and 962
    Given the MARC record has a 962 field with subfield "u" value "http://wellcomeimages.org/indexplus/image/A0123456.html"
    And the MARC record has a 089 field with subfield "a" value "V 13889"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type              | value    | reason           |
      | miro-image-number | V0013889 | Miro/Sierra work |
      | miro-image-number | A0123456 | Miro/Sierra work |

  Scenario: The same Miro id in 089 and 962 gives one merge candidate
    Given the MARC record has a 089 field with subfield "a" value "A0123456"
    And the MARC record has a 962 field with subfield "u" value "http://wellcomeimages.org/indexplus/image/A0123456.html"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type              | value    | reason           |
      | miro-image-number | A0123456 | Miro/Sierra work |

  Scenario: A suffixed Miro id replaces the same id without its suffix
    Given the MARC record has a 089 field with subfield "a" value "V0036036"
    And the MARC record has a 962 field with subfield "u" value "http://wellcomeimages.org/indexplus/image/V0036036EL.html"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type              | value      | reason           |
      | miro-image-number | V0036036EL | Miro/Sierra work |

  Scenario: An unsuffixed Miro id is kept when no suffixed version of it is present
    Given the MARC record has a 089 field with subfield "a" value "V0036036"
    And the MARC record has a 962 field with subfield "u" value "http://wellcomeimages.org/indexplus/image/V0012345EBR.html"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type              | value       | reason           |
      | miro-image-number | V0036036    | Miro/Sierra work |
      | miro-image-number | V0012345EBR | Miro/Sierra work |

  # EBSCO e-resources, from 035 ǂa

  Scenario: An (EBZ) id in 035 ǂa is an EBSCO merge candidate
    Given the MARC record has a 035 field with subfield "a" value "(EBZ)ebs12345e"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type             | value     | reason                  |
      | ebsco-alt-lookup | ebs12345e | EBSCO/Sierra e-resource |

  Scenario: An (EBZ) value that isn't an EBSCO id is not a merge candidate
    Given the MARC record has a 035 field with subfield "a" value "(EBZ)12345"
    When I transform the MARC record
    Then there are no merge candidates

  Scenario: An EBSCO-shaped id under another prefix is not a merge candidate
    Given the MARC record has a 035 field with subfield "a" value "(OCoLC)ebs12345e"
    When I transform the MARC record
    Then there are no merge candidates

  # A suppressed stub becomes a deleted work, which carries no merge candidate,
  # so its old Sierra work page would stop redirecting to the EBSCO work.
  # This is why stubs with a live EBSCO record must not be suppressed
  # (wellcomecollection/platform#6763).

  Scenario: A suppressed EBSCO stub is a deleted work with no merge candidates
    Given the MARC record has a 035 field with subfield "a" value "(EBZ)ebs12345e"
    And the MARC record's only 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001" and subfield "t" value "1"
    When I transform the MARC record
    Then the work is deleted
    And there are no merge candidates

  # Known divergences from the Scala.
  #
  # The Scala reads the EBSCO id from 001 or 099 when 003 is "EBZ". No FOLIO
  # record has a 003, and 001 is the FOLIO HRID.
  #
  # The Scala makes each Calm RecordID in 035 ǂa a "Calm/Sierra harvest"
  # candidate. None is emitted here. The RecordIDs left in FOLIO 035 don't
  # resolve to Axiell works, and none is among the legacy Calm records still
  # ingested through the Calm transformer. A Calm record that was harvested
  # into Sierra already links to its FOLIO work through its own BNumber
  # candidate, which resolves via the 907 predecessor.

  Scenario: A Calm RecordID in 035 ǂa is not a merge candidate
    Given the MARC record has a 035 field with subfield "a" value "(UkLW)1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"
    When I transform the MARC record
    Then there are no merge candidates

  Scenario: A 776 b-number, Miro ids and an EBSCO id are all merge candidates
    Given the MARC record has a 776 field with subfield "w" value "(UkLW)b21414440"
    And the MARC record has a 962 field with subfield "u" value "http://wellcomeimages.org/indexplus/image/A0123456.html"
    And the MARC record has a 035 field with subfield "a" value "(EBZ)ebs12345e"
    When I transform the MARC record
    Then the work has the merge candidates:
      | type                 | value     | reason                         |
      | sierra-system-number | b21414440 | Physical/digitised Sierra work |
      | miro-image-number    | A0123456  | Miro/Sierra work               |
      | ebsco-alt-lookup     | ebs12345e | EBSCO/Sierra e-resource        |

Feature: subjects (MARC 6xx)
  Subjects are built from the 600, 610, 611, 648, 650 and 651 headings, as for
  EBSCO records, but FOLIO keeps every heading whose second indicator names a
  thesaurus itself and drops every heading sourced from ǂ2 (second indicator 7),
  the adopted local vocabularies included. FOLIO also lists subjects by heading
  type rather than in document order, as the Sierra transformer does.

  These scenarios mirror the second-indicator unit tests of the Scala Sierra
  transformer this replaces (SierraConceptSubjectsTest.scala,
  SierraPersonSubjectsTest.scala, SierraOrganisationSubjectsTest.scala),
  including their test data, so the two can be compared directly. The
  scenarios at the end codify where the Python implementation deliberately
  diverges from Scala; each says what Scala does instead.

  https://www.loc.gov/marc/bibliographic/bd650.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: Subjects are listed by heading type, as the Sierra transformer lists them
    Concept headings first, then personal, corporate and meeting names, as Scala runs one
    transformer per heading type. Within each group, fields keep their document order
    Given the MARC record has a 600 field with indicators "" "0" with subfields:
      | code | value               |
      | a    | Le Tellier, Michel, |
      | d    | 1603-1685.          |
    And the MARC record has a 610 field with indicators "" "0" with subfield "a" value "Collège de Clermont (Paris, France)"
    And the MARC record has a 650 field with indicators "" "0" with subfield "a" value "Philosophy."
    And the MARC record has a 611 field with indicators "" "0" with subfield "a" value "Council of Trent"
    When I transform the MARC record
    Then the work has 4 subjects with label:
      | Philosophy                          |
      | Le Tellier, Michel, 1603-1685.      |
      | Collège de Clermont (Paris, France) |
      | Council of Trent                    |

  Scenario: A topical heading with two "a" subfields gives a concept for each
    Based on Sierra b23990739
    Given the MARC record has a 650 field with indicators "" "0" with subfields:
      | code | value                |
      | a    | Geography            |
      | a    | Textbooks            |
      | v    | Early works to 1800. |
    When I transform the MARC record
    Then the only subject has the label "Geography - Textbooks - Early works to 1800"
    And it has 3 concepts
    And its 1st concept has the source identifier value "geography"
    And its 2nd concept has the source identifier value "textbooks"
    And its 3rd concept has the source identifier value "early works to 1800"

  Scenario: The "a" subfields come first in the label whatever their position
    Based on Sierra b17617558
    Given the MARC record has a 650 field with indicators "" "0" with subfields:
      | code | value         |
      | a    | Death notices |
      | z    | Great Britain |
      | y    | 19th century  |
      | a    | Last words    |
    When I transform the MARC record
    Then the only subject has the label "Death notices - Last words - Great Britain - 19th century"
    And it has 4 concepts
    And its 1st concept has the source identifier value "death notices"
    And its 2nd concept has the source identifier value "last words"
    And its 3rd concept has the type "Place"
    And its 3rd concept has the source identifier value "great britain"
    And its 4th concept has the type "Period"
    And its 4th concept has the source identifier value "19th century"

  Scenario: Whitespace around a topical term is left out of its identifier
    Based on Sierra b14653746
    Given the MARC record has a 650 field with indicators "" "0" with subfield "a" value " Yellowstone National Park"
    When I transform the MARC record
    Then the only subject has the source identifier value "yellowstone national park"
    And its only concept has the source identifier value "yellowstone national park"

  Scenario: Whitespace around a geographic subdivision is left out of its identifier
    Based on Sierra b3083353x. The Scala test leaves out the ǂa, which the bib does have
    Given the MARC record has a 650 field with indicators "" "0" with subfield "a" value "Reproductive rights" and subfield "z" value "United States. "
    When I transform the MARC record
    Then the only subject has the label "Reproductive rights - United States"
    And its 2nd concept has the type "Place"
    And its 2nd concept has the source identifier value "united states"

  Scenario: A corporate name heading uses repeated subordinate units in its label
    Given the MARC record has a 610 field with indicators "" "0" with subfields:
      | code | value          |
      | a    | United States. |
      | b    | Army.          |
      | b    | Cavalry, 7th.  |
      | b    | Company E,     |
      | e    | depicted.      |
    When I transform the MARC record
    Then the only subject has the label "United States. Army. Cavalry, 7th. Company E, depicted"

  Scenario: A corporate name heading with a ǂ0 and second indicator 0 gets an LC Names identifier
    Given the MARC record has a 610 field with indicators "" "0" with subfield "a" value "ACME Corp" and subfield "0" value "n81290903210"
    When I transform the MARC record
    Then the only subject has the label "ACME Corp"
    And it has the source identifier type "lc-names"
    And it has the source identifier value "n81290903210"
    And it has the source identifier ontology type "Organisation"

  Scenario: Repeated ǂ0 with the same value give one identifier
    Given the MARC record has a 610 field with indicators "" "0" with subfield "a" value "ACME Corp" and subfield "0" value "  n1234" and subfield "0" value "n1234"
    When I transform the MARC record
    Then the only subject has the source identifier type "lc-names"
    And it has the source identifier value "n1234"

  Scenario: A corporate name heading with no subfields gives no subject
    Given the MARC record has a 610 field with no subfields
    When I transform the MARC record
    Then there are no subjects

  Scenario: Each corporate name heading gives a subject
    Given the MARC record has a 610 field with indicators "" "0" with subfield "a" value "ACME Corp."
    And the MARC record has another 610 field with indicators "" "0" with subfield "a" value "BBC."
    And the MARC record has another 610 field with indicators "" "0" with subfield "a" value "Charlie's Chocolate Factory."
    When I transform the MARC record
    Then there are 3 subjects

  Scenario: A heading with second indicator 7 is ignored
    Given the MARC record has a 650 field with indicators "" "7" with subfield "a" value "absence" and subfield "0" value "lcsh/123"
    And the MARC record has another 650 field with indicators "" "2" with subfield "a" value "abolition" and subfield "0" value "mesh/456"
    When I transform the MARC record
    Then the only subject has the label "abolition"
    And it has the source identifier value "mesh/456"
    And it has the source identifier type "nlm-mesh"
    And its only concept has the label "abolition"
    And it has the source identifier value "mesh/456"

  Scenario: A heading with second indicator 7 and no ǂ0 is ignored
    Given the MARC record has a 650 field with indicators "" "7" with subfield "a" value "abolition"
    When I transform the MARC record
    Then there are no subjects

  Scenario: A heading from an adopted local vocabulary is ignored
    Unlike for EBSCO, a Homosaurus heading is dropped with every other ǂ2-sourced heading
    Given the MARC record has a 650 field with indicators "" "7" with subfield "a" value "Lesbian history" and subfield "2" value "homoit"
    When I transform the MARC record
    Then there are no subjects

  Scenario: A heading whose second indicator does not name a supported scheme is kept
    Based on Sierra b1000046x. Second indicator 4 means the source is not specified
    Given the MARC record has a 648 field with indicators "" "4" with subfield "a" value "19th century."
    When I transform the MARC record
    Then the only subject has the label "19th century"
    And it has the source identifier type "label-derived"
    And its only concept has the type "Period"

  # Everything below is a deliberate divergence from the Scala implementation.

  Scenario: A MeSH identifier is taken from a personal name heading with second indicator 2
    The Scala transformer gives this subject a label-derived identifier. Python reads
    second indicator 2 as MeSH for every heading type, so the ǂ0 is kept as a MeSH identifier
    Given the MARC record has a 600 field with indicators "" "2" with subfield "a" value "Gerry the Garlic" and subfield "0" value "mesh/456"
    When I transform the MARC record
    Then the only subject has the label "Gerry the Garlic"
    And its only concept has the label "Gerry the Garlic"
    And it has the source identifier type "nlm-mesh"
    And it has the source identifier value "mesh/456"

  Scenario: A blank second indicator does not imply LC Names
    The Scala Sierra transformer assumes LC Names when the second indicator is blank.
    Python leaves the identifier label-derived, so the ǂ0 is not used
    Given the MARC record has a 610 field with subfield "a" value "ACME Corp" and subfield "0" value "n81290903210"
    When I transform the MARC record
    Then the only subject has the label "ACME Corp"
    And it has the source identifier type "label-derived"
    And it has the source identifier value "acme corp"

  Scenario: Blank subfields are left out of the subject label
    Based on Sierra b24000802. Scala keeps the blank subfield in the subject label,
    giving it a leading space, and only leaves it out of the concept label and identifier
    Given the MARC record has a 600 field with indicators "" "0" with subfields:
      | code | value        |
      | a    |              |
      | a    | Turner, John |
    When I transform the MARC record
    Then the only subject has the label "Turner, John"
    And it has the source identifier value "turner, john"
    And its only concept has the label "Turner, John"

  Scenario: A heading with only subdivisions gives no subject
    Based on Sierra b17612421. Scala builds a subject from the subdivisions alone,
    here "Early works to 1800" as a lone Concept
    Given the MARC record has a 650 field with indicators "" "0" with subfield "v" value "Early works to 1800."
    When I transform the MARC record
    Then there are no subjects

  Scenario: Two "a" subfields without subdivisions give concepts with their own identifiers
    Based on Sierra b21633484. Scala gives both concepts the heading's identifier "otology - ear"
    Given the MARC record has a 650 field with indicators "" "0" with subfield "a" value "Otology" and subfield "a" value "Ear."
    When I transform the MARC record
    Then the only subject has the label "Otology - Ear"
    And it has the source identifier value "otology - ear"
    And it has 2 concepts
    And its 1st concept has the source identifier value "otology"
    And its 2nd concept has the source identifier value "ear"

  Scenario: A repeated subfield that duplicates an earlier value is dropped
    Based on Sierra b10221608. Scala keeps the duplicate, giving
    "Foucault, Michel, 1926-1984. Order of things, 1926-1984."
    Given the MARC record has a 600 field with indicators "" "0" with subfields:
      | code | value             |
      | a    | Foucault, Michel, |
      | d    | 1926-1984.        |
      | t    | Order of things,  |
      | d    | 1926-1984.        |
    When I transform the MARC record
    Then an error "Repeated non-repeating subfield" is logged with tag "600" and subfield "d"
    And the only subject has the label "Foucault, Michel, 1926-1984. Order of things,"

  Scenario: The concept of a name heading derives its identifier from its own label
    Based on Sierra b10769286. Scala's ontology type harmonisation gives the sole concept
    of a subject the subject's identifier, here "stanton, louisa, defendant", so the role
    leaks into the concept's identifier
    Given the MARC record has a 600 field with indicators "" "0" with subfields:
      | code | value            |
      | a    | Stanton, Louisa, |
      | e    | defendant        |
    When I transform the MARC record
    Then the only subject has the label "Stanton, Louisa, defendant"
    And it has the source identifier value "stanton, louisa, defendant"
    And it has 1 concept
    And its 1st concept has the label "Stanton, Louisa,"
    And its 1st concept has the source identifier value "stanton, louisa,"

  Scenario: A corporate name heading with two different ǂ0 gets a label-derived identifier
    Scala leaves the concept unidentifiable
    Given the MARC record has a 610 field with indicators "" "0" with subfield "a" value "ACME Corp" and subfield "0" value "n12345" and subfield "0" value "n67890"
    When I transform the MARC record
    Then a warning "Multiple identifier subfields" is logged
    And the only subject has the label "ACME Corp"
    And it has the source identifier type "label-derived"
    And its only concept has the source identifier type "label-derived"
    And it has the source identifier value "acme corp"

  Scenario: A ǂ0 under an unsupported scheme leaves the subject identifier label-derived
    Scala gives the subject an Unidentifiable identifier when the second indicator names
    a scheme it does not read, such as 4, and a ǂ0 is present. Python ignores the ǂ0 and
    derives the identifier from the label, as it does for the concept
    Given the MARC record has a 650 field with indicators "" "4" with subfield "a" value "Medicine" and subfield "0" value "sh85083064"
    When I transform the MARC record
    Then the only subject has the label "Medicine"
    And it has the source identifier type "label-derived"
    And it has the source identifier value "medicine"
    And its only concept has the source identifier type "label-derived"

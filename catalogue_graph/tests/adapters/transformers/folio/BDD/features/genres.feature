Feature: genres (MARC 655)
  A genre is built from each MARC 655. Its label is ǂa followed by the
  subdivision subfields in document order, joined with " - ". Its concepts
  are a GenreConcept for ǂa followed by one concept per subdivision, typed by
  subfield code. Only ǂa can carry an authority identifier, taken from ǂ0
  when the second indicator names a scheme we support; every other concept
  gets a label-derived identifier. Genres with the same label are
  deduplicated, keeping the first.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (SierraGenresTest.scala), including its test data, so the two
  can be compared directly.

  https://www.loc.gov/marc/bibliographic/bd655.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A record with no 655 has no genres
    When I transform the MARC record
    Then there are no genres

  Scenario: Each 655 gives a genre
    Given the MARC record has a 655 field with subfield "a" value "A1 Content" and subfield "z" value "Z1 Content"
    And the MARC record has another 655 field with subfield "a" value "A2 Content" and subfield "v" value "V2 Content"
    When I transform the MARC record
    Then there are 2 genres
    And the 1st genre has the label "A1 Content - Z1 Content"
    And the 2nd genre has the label "A2 Content - V2 Content"

  Scenario: A 655 with an empty label gives no genre
    Given the MARC record has a 655 field with subfield "2" value "rbgenr"
    And the MARC record has another 655 field with subfield "a" value "A2 Content" and subfield "v" value "V2 Content"
    When I transform the MARC record
    Then the only genre has the label "A2 Content - V2 Content"

  Scenario: Genres are deduplicated once transformed
    Given the MARC record has a 655 field with subfield "a" value "Electronic journals"
    And the MARC record has another 655 field with subfield "a" value "Electronic journals"
    And the MARC record has another 655 field with subfield "a" value "Periodical"
    And the MARC record has another 655 field with subfield "a" value "Periodicals" and subfield "2" value "rbgenr"
    And the MARC record has another 655 field with subfield "a" value "Periodicals" and subfield "2" value "lcgft"
    When I transform the MARC record
    Then there are 3 genres
    And the 1st genre has the label "Electronic journals"
    And the 2nd genre has the label "Periodical"
    And the 3rd genre has the label "Periodicals"

  Scenario: A 655 with only ǂa gives a genre with a single concept
    Given the MARC record has a 655 field with subfield "a" value "A Content"
    When I transform the MARC record
    Then the only genre has the label "A Content"
    And it has 1 concept
    And its 1st concept has the type "GenreConcept"
    And its 1st concept has the id.source_identifier.identifier_type.id "label-derived"
    And its 1st concept has the id.source_identifier.ontology_type "Genre"
    And its 1st concept has the id.source_identifier.value "a content"

  Scenario: Trailing punctuation is stripped
    Given the MARC record has a 655 field with subfield "a" value "Printed books."
    When I transform the MARC record
    Then the only genre has the label "Printed books"
    And its 1st concept has the label "Printed books"
    And its 1st concept has the id.source_identifier.value "printed books"

  Scenario: ǂa and ǂv give a GenreConcept and a Concept
    Given the MARC record has a 655 field with subfield "a" value "A Content" and subfield "v" value "V Content"
    When I transform the MARC record
    Then the only genre has the label "A Content - V Content"
    And it has 2 concepts
    And its 1st concept has the type "GenreConcept"
    And its 1st concept has the label "A Content"
    And its 1st concept has the id.source_identifier.value "a content"
    And its 2nd concept has the type "Concept"
    And its 2nd concept has the label "V Content"
    And its 2nd concept has the id.source_identifier.value "v content"

  Scenario: ǂa is always the first concept, wherever it appears in the field
    Given the MARC record has a 655 field with subfield "v" value "V Content" and subfield "a" value "A Content"
    When I transform the MARC record
    Then the only genre has the label "A Content - V Content"
    And its 1st concept has the label "A Content"
    And its 2nd concept has the label "V Content"

  Scenario: Subdivisions keep their document order
    Given the MARC record has a 655 field with subfield "a" value "A Content" and subfield "x" value "X Content" and subfield "v" value "V Content"
    When I transform the MARC record
    Then the only genre has the label "A Content - X Content - V Content"
    And it has 3 concepts
    And its 2nd concept has the label "X Content"
    And its 3rd concept has the label "V Content"

  Scenario: ǂz gives a Place
    Given the MARC record has a 655 field with subfield "z" value "Z Content" and subfield "a" value "A Content"
    When I transform the MARC record
    Then the only genre has the label "A Content - Z Content"
    And its 2nd concept has the type "Place"
    And its 2nd concept has the label "Z Content"
    And its 2nd concept has the id.source_identifier.value "z content"

  Scenario: A MeSH identifier is taken from ǂ0 when the second indicator is 2
    Given the MARC record has a 655 field with indicators "" "2" with subfield "a" value "abolition" and subfield "0" value "mesh/456"
    When I transform the MARC record
    Then the only genre has the label "abolition"
    And its 1st concept has the id.source_identifier.identifier_type.id "nlm-mesh"
    And its 1st concept has the id.source_identifier.ontology_type "Genre"
    And its 1st concept has the id.source_identifier.value "mesh/456"

  # Known divergences from the Scala. Each is deliberate unless it says otherwise.

  Scenario: A Library of Congress identifier in ǂ0 is not used
  The Scala reads ǂ0 as an LCSH or LC Names identifier when the second
  indicator is 0. The Python does not support those schemes yet and falls
  back to a label-derived identifier. Not deliberate: no live 655 carries a
  usable one, and the 61 that carry a local identifier there would make the
  Scala throw.
    Given the MARC record has a 655 field with indicators "" "0" with subfield "a" value "absence" and subfield "0" value "sh85060628"
    When I transform the MARC record
    Then the only genre has the label "absence"
    And its 1st concept has the id.source_identifier.identifier_type.id "label-derived"
    And its 1st concept has the id.source_identifier.value "absence"

  Scenario: A chronological subdivision the Python parser cannot read gets no range
  The Scala strips a leading roman numeral and brackets and parses 1787,
  deriving the identifier "1787". The Python parser handles centuries and
  four-digit years only, and derives the identifier from the label as written.
  Not deliberate: no live ǂy contains a roman numeral.
    Given the MARC record has a 655 field with subfield "y" value "MDCCLXXXVII. [1787]" and subfield "a" value "A Content"
    When I transform the MARC record
    Then the only genre has the label "A Content - MDCCLXXXVII. [1787]"
    And its 2nd concept has the type "Period"
    And its 2nd concept has the label "MDCCLXXXVII. [1787]"
    And its 2nd concept has no range
    And its 2nd concept has the id.source_identifier.value "mdcclxxxvii. [1787]"

  Scenario: A chronological subdivision the Python parser can read gets a range
    Given the MARC record has a 655 field with subfield "a" value "A Content" and subfield "y" value "18th cent."
    When I transform the MARC record
    Then the only genre has the label "A Content - 18th cent"
    And its 2nd concept has the type "Period"
    And its 2nd concept has the range.from_time "1700-01-01T00:00:00Z"
    And its 2nd concept has the range.to_time "1799-12-31T23:59:59.999999999Z"

  Scenario: The concept label for Electronic Books is lower-cased along with the genre label
  The Scala replaces "Electronic Books" in the genre label only, leaving the
  concept label as catalogued. The Python applies the same replacement to both.
    Given the MARC record has a 655 field with subfield "a" value "Electronic Books."
    When I transform the MARC record
    Then the only genre has the label "Electronic books"
    And its 1st concept has the label "Electronic books"

  Scenario: Genres with the same label are deduplicated even when their identifiers differ
  The Scala deduplicates whole Genre objects, so two "Periodicals" with
  different ǂ0 or ǂ2 both survive and the work shows the label twice. The
  Python deduplicates on label and keeps the first.
    Given the MARC record has a 655 field with indicators "" "2" with subfield "a" value "Periodicals" and subfield "0" value "D020492"
    And the MARC record has another 655 field with indicators "" "7" with subfield "a" value "Periodicals." and subfield "2" value "rbgenr"
    When I transform the MARC record
    Then the only genre has the label "Periodicals"
    And its 1st concept has the id.source_identifier.identifier_type.id "nlm-mesh"

  Scenario: A repeated ǂa is logged and only the first used
  The Scala joins every ǂa into the label and makes a GenreConcept of each.
    Given the MARC record has a 655 field with subfield "a" value "Hindi language" and subfield "a" value "Dictionaries."
    When I transform the MARC record
    Then an error "Repeated non-repeating subfield" is logged with tag "655" and subfield "a"
    And the only genre has the label "Hindi language"
    And it has 1 concept

  Scenario: A 655 with subdivisions but no ǂa gives no genre
  The Scala builds a genre from the subdivisions alone, with no primary concept.
    Given the MARC record has a 655 field with subfield "x" value "English" and subfield "y" value "18th century"
    When I transform the MARC record
    Then there are no genres

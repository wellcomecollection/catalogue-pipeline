Feature: lettering (MARC 246)
  Lettering comes from MARC 246 ǂa where the second indicator is "6", which
  marks a caption title. Each matching ǂa is taken in record order and the
  values are joined with a blank line.

  These scenarios mirror the unit tests of the Scala transformer this
  replaces (SierraLetteringTest.scala), including its test data, so the two
  can be compared directly.

  https://www.loc.gov/marc/bibliographic/bd246.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A second indicator of 6 on a field other than 246 is not lettering
    Given the MARC record has a 300 field with indicators "" "6" with subfield "a" value "Alas, ailments are annoying"
    When I transform the MARC record
    Then the work's lettering is absent

  Scenario: A 246 with a second indicator other than 6 is not lettering
    Given the MARC record has a 246 field with indicators "" "7" with subfield "a" value "Alas, ailments are annoying"
    When I transform the MARC record
    Then the work's lettering is absent

  Scenario: A caption-title 246 with no ǂa has no lettering
    Given the MARC record has a 246 field with indicators "" "6" with subfield "b" value "Belligerent beavers beneath a bridge"
    When I transform the MARC record
    Then the work's lettering is absent

  Scenario: A single caption-title 246 ǂa is passed through
    Given the MARC record has a 246 field with indicators "" "6" with subfield "a" value "Crowded crows carry a chocolate crepe"
    When I transform the MARC record
    Then the work's lettering is "Crowded crows carry a chocolate crepe"

  Scenario: Multiple caption-title 246 ǂa are joined with a blank line
    Given the MARC record has a 246 field with indicators "" "6" with subfield "a" value "Daring dalmations dance with danger"
    And the MARC record has another 246 field with indicators "" "6" with subfield "a" value "Enterprising eskimos exile every eagle"
    When I transform the MARC record
    Then the work's lettering is:
      """
      Daring dalmations dance with danger

      Enterprising eskimos exile every eagle
      """

  Scenario: A 514 does not contribute to lettering
    Given the MARC record has a 514 field with subfield "a" value "Lettering continues: Comment va  le malade? Hélas Monsieur, il est mort ce matin à six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."
    And the MARC record has a 246 field with indicators "" "6" with subfield "a" value "Le médecin et la garde malade. H.D. ..."
    When I transform the MARC record
    Then the work's lettering is "Le médecin et la garde malade. H.D. ..."

  # Known divergences from the Scala.
  #
  # The Scala appends 514 ǂa for material type "k" (Pictures). Pictures are
  # not catalogued in FOLIO, so there is no material type to test and 514 is
  # never used; the "non-visual material" case above is the only one ported.
  #
  # The Scala does not trim and keeps blank ǂa values in the join, padding
  # the result with stray spaces and line breaks.

  Scenario: Surrounding whitespace is stripped from each ǂa before joining
    Given the MARC record has a 246 field with indicators "3" "6" with subfield "a" value "Jesus-Hilfe: " and subfield "b" value "Das Aussätzigen-asyl der Brüdergemeine in Jerusalem"
    When I transform the MARC record
    Then the work's lettering is "Jesus-Hilfe:"

  Scenario: Blank caption-title ǂa values are dropped rather than padding the result
    Given the MARC record has a 246 field with indicators "" "6" with subfield "a" value "   "
    And the MARC record has another 246 field with indicators "" "6" with subfield "a" value "Crowded crows carry a chocolate crepe"
    When I transform the MARC record
    Then the work's lettering is "Crowded crows carry a chocolate crepe"

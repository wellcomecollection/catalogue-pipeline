Feature: Axiell work format
  The format field is derived from 655 $a where ind2=7 and $2=local.
  If "Archives - Digital" appears, the work is "Born-digital archives";
  otherwise it defaults to "Archives and manuscripts".

  Background:
    Given a valid MARC record

  Scenario: No 655 field — default format is Archives and manuscripts
    When I transform the MARC record
    Then the work's format.id is "h"
    And the work's format.label is "Archives and manuscripts"

  Scenario: 655 with "Archives - Digital" yields Born-digital archives
    Given the MARC record has a 655 field with indicators " " "7" with subfield "a" value "Archives - Digital" and subfield "2" value "local"
    When I transform the MARC record
    Then the work's format.id is "hdig"
    And the work's format.label is "Born-digital archives"

  Scenario: 655 with another local term yields Archives and manuscripts
    Given the MARC record has a 655 field with indicators " " "7" with subfield "a" value "Photographs" and subfield "2" value "local"
    When I transform the MARC record
    Then the work's format.id is "h"
    And the work's format.label is "Archives and manuscripts"

  Scenario: 655 with wrong ind2 is ignored
    Given the MARC record has a 655 field with indicators " " "0" with subfield "a" value "Archives - Digital" and subfield "2" value "local"
    When I transform the MARC record
    Then the work's format.id is "h"
    And the work's format.label is "Archives and manuscripts"

  Scenario: 655 with $2 not "local" is ignored
    Given the MARC record has a 655 field with indicators " " "7" with subfield "a" value "Archives - Digital" and subfield "2" value "lcsh"
    When I transform the MARC record
    Then the work's format.id is "h"
    And the work's format.label is "Archives and manuscripts"

  Scenario: Multiple 655 fields — "Archives - Digital" among them yields Born-digital archives
    Given the MARC record has a 655 field with indicators " " "7" with subfield "a" value "Photographs" and subfield "2" value "local"
    And the MARC record has another 655 field with indicators " " "7" with subfield "a" value "Archives - Digital" and subfield "2" value "local"
    When I transform the MARC record
    Then the work's format.id is "hdig"
    And the work's format.label is "Born-digital archives"

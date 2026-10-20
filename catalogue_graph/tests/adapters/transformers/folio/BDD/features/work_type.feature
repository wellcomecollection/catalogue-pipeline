Feature: work type
  Every FOLIO work has the work type "Standard".

  Work type (Standard, Collection, Series, Section) is the archive hierarchy
  level of a work, exposed by the catalogue API as "type". It is unrelated to
  format, which the API exposes as "workType". Only the Axiell transformer
  derives it, from the archival level in MARC 351 ǂc. The Sierra transformer
  this replaces never derives it, so every Sierra work is "Standard",
  including bibs linked into a host/constituent hierarchy through MARC 773
  and 774.

  Axiell works will also reach FOLIO through the Axiell to FOLIO sync, but
  only item-level records are synced (see the selection gate in
  src/adapters/steps/axiell_folio_sync/mapping/builders.py), which always map
  to Standard. In any case, the merger will merge the synced instance into
  its Axiell work, whose type then wins, just as Sierra works harvested from
  CALM were merged into their CALM work.

  No other FOLIO record carries a 351 ǂc, so the scenario below only guards
  against the field appearing. The leader bibliographic level is likewise not
  an archive hierarchy, and the Sierra transformer never reads it.

  https://github.com/wellcomecollection/platform/issues/6564

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A minimal record is a Standard work
    When I transform the MARC record
    Then the work's work type is "Standard"

  Scenario: An archival level in 351 ǂc does not change the work type
    Given the MARC record has a 351 field with subfield "c" value "Collection record level."
    When I transform the MARC record
    Then the work's work type is "Standard"

  Scenario: A host record with constituent units is a Standard work
    Given the MARC record has a 774 field with indicators "0" " " with subfield "t" value "Charing Cross Hospital: a portrait of house surgeons." and subfield "w" value "(Wcat)28914i"
    When I transform the MARC record
    Then the work's work type is "Standard"

  Scenario: A constituent record with a host entry is a Standard work
    Given the MARC record has a 773 field with indicators "0" " " with subfield "t" value "Basil Hood. Photograph album," and subfield "w" value "(Wcat)9175i"
    When I transform the MARC record
    Then the work's work type is "Standard"

  Scenario: A collection-level leader does not change the work type
    Given the MARC record's leader is "00000ntc a2200000 a 4500"
    When I transform the MARC record
    Then the work's work type is "Standard"

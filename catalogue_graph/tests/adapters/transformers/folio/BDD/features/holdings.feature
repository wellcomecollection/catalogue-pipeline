Feature: FOLIO work-level holdings
  A FOLIO holdings record with holdings statements becomes a Holdings on the
  work, as set out on platform#6591 and platform#6611. Only holdings made from
  Sierra check-in records carry statements, and most of those sit in the
  migration placeholder while the instance's items are on another holdings
  record in the real location. The location type and label follow the item
  location rule from platform#6584.

  A Holdings never has a note: FOLIO returns no holdings notes, and which note
  type would qualify is still open on platform#6591. FOLIO has no
  librarian-written description separate from the statements, so the
  enumeration is the statements in FOLIO's order.

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A holdings record becomes a Holdings with an enumeration and one location
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "v.1:no.1 (1823) - v.385:no.9969 (2015)"
    And that holdings record has the statement "v.385:no.9972 (2015) - v.388:no.10063 (2016)"
    And that holdings record has the call number "S7946"
    When I transform the MARC record
    Then the holdings have the enumeration "v.1:no.1 (1823) - v.385:no.9969 (2015) | v.385:no.9972 (2015) - v.388:no.10063 (2016)"
    And the holdings have a location of type "closed-stores" labelled "Closed stores"
    And the holdings have the shelfmark "S7946"
    And the holdings have no note

  Scenario: Statements keep FOLIO's order
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "v. 3 (1946)"
    And that holdings record has the statement "Vol. 1 only"
    And that holdings record has the statement "v. 2 (1945), nos. 2,4-6"
    When I transform the MARC record
    Then the holdings have the enumeration "v. 3 (1946) | Vol. 1 only | v. 2 (1945), nos. 2,4-6"

  Scenario: Statements joined with a semicolon are split
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "Vol. 1 (1876)-v. 16 (1891) ; ;v. 24, no. 96 (Oct. 1915);  "
    When I transform the MARC record
    Then the holdings have the enumeration "Vol. 1 (1876)-v. 16 (1891) | v. 24, no. 96 (Oct. 1915)"

  Scenario: A holdings record in an open shelves location is labelled with its name
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "Vol. 1 (1922)-v. 5 (1931)"
    And that holdings record is in the location "wslom" named "Student Coll. (Med Lit)" in the library "Open shelves"
    When I transform the MARC record
    Then the holdings have a location of type "open-shelves" labelled "Student Coll. (Med Lit)"
    And the holdings have no shelfmark

  Scenario: A work without an inventory record has no holdings
    Given the instance has no inventory record
    When I transform the MARC record
    Then there are no holdings

  Scenario: A work with no holdings records has no holdings
    Given a FOLIO item "a"
    When I transform the MARC record
    Then there are no holdings

  Scenario: A holdings record without statements gives no holdings
    Given a FOLIO holdings record "items"
    And that holdings record has the holdings type "Physical"
    When I transform the MARC record
    Then there are no holdings

  Scenario: A holdings record whose statements are blank gives no holdings
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "   "
    And that holdings record has the statement " ; "
    When I transform the MARC record
    Then there are no holdings

  Scenario: Two holdings records that give the same holdings are deduplicated
    Given a FOLIO holdings record "first"
    And that holdings record has the statement "Complete set"
    And a FOLIO holdings record "second"
    And that holdings record has the statement "Complete set"
    When I transform the MARC record
    Then there is 1 holdings

  Scenario: Holdings records that differ are all kept
    Given a FOLIO holdings record "first"
    And that holdings record has the statement "Vol. 4 only"
    And a FOLIO holdings record "second"
    And that holdings record has the statement "Complete set"
    When I transform the MARC record
    Then there are 2 holdings

  Scenario: A holdings record at the Online location gives no holdings
    Given a FOLIO holdings record "online"
    And that holdings record has the statement "1.1 1999-2023 01-08 01-28"
    And that holdings record is in the location "elro" named "Online" in the library "Online"
    When I transform the MARC record
    Then there are no holdings
    And there are no items

  Scenario: A suppressed holdings record gives no holdings
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "Complete set"
    And that holdings record is suppressed from discovery
    When I transform the MARC record
    Then there are no holdings

  # The location fallback: a check-in holdings record in the migration
  # placeholder takes the location of the instance's other holdings, but not
  # their call number. That is the items' shelfmark, which Sierra kept off
  # holdings and the catalogue already shows on the items.

  Scenario Outline: A holdings record in the <code> location takes the location of the instance's other holdings
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "Vol. 7 (Jan.-Jun. 1896) - vol. 22 (Jul.-Dec. 1903)"
    And that holdings record is in the location "<code>" named "<name>" in the library "<library>"
    And a FOLIO holdings record "items"
    And that holdings record has the holdings type "Physical"
    And that holdings record is in the location "wgser" named "Open shelves journals" in the library "Open shelves"
    And that holdings record has the call number "W/J 12"
    When I transform the MARC record
    Then the holdings have the enumeration "Vol. 7 (Jan.-Jun. 1896) - vol. 22 (Jul.-Dec. 1903)"
    And the holdings have a location of type "open-shelves" labelled "Open shelves journals"
    And the holdings have no shelfmark

    Examples:
      | code      | name        | library       |
      | migration | migration   | Migration     |
      | none      | No location | Closed stores |

  Scenario: A holdings record keeps its own call number when it takes another location
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "Complete set"
    And that holdings record is in the location "migration" named "migration" in the library "Migration"
    And that holdings record has the call number "S8141"
    And a FOLIO holdings record "items"
    And that holdings record has the call number "S7946"
    When I transform the MARC record
    Then the holdings have a location of type "closed-stores" labelled "Closed stores"
    And the holdings have the shelfmark "S8141"

  Scenario: Other holdings in different locations give no location
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "Complete set"
    And that holdings record is in the location "migration" named "migration" in the library "Migration"
    And a FOLIO holdings record "closed"
    And a FOLIO holdings record "open"
    And that holdings record is in the location "wgser" named "Open shelves journals" in the library "Open shelves"
    When I transform the MARC record
    Then the holdings have the enumeration "Complete set"
    And the holdings have no location

  Scenario: A holdings record in the migration placeholder with no other holdings has no location
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "Complete set"
    And that holdings record is in the location "migration" named "migration" in the library "Migration"
    When I transform the MARC record
    Then the holdings have no location

  Scenario: Suppressed and bound-with holdings don't give a location
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "Complete set"
    And that holdings record is in the location "migration" named "migration" in the library "Migration"
    And a FOLIO holdings record "suppressed"
    And that holdings record is suppressed from discovery
    And a FOLIO holdings record "boundwiths"
    And that holdings record has the holdings type "Boundwiths"
    And that holdings record is in the location "wgser" named "Open shelves journals" in the library "Open shelves"
    When I transform the MARC record
    Then the holdings have no location

  Scenario: Other holdings in different closed stores locations give closed stores
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "Complete set"
    And that holdings record is in the location "migration" named "migration" in the library "Migration"
    And a FOLIO holdings record "journals"
    And a FOLIO holdings record "offsite"
    And that holdings record is in the location "ofvn1" named "Offsite Closed stores" in the library "Offsite (DS)"
    When I transform the MARC record
    Then the holdings have a location of type "closed-stores" labelled "Closed stores"

  Scenario: A holdings record in an unmapped location gives no location and a warning
    Given a FOLIO holdings record "checkin"
    And that holdings record has the statement "Complete set"
    And that holdings record is in the location "newco" named "Somewhere new" in the library "Somewhere"
    When I transform the MARC record
    Then the holdings have no location
    And a warning "Unmapped FOLIO location" is logged

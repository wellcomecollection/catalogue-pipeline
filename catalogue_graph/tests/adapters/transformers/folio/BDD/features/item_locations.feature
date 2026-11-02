Feature: FOLIO item locations
  Each FOLIO item gets a physical location from its effective location, as
  agreed in platform#6584 (docs/folio/location-mapping.md). The location type
  comes from the location's library, with exceptions by location code. The
  label is "Closed stores" for closed stores and the location's name otherwise,
  and the shelfmark is the item's effective call number.

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"
    And a FOLIO item "a"

  Scenario Outline: A location in the <library> library is <location_type>
    Given that item is in the location "<code>" named "<name>" in the library "<library>"
    When I transform the MARC record
    Then item "a" has a location of type "<location_type>" labelled "<label>"

    Examples:
      | code        | name                    | library       | location_type | label                   |
      | sepbb       | Closed stores EPB / B   | Closed stores | closed-stores | Closed stores           |
      | ofvn1       | Offsite Closed stores   | Offsite (DS)  | closed-stores | Closed stores           |
      | wslom       | Student Coll. (Med Lit) | Open shelves  | open-shelves  | Student Coll. (Med Lit) |
      | Exhibitions | On exhibition           | On Exhibition | on-exhibition | On exhibition           |

  Scenario Outline: The <code> location is closed stores whatever its library
    Given that item is in the location "<code>" named "<name>" in the library "<library>"
    When I transform the MARC record
    Then item "a" has a location of type "closed-stores" labelled "Closed stores"

    Examples:
      | code         | name                        | library          |
      | DIGITISATION | Digitisation                | Digitisation     |
      | axce         | AxC Euston Road             | Axiell sync (EUA) |
      | axcc         | AxC Constantine London West | Axiell sync      |
      | hicon        | Offsite Closed stores CLW   | Axiell sync      |

  Scenario Outline: The <code> location gives no location
    Given that item is in the location "<code>" named "<name>" in the library "<library>"
    When I transform the MARC record
    Then item "a" has no location

    Examples:
      | code      | name                            | library           |
      | none      | No location                     | Closed stores     |
      | migration | migration                       | Open shelves      |
      | elro      | Online                          | Closed stores     |
      | ILL       | Interlibrary Loan               | Interlibrary Loan |
      | ACQL      | Acquisitions Temporary Location | Acquisitions      |
      | enhal     | Enquiry Hall                    | Laptops           |

  Scenario: A location in an unmapped library gives no location and a warning
    Given that item is in the location "newco" named "Somewhere new" in the library "Somewhere"
    When I transform the MARC record
    Then item "a" has no location
    And a warning "Unmapped FOLIO location" is logged

  Scenario: A location with a blank name gives no location and a warning
    Given that item is in the location "wslom" named "   " in the library "Open shelves"
    When I transform the MARC record
    Then item "a" has no location
    And a warning "FOLIO location has no name" is logged

  Scenario: An item with no location data has no location
    Given that item has no location
    When I transform the MARC record
    Then item "a" has no location

  Scenario: An item without a location keeps its display note
    Given that item is in the location "none" named "No location" in the library "Closed stores"
    And that item has the display note "Impression lacking lettering."
    When I transform the MARC record
    Then item "a" has no location
    And item "a" has the note "Impression lacking lettering."

  Scenario: The shelfmark is the item's effective call number
    Given that item has the call number "EPB/B/28531.v1"
    When I transform the MARC record
    Then item "a" has the shelfmark "EPB/B/28531.v1"

  Scenario: An item with no call number has no shelfmark
    Given that item has no call number
    When I transform the MARC record
    Then item "a" has no shelfmark

  Scenario: A suppressed item is left off the work
    Given that item is suppressed from discovery
    And a FOLIO item "b"
    When I transform the MARC record
    Then the work has no item "a"
    And item "b" has a location of type "closed-stores" labelled "Closed stores"

  # Bound in above and contained in above. items-and-holdings returns the host
  # item on the same instance, so the bound-with item takes the location its
  # other items share, as Sierra does per bib.

  Scenario Outline: A <code> item takes its host item's location
    Given that item is in the location "<code>" named "<name>" in the library "<code>"
    And a FOLIO item "host"
    And that item is in the location "wslom" named "Student Coll. (Med Lit)" in the library "Open shelves"
    When I transform the MARC record
    Then item "a" has a location of type "open-shelves" labelled "Student Coll. (Med Lit)"

    Examples:
      | code  | name               |
      | bwith | bound in above     |
      | cwith | contained in above |

  Scenario: A bound-with item takes closed stores when its other items are in different closed stores
    Given that item is in the location "bwith" named "bound in above" in the library "bwith"
    And a FOLIO item "journals"
    And that item is in the location "sgser" named "Closed stores journals" in the library "Closed stores"
    And a FOLIO item "offsite"
    And that item is in the location "ofvn1" named "Offsite Closed stores" in the library "Offsite (DS)"
    When I transform the MARC record
    Then item "a" has a location of type "closed-stores" labelled "Closed stores"

  Scenario: A bound-with item ignores other items with no location
    Given that item is in the location "bwith" named "bound in above" in the library "bwith"
    And a FOLIO item "host"
    And a FOLIO item "unlocated"
    And that item is in the location "none" named "No location" in the library "Closed stores"
    When I transform the MARC record
    Then item "a" has a location of type "closed-stores" labelled "Closed stores"

  Scenario: A bound-with item whose other items disagree has no location
    Given that item is in the location "bwith" named "bound in above" in the library "bwith"
    And a FOLIO item "closed"
    And a FOLIO item "open"
    And that item is in the location "wslom" named "Student Coll. (Med Lit)" in the library "Open shelves"
    When I transform the MARC record
    Then item "a" has no location
    And a warning "Bound-with item has ambiguous host locations" is logged

  Scenario: A bound-with item with no other items has no location
    Given that item is in the location "bwith" named "bound in above" in the library "bwith"
    When I transform the MARC record
    Then item "a" has no location

  Scenario: A suppressed item doesn't give a bound-with item its location
    Given that item is in the location "bwith" named "bound in above" in the library "bwith"
    And a FOLIO item "host"
    And that item is suppressed from discovery
    When I transform the MARC record
    Then item "a" has no location

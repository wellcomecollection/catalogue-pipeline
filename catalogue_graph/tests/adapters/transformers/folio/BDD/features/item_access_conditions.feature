Feature: FOLIO item access conditions
  Each FOLIO item with a location gets one access condition on that location,
  from the first matching row of the decision table agreed in platform#6589
  (docs/folio/access-conditions/decision-table.md, with the rows in
  src/adapters/transformers/folio/access_rules/rules.csv). The rule ids below
  are the table's own.

  The table's open-request row isn't covered, because only the items service
  (platform#6654) knows about open requests, so the transform never reaches it.

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"
    And a FOLIO item "a"

  Scenario Outline: <rule>
    Given that item has the status "<item_status>"
    And that item has the statistical code "<code>"
    And that item has the loan type "<loan_type>"
    And that item is in the location "<location_code>" named "<location_name>" in the library "<library>"
    And that item has the material type "<material_type>"
    When I transform the MARC record
    Then item "a" has the access method "<method>", <status> and <note>

    Examples:
      | rule                         | item_status                  | code             | loan_type       | location_code | location_name           | library       | material_type  | method          | status                            | note                        |
      | missing                      | Missing                      | OPEN SHELVES     | Can circulate   | wslom         | Student Coll. (Med Lit) | Open shelves  | book           | NotRequestable  | the status "Unavailable"          | the "missing" note          |
      | withdrawn                    | Withdrawn                    | UNAVAILABLE      | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | NotRequestable  | the status "Unavailable"          | the "withdrawn" note        |
      | data-issues                  | Unknown                      | ONLINE REQUEST   | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | NotRequestable  | no status                         | the "contact" note          |
      | on-search                    | Declared lost                | ONLINE REQUEST   | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | NotRequestable  | no status                         | the "contact" note          |
      | bound-with                   | Intellectual item            | ONLINE REQUEST   | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | NotRequestable  | no status                         | the "top-item" note         |
      | digitisation                 | Unavailable                  | DIGITISATION     | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | NotRequestable  | the status "TemporarilyUnavailable" | the "digitisation" note   |
      | digitisation-status          | In process (non-requestable) | ONLINE REQUEST   | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | NotRequestable  | the status "TemporarilyUnavailable" | the "digitisation" note   |
      | closed                       | Available                    | CLOSED           | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | NotRequestable  | the status "Closed"               | no note                     |
      | unavailable                  | Unavailable                  | UNAVAILABLE      | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | NotRequestable  | the status "TemporarilyUnavailable" | the "assessment" note     |
      | safeguarded                  | Checked out                  | SAFEGUARDED      | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | NotRequestable  | the status "Safeguarded"          | no note                     |
      | in-circulation-closed-stores | Awaiting pickup              | ONLINE REQUEST   | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | NotRequestable  | the status "TemporarilyUnavailable" | the "in-use" note         |
      | in-circulation-open-shelves  | Checked out                  | OPEN SHELVES     | Can circulate   | wslom         | Student Coll. (Med Lit) | Open shelves  | book           | OpenShelves     | the status "TemporarilyUnavailable" | the "in-use" note         |
      | on-exhibition                | Available                    | MANUAL REQUEST   | Can circulate   | Exhibitions   | On exhibition           | On Exhibition | book           | NotRequestable  | no status                         | the "contact" note          |
      | on-exhibition-material       | Available                    | ONLINE REQUEST   | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | exhibit        | NotRequestable  | no status                         | the "contact" note          |
      | restricted                   | Available                    | RESTRICTED       | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | OnlineRequest   | the status "Restricted"           | no note                     |
      | by-appointment               | Available                    | BY APPOINTMENT   | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | ManualRequest   | the status "ByAppointment"        | no note                     |
      | donor-permission             | Available                    | DONOR PERMISSION | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | ManualRequest   | the status "PermissionRequired"   | no note                     |
      | manual-request               | Available                    | MANUAL REQUEST   | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | ManualRequest   | no status                         | the "manual-request" note   |
      | offsite-manual-request       | Available                    | ONLINE REQUEST   | Can circulate   | somet         | Offsite Closed stores   | Offsite (DS)  | book           | NotRequestable  | no status                         | the "contact" note          |
      | computer-media               | Available                    | ONLINE REQUEST   | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | computer media | NotRequestable  | no status                         | the "contact" note          |
      | not-circulating              | Available                    | ONLINE REQUEST   | Can't circulate | sepbb         | Closed stores EPB / B   | Closed stores | book           | NotRequestable  | no status                         | the "contact" note          |
      | open-shelves                 | Available                    | OPEN SHELVES     | Can circulate   | wslom         | Student Coll. (Med Lit) | Open shelves  | book           | OpenShelves     | no status                         | no note                     |
      | online-request               | Available                    | ONLINE REQUEST   | Can circulate   | sepbb         | Closed stores EPB / B   | Closed stores | book           | OnlineRequest   | the status "Open"                 | no note                     |
      | fallback                     | Available                    | ONLINE REQUEST   | Can circulate   | wslom         | Student Coll. (Med Lit) | Open shelves  | book           | NotRequestable  | no status                         | the "contact" note          |

  Scenario: bound-with-interim catches a bound-with item that is still Available
    Given that item is in the location "bwith" named "bound in above" in the library "bwith"
    And a FOLIO item "host"
    When I transform the MARC record
    Then item "a" has the access method "NotRequestable", no status and the "top-item" note
    And item "host" has the access method "OnlineRequest", the status "Open" and no note

  # Precedence between rows.

  Scenario: An item in circulation is in use even when it needs a manual request
    Given that item has the status "Paged"
    And that item has the statistical code "MANUAL REQUEST"
    When I transform the MARC record
    Then item "a" has the access method "NotRequestable", the status "TemporarilyUnavailable" and the "in-use" note

  Scenario: An item on exhibition is shown as on exhibition even when it's checked out
    Given that item has the status "Checked out"
    And that item is in the location "Exhibitions" named "On exhibition" in the library "On Exhibition"
    When I transform the MARC record
    Then item "a" has the access method "NotRequestable", no status and the "contact" note

  Scenario: The temporary loan type wins over the permanent one
    Given that item has the temporary loan type "Can't circulate"
    When I transform the MARC record
    Then item "a" has the access method "NotRequestable", no status and the "contact" note

  Scenario: An item with no statistical code reaches the fallback
    Given that item has no statistical code
    When I transform the MARC record
    Then item "a" has the access method "NotRequestable", no status and the "contact" note

  # Display note precedence, from the decision table's "Note precedence" section.

  Scenario: A manual-request display note replaces the row's placeholder note
    Given that item has the statistical code "MANUAL REQUEST"
    And that item has the display note "Email library@wellcomecollection.org to tell us why you need access."
    When I transform the MARC record
    Then item "a" has the access note "Email library@wellcomecollection.org to tell us why you need access."
    And item "a" has no note

  Scenario Outline: An access display note fills the note on a <rule> item
    Given that item has the statistical code "<code>"
    And that item is in the location "<location_code>" named "<location_name>" in the library "<library>"
    And that item has the display note "<display_note>"
    When I transform the MARC record
    Then item "a" has the access note "<display_note>"
    And item "a" has no note

    # The rows Sierra's SierraItemAccessTest checks this for.
    Examples:
      | rule           | code           | location_code | location_name           | library       | display_note                                                                                     |
      | online-request | ONLINE REQUEST | sepbb         | Closed stores EPB / B   | Closed stores | Shelved at the Library Enquiry Desk.                                                             |
      | open-shelves   | OPEN SHELVES   | wslom         | Student Coll. (Med Lit) | Open shelves  | Shelved at the end of the Quick Ref. section with the oversize Quick Ref. books.                 |
      | by-appointment | BY APPOINTMENT | sepbb         | Closed stores EPB / B   | Closed stores | Email library@wellcomecollection.org to tell us why you need the physical copy.                  |
      | safeguarded    | SAFEGUARDED    | sepbb         | Closed stores EPB / B   | Closed stores | This item requires safeguarded access. Please contact collections@wellcomecollection.org.        |

  Scenario Outline: An access display note is dropped when the <rule> row sets its own note
    Given that item has the status "<item_status>"
    And that item has the display note "Email library@wellcomecollection.org to tell us why you need access."
    When I transform the MARC record
    Then item "a" has the access method "NotRequestable", <status> and the "<note>" note
    And item "a" has no note

    Examples:
      | rule                         | item_status     | status                              | note    |
      | missing                      | Missing         | the status "Unavailable"            | missing |
      | in-circulation-closed-stores | Awaiting pickup | the status "TemporarilyUnavailable" | in-use  |

  # Sierra's version uses the digitisation note, but that reads as access
  # information anyway, so this uses a note with no access wording.
  Scenario: A display note identical to the row's note isn't repeated on the item
    Given that item has the status "Missing"
    And that item has the display note "This item is missing."
    When I transform the MARC record
    Then item "a" has the access method "NotRequestable", the status "Unavailable" and the "missing" note
    And item "a" has no note

  Scenario: A display note about something else stays on the item
    Given that item has the display note "Impression lacking lettering."
    When I transform the MARC record
    Then item "a" has the access method "OnlineRequest", the status "Open" and no note
    And item "a" has the note "Impression lacking lettering."

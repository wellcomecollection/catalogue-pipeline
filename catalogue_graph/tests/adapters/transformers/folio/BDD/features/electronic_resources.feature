Feature: electronic resources (MARC 856)
  Ported from the Scala SierraElectronicResources transformer
  (SierraElectronicResourcesTest.scala), as agreed in platform#6612: every
  link in FOLIO is an 856 on the instance, so each 856 with a resolvable URL
  becomes an item with a digital location, after any physical items. The
  label is the concatenation of ǂz, ǂy and ǂ3; a short call to action becomes
  the link text and anything else the item title. ind2 = 2 marks a related
  resource. The local 956 stays unmapped, and holdings and items carry no
  electronic access (platform#6766).

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: An 856 becomes an item with a digital location at its URL
    Given the MARC record has a 856 field with subfield "u" value "https://example.org/journal"
    When I transform the MARC record
    Then the only item has the id type "Unidentifiable"
    And it has no title
    And its only location has the url "https://example.org/journal"
    And it has the location type "online-resource"
    And it has no link text
    And its only access condition has the access method "ViewOnline"
    And it has the access status "LicensedResources"
    And it has the access status relationship "Resource"

  Scenario: A repeated 856 gives one item per field, in order
    Given the MARC record has a 856 field with subfield "u" value "https://example.org/journal"
    And the MARC record has another 856 field with subfield "u" value "https://example.org/another-journal"
    When I transform the MARC record
    Then there are 2 items
    And the 1st item has the id type "Unidentifiable"
    And its only location has the url "https://example.org/journal"
    And the 2nd item has the id type "Unidentifiable"
    And its only location has the url "https://example.org/another-journal"

  Scenario: Digital items follow the physical items
    Given a FOLIO item "a"
    And the MARC record has a 856 field with subfield "u" value "https://example.org/journal"
    When I transform the MARC record
    Then there are 2 items
    And the 1st item has the id type "Identifiable"
    And the 2nd item has the id type "Unidentifiable"

  Scenario: ind2 = 2 marks a related resource
    Given the MARC record has a 856 field with indicators "4" "2" with subfield "u" value "https://example.org/journal"
    When I transform the MARC record
    Then the only item has the id type "Unidentifiable"
    And its only location has the url "https://example.org/journal"
    And its only access condition has the access status relationship "RelatedResource"

  Scenario: The label concatenates ǂz, ǂy and ǂ3 in the order they appear
    Given the MARC record has a 856 field with subfield "u" value "https://example.org/journal" and subfield "3" value "Related archival materials:" and subfield "z" value "available to library members." and subfield "z" value "Cambridge Books Online."
    When I transform the MARC record
    Then the only item has the title "Related archival materials: available to library members. Cambridge Books Online."
    And its only location has no link text

  Scenario Outline: A short call to action is the link text
    Given the MARC record has a 856 field with subfield "u" value "https://example.org/resource" and subfield "<code>" value "<label>"
    When I transform the MARC record
    Then the only item has the id type "Unidentifiable"
    And it has no title
    And its only location has the link text "<label>"

    Examples:
      | code | label              |
      | 3    | View online        |
      | z    | Access resource    |
      | z    | Connect to journal |
      | y    | View online        |

  Scenario: A short label that is not a call to action is the title
    Given the MARC record has a 856 field with subfield "u" value "https://example.org/oxford" and subfield "3" value "Oxford Libraries Online"
    When I transform the MARC record
    Then the only item has the title "Oxford Libraries Online"
    And its only location has no link text

  Scenario: A long call to action is the title
    Given the MARC record has a 856 field with subfield "u" value "https://example.org/zine" and subfield "z" value "This zine is available to view or download from the author's website"
    When I transform the MARC record
    Then the only item has the title "This zine is available to view or download from the author's website"
    And its only location has no link text

  Scenario Outline: Link text is tidied
    Given the MARC record has a 856 field with subfield "u" value "https://example.org/resource" and subfield "3" value "<label>"
    When I transform the MARC record
    Then the only item has the id type "Unidentifiable"
    And it has no title
    And its only location has the link text "<link text>"

    Examples: Whitespace is trimmed and trailing punctuation stripped
      | label           | link text     |
      | View resource   | View resource |
      | View resource.  | View resource |
      | View resource:  | View resource |

    Examples: "view" is capitalised only at the start of the label
      | label                             | link text                         |
      | view resource                     | View resource                     |
      | You can view this resource online | You can view this resource online |

    Examples: Known odd records are fixed
      | label                   | link text               |
      | VIEW FULL TEXT          | View full text          |
      | View resource {PDF      | View resource [PDF      |
      | View resource 613.7 KB] | View resource [613.7 KB] |
      | View on youtube         | View on YouTube         |

  Scenario: A record with no 856 has no items
    Given the MARC record has a 855 field with subfield "u" value "https://example.org/journal"
    And the MARC record has a 857 field with subfield "u" value "https://example.org/journal"
    When I transform the MARC record
    Then there are no items

  Scenario Outline: An 856 whose ǂu is not a URL is skipped and logged
    Given the MARC record has a 856 field with subfield "u" value "<value>" and subfield "z" value "View resource"
    When I transform the MARC record
    Then there are no items
    And a warning "856 ǂu is not a URL" is logged

    Examples:
      | value                                          |
      | search for 'online journals'                   |
      | www.rcuk.ac.uk/documents/documents/report.pdf  |
      | https://[example.org/resource                  |

  Scenario: An 856 without ǂu is skipped and logged
    Given the MARC record has a 856 field with subfield "a" value "https://example.org/journal"
    When I transform the MARC record
    Then there are no items
    And a warning "856 without a URL" is logged

  Scenario: A suppressed instance with an 856 is deleted
    Given the MARC record has another 999 field with subfield "t" value "1"
    And the MARC record has a 856 field with subfield "u" value "https://example.org/journal"
    When I transform the MARC record
    Then the work is deleted

  # Deliberate divergences: a repeated ǂu takes the first valid URL and logs a
  # warning, where Sierra dropped the field; and only http and https count as
  # URLs, so the handful of ftp links are skipped.

  Scenario: A repeated ǂu takes the first URL and logs a warning
    Given the MARC record has a 856 field with subfield "u" value "https://example.org/journal" and subfield "u" value "https://example.org/another-journal"
    When I transform the MARC record
    Then the only item has the id type "Unidentifiable"
    And its only location has the url "https://example.org/journal"
    And a warning "Repeated 856 ǂu" is logged

  Scenario: A repeated ǂu skips a value that is not a URL
    Given the MARC record has a 856 field with subfield "u" value "search for 'online journals'" and subfield "u" value "https://example.org/journal"
    When I transform the MARC record
    Then the only item has the id type "Unidentifiable"
    And its only location has the url "https://example.org/journal"
    And a warning "Repeated 856 ǂu" is logged
    And a warning "856 ǂu is not a URL" is logged

  Scenario: An ftp link is skipped and logged
    Given the MARC record has a 856 field with subfield "u" value "ftp://ftp.cordis.europa.eu/pub/docs/ss_en.pdf" and subfield "z" value "View resource"
    When I transform the MARC record
    Then there are no items
    And a warning "856 ǂu is not a URL" is logged

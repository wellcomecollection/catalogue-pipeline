Feature: <msIdentifier> in msDesc
  The only part of <msIdentifier> the catalogue reads is
  <altIdentifier type="Sierra">, holding a Sierra b-number. A manuscript
  catalogued in more than one Sierra record can have one for each.

  For each b-number you give, the TEI catalogue entry and that Sierra
  catalogue entry are combined into one.

  Other altIdentifiers (e.g. type="former"), and settlement, repository and
  collection, are not used.

  Background:
    Given I write a TEI file for manuscript "manuscript_15651"

  Scenario Outline: A Sierra b-number links the manuscript to Sierra
    The type attribute can be in any case. Spaces around the b-number are ignored.
    Given the <msIdentifier> contains:
      """xml
      <altIdentifier type="former"><idno>WMS. Or. 1a (Iskandar)</idno></altIdentifier>
      <altIdentifier type="<type>"><idno> b1234567x </idno></altIdentifier>
      """
    When the TEI file is published to the catalogue
    Then the manuscript is merged with the Sierra record "b1234567x"
    Examples:
      | type   |
      | Sierra |
      | sierra |

  Scenario Outline: A b-number in the wrong format is ignored
    A b-number is "b", seven digits, then a check digit (0-9 or x). Without the
    check digit the record can't be matched. The file is not rejected, but no
    merge happens.
    Given the <msIdentifier> contains:
      """xml
      <altIdentifier type="Sierra"><idno><bnumber></idno></altIdentifier>
      """
    When the TEI file is published to the catalogue
    Then the manuscript is not merged with any Sierra record
    Examples:
      | bnumber    |
      | b1234567   |
      | 1234567x   |
      | i1234567x  |
      | b1234567xx |

  Scenario: Former shelfmarks are not used
    Given the <msIdentifier> contains:
      """xml
      <altIdentifier type="former"><idno>WMS. Or. 1a</idno></altIdentifier>
      """
    When the TEI file is published to the catalogue
    Then the manuscript is not merged with any Sierra record
    And "WMS. Or. 1a" does not appear in the catalogue entry

  Scenario: Several Sierra b-numbers link the manuscript to each Sierra record
    A b-number given more than once is only used once. Any that are in the
    wrong format are ignored, as above, without affecting the others.
    Given the <msIdentifier> contains:
      """xml
      <altIdentifier type="Sierra"><idno>b1234567x</idno></altIdentifier>
      <altIdentifier type="Sierra"><idno>b7654321x</idno></altIdentifier>
      <altIdentifier type="Sierra"><idno>b1234567x</idno></altIdentifier>
      <altIdentifier type="Sierra"><idno>b1234567</idno></altIdentifier>
      """
    When the TEI file is published to the catalogue
    Then the manuscript is merged with the Sierra records:
      | b1234567x |
      | b7654321x |

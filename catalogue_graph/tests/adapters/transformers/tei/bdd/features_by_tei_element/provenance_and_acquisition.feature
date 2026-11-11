Feature: <provenance> and <acquisition> in history
  These are only read from msDesc/history, and are shown on the manuscript's
  own entry:
    - <provenance>  -> "Ownership note"
    - <acquisition> -> "Acquisition note"
  They are NOT read from msPart/history. <history>/<summary> is not used.

  Dates given as attributes (when, from, notBefore, to, notAfter) are written
  at the start of the note in brackets. They always appear in this order,
  whatever order you write them in: when, from, not before, to, not after.

  Background:
    Given I write a TEI file for manuscript "MS_Arabic_1"

  Scenario: Provenance
    Given the <history> contains:
      """xml
      <provenance>Owned by Sir Henry Wellcome.</provenance>
      """
    When the TEI file is published to the catalogue
    Then the manuscript has an "Ownership note" reading "Owned by Sir Henry Wellcome."

  Scenario: Acquisition
    Given the <history> contains:
      """xml
      <acquisition>Purchased at Sotheby's.</acquisition>
      """
    When the TEI file is published to the catalogue
    Then the manuscript has an "Acquisition note" reading "Purchased at Sotheby's."

  Scenario Outline: Date attributes
    Given the <history> contains:
      """xml
      <provenance <attributes>>Owned by Sir Henry Wellcome.</provenance>
      """
    When the TEI file is published to the catalogue
    Then the manuscript has an "Ownership note" reading "<note>"
    Examples:
      | attributes                       | note                                                            |
      | when="1905"                      | (1905): Owned by Sir Henry Wellcome.                            |
      | from="1890" to="1936"            | (from 1890, to 1936): Owned by Sir Henry Wellcome.              |
      | to="1936" from="1890"            | (from 1890, to 1936): Owned by Sir Henry Wellcome.              |
      | notBefore="1890" notAfter="1936" | (not before 1890, not after 1936): Owned by Sir Henry Wellcome. |
      | when="1905" notAfter="1936"      | (1905, not after 1936): Owned by Sir Henry Wellcome.            |

  Scenario: Dates without any text produce no note
    Given the <history> contains:
      """xml
      <acquisition when="1936"/>
      """
    When the TEI file is published to the catalogue
    Then the manuscript has no notes

  Scenario: Provenance notes are shown before acquisition notes, after all other notes
    Given the <history> contains:
      """xml
      <acquisition>Purchased at Sotheby's.</acquisition>
      <provenance>Owned by Sir Henry Wellcome.</provenance>
      """
    And the <msContents> contains:
      """xml
      <colophon>Copied in 1100 AH</colophon>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's notes are, in order:
      | note             | text                         |
      | Colophon note    | Copied in 1100 AH            |
      | Ownership note   | Owned by Sir Henry Wellcome. |
      | Acquisition note | Purchased at Sotheby's.      |

  Scenario: Provenance in an msPart is not shown
    Given the <msDesc> contains:
      """xml
      <msPart xml:id="Part_1">
        <history><provenance>Owned by Sir Henry Wellcome.</provenance></history>
      </msPart>
      """
    When the TEI file is published to the catalogue
    Then the catalogue entry for "Part_1" has no notes
    And the manuscript has no notes

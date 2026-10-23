Feature: <msPart>
  In a multi-part manuscript, each <msPart> becomes its own catalogue entry,
  linked to the manuscript in a collection hierarchy. msItems inside a part's
  msContents become entries below the part.

  msParts are only used when msDesc/msContents has no msItems of its own.

  Parts have no title of their own in TEI, so each one is titled with the
  manuscript's msID followed by "part" and its position, counting from 1.

  Each msPart must have an xml:id. It identifies the part's catalogue entry, so
  keep it stable.

  What the catalogue reads from an msPart:
    | TEI                                   | Shown as                                            |
    | msContents/summary                    | the part's description (summary.feature)            |
    | msContents/textLang                   | the part's languages (textLang.feature)             |
    | msContents/msItem                     | items below the part (msItem.feature)               |
    | physDesc//supportDesc                 | the part's physical description (supportDesc.feature) |
    | physDesc/handDesc/handNote            | the part's Hand notes (handNote.feature)            |
    | history/origin                        | the part's place and date of production (origin.feature) |
  Not read from an msPart:
    - msIdentifier, msContents/colophon, msContents/incipit, msContents/explicit,
      msContents/locus, history/provenance, history/acquisition
    - scribes in the part's own handDesc (put them in msDesc/physDesc with a
      locus target instead; see handNote.feature)

  Background:
    Given I write a TEI file for manuscript "Greek_14" with msID "MS Greek 14"

  Scenario: Each msPart becomes a numbered entry below the manuscript
    Given the <msDesc> contains:
      """xml
      <msPart xml:id="Greek_14_A">
        <msContents>
          <msItem xml:id="Greek_14_A_1"/>
        </msContents>
      </msPart>
      <msPart xml:id="Greek_14_B"/>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's collection hierarchy is:
      """
      MS Greek 14
        MS Greek 14 part 1
          MS Greek 14 part 1 item 1
        MS Greek 14 part 2
      """

  Scenario: The n attribute is not used for the part number
    Given the <msDesc> contains:
      """xml
      <msPart n="B" xml:id="Greek_14_B"/>
      <msPart n="A" xml:id="Greek_14_A"/>
      """
    When the TEI file is published to the catalogue
    Then the catalogue entry for "Greek_14_B" has the title "MS Greek 14 part 1"
    And the catalogue entry for "Greek_14_A" has the title "MS Greek 14 part 2"

  Scenario: msParts are ignored if msContents has msItems
    Given the <msDesc> contains:
      """xml
      <msContents><msItem xml:id="Greek_14_1"/></msContents>
      <msPart xml:id="Greek_14_A"/>
      """
    When the TEI file is published to the catalogue
    Then there is no catalogue entry for "Greek_14_A"

  Scenario: An msPart without an xml:id is rejected
    Given the <msDesc> contains:
      """xml
      <msPart n="1"/>
      """
    When the TEI file is published to the catalogue
    Then the TEI file is rejected with "Could not find an id in node!"

  Scenario: Everything a part can have
    Given the <msDesc> contains:
      """xml
      <msPart xml:id="Greek_14_A">
        <msContents>
          <summary>A medical treatise</summary>
          <textLang mainLang="grc">Ancient Greek</textLang>
        </msContents>
        <physDesc>
          <objectDesc><supportDesc material="paper"/></objectDesc>
          <handDesc><handNote>A fourteenth-century Cypriot hand.</handNote></handDesc>
        </physDesc>
        <history>
          <origin>
            <origPlace><country>Cyprus</country></origPlace>
            <origDate calendar="Gregorian">14th century</origDate>
          </origin>
          <provenance>Owned by Henry Wellcome</provenance>
        </history>
      </msPart>
      """
    When the TEI file is published to the catalogue
    Then the catalogue entry for "Greek_14_A" has:
      | field                | value                             |
      | title                | MS Greek 14 part 1                |
      | description          | A medical treatise                |
      | language             | Greek, Ancient (to 1453)          |
      | physical description | Material: paper                   |
      | Hand note            | A fourteenth-century Cypriot hand. |
      | production           | Cyprus, 14th century              |
      | format               | Archives and manuscripts          |
    And the catalogue entry for "Greek_14_A" has no "Ownership note"
    And nothing from the part is shown on the manuscript's own entry

  Scenario: Fihrist manuscripts don't use msItems inside msParts
    Given the <publicationStmt> contains:
      """xml
      <idno type="catalogue">Fihrist</idno>
      """
    And the <msDesc> contains:
      """xml
      <msPart xml:id="Part_1">
        <msContents><msItem xml:id="Part_1_Item_1"/></msContents>
      </msPart>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's collection hierarchy is:
      """
      MS Greek 14
        MS Greek 14 part 1
      """

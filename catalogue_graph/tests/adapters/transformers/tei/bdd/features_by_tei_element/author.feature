Feature: <author> in msItem
  An <author> inside an <msItem> is shown as a contributor to that item, with
  the role "author". Authors are only read from msItems, so an <author>
  anywhere else (for example in titleStmt) is not shown.

  The key attribute links the author to an authority record:
    - VIAF (viaf.org) for most manuscripts
    - the Fihrist authority for manuscripts whose publicationStmt has
      <idno type="catalogue">Fihrist</idno>
  Without a key, the author is shown by name only. Authors with the same name
  on different manuscripts are then treated as the same person.

  Background:
    Given I write a TEI file for manuscript "MS_MSL_114"

  Scenario: An author name and key
    Given the <msContents> contains:
      """xml
      <msItem xml:id="MS_MSL_114_1">
        <author key="person_84812936">Paul of Aegina</author>
      </msItem>
      """
    When the TEI file is published to the catalogue
    Then the catalogue entry for "MS_MSL_114_1" shows the author "Paul of Aegina"
    And the author is linked to the VIAF record "person_84812936"

  Scenario: An author without a key
    Given the <msContents> contains:
      """xml
      <msItem xml:id="MS_MSL_114_1"><author>Paul of Aegina</author></msItem>
      """
    When the TEI file is published to the catalogue
    Then the catalogue entry for "MS_MSL_114_1" shows the author "Paul of Aegina"
    And the author is not linked to an authority record

  Scenario: The name can be in a persName, with the key on either element
    Given the <msContents> contains:
      """xml
      <msItem xml:id="Item_1">
        <author key="person_1"><persName>Avicenna, d. 980-1037</persName></author>
        <author><persName key="person_2">Rhazes</persName></author>
      </msItem>
      """
    When the TEI file is published to the catalogue
    Then the catalogue entry for "Item_1" shows the authors:
      | name                  | VIAF record |
      | Avicenna, d. 980-1037 | person_1    |
      | Rhazes                | person_2    |

  Scenario: With several persNames, the one with type="original" is shown
    Only that name is shown. The other forms of the name are not.
    Given the <msContents> contains:
      """xml
      <msItem xml:id="Item_1">
        <author key="person_97166546">
          <persName xml:lang="en">Avicenna, d. 980-1037</persName>
          <persName xml:lang="ar" type="original">ابو على الحسين ابن عبد الله ابن سينا</persName>
        </author>
      </msItem>
      """
    When the TEI file is published to the catalogue
    Then the catalogue entry for "Item_1" shows the author "ابو على الحسين ابن عبد الله ابن سينا"
    And "Avicenna, d. 980-1037" is not shown

  Scenario Outline: Several persNames must include exactly one type="original"
    Given the <msContents> contains:
      """xml
      <msItem xml:id="Item_1">
        <author>
          <persName <first>>Avicenna</persName>
          <persName <second>>ابن سينا</persName>
        </author>
      </msItem>
      """
    When the TEI file is published to the catalogue
    Then the TEI file is rejected with "<message>"
    Examples:
      | first           | second          | message                                    |
      | xml:lang="en"   | xml:lang="ar"   | No persName nodes with type=original       |
      | type="original" | type="original" | Multiple persName nodes with type=original |

  Scenario: An empty author is ignored
    Given the <msContents> contains:
      """xml
      <msItem xml:id="Item_1"><author key="person_1"/></msItem>
      """
    When the TEI file is published to the catalogue
    Then the catalogue entry for "Item_1" has no contributors

  Scenario: In a Fihrist manuscript, the key refers to the Fihrist authority
    Given the <publicationStmt> contains:
      """xml
      <idno type="catalogue">Fihrist</idno>
      """
    And the <msContents> contains:
      """xml
      <msItem xml:id="Item_1"><author key="person_97166546">Avicenna</author></msItem>
      """
    When the TEI file is published to the catalogue
    Then the author is linked to the Fihrist authority record "person_97166546"

  Scenario: Authors are listed before scribes
    Given the <msDesc> contains:
      """xml
      <msContents>
        <msItem xml:id="Item_1"><author key="person_1">Paul of Aegina</author></msItem>
      </msContents>
      <physDesc><handDesc>
        <handNote scribe="Scribe_A"><locus target="#Item_1">ff. 1-20</locus>Datu Poduwon</handNote>
      </handDesc></physDesc>
      """
    When the TEI file is published to the catalogue
    Then the catalogue entry for "Item_1" shows the contributors, in order:
      | name           | role   |
      | Paul of Aegina | author |
      | Datu Poduwon   | scribe |

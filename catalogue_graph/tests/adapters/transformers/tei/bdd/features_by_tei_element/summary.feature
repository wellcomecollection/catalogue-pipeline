Feature: <summary> in msContents
  The <summary> becomes the manuscript's description.

  Where it is read:
    - msDesc/msContents/summary   -> the manuscript's description
    - msPart/msContents/summary   -> that part's description
  A <summary> inside an <msItem>, or in <history>, is not used.

  You can split the summary into paragraphs with <p>. Any other markup inside
  the summary (persName, title, ref, hi, ...) is removed and only its text is
  kept, so links and formatting don't carry through.

  Background:
    Given I write a TEI file for manuscript "manuscript_15651"

  Scenario: A plain summary
    Given the <msContents> contains:
      """xml
      <summary>1 copy of al-Qānūn fī al-ṭibb by Avicenna, 980-1037</summary>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's description is "1 copy of al-Qānūn fī al-ṭibb by Avicenna, 980-1037"

  Scenario: Markup inside the summary is reduced to its text
    Given the <msContents> contains:
      """xml
      <summary>A treatise by <persName key="person_1">Avicenna</persName> on <term>medicine</term></summary>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's description is "A treatise by Avicenna on medicine"

  Scenario: Paragraphs are kept, but their attributes are dropped
    Given the <msContents> contains:
      """xml
      <summary><p rend="italic">First paragraph</p><p>Second <hi>paragraph</hi></p></summary>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's description is shown as two paragraphs: "First paragraph" and "Second paragraph"
    And the first paragraph is not shown in italics

  Scenario: Only one summary is allowed
    Given the <msContents> contains:
      """xml
      <summary>One</summary>
      <summary>Two</summary>
      """
    When the TEI file is published to the catalogue
    Then the TEI file is rejected with "More than one summary node!"

  Scenario: A summary in an msPart describes that part
    Given the <msDesc> contains:
      """xml
      <msPart xml:id="Part_1">
        <msContents><summary>The first part</summary></msContents>
      </msPart>
      """
    When the TEI file is published to the catalogue
    Then the catalogue entry for "Part_1" has the description "The first part"
    And the manuscript has no description

  Scenario: A summary in an msItem is not used
    Given the <msContents> contains:
      """xml
      <msItem xml:id="Item_1"><summary>About this item</summary></msItem>
      """
    When the TEI file is published to the catalogue
    Then the catalogue entry for "Item_1" has no description

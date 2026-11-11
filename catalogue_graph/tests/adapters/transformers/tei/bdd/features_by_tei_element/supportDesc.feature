Feature: <supportDesc> in physDesc
  The <supportDesc> becomes the physical description. It is read from
  msDesc/physDesc (the manuscript) and msPart/physDesc (that part). msItems
  have no physical description.

  The physical description is built from up to three pieces, in this order,
  separated by "; ":
    1. the <support> text, followed by "Watermarks: " and the text of any <watermark>s
    2. "Material: " followed by the material attribute on <supportDesc>
    3. the <extent> text, followed by the <dimensions>

  Other parts of physDesc (layoutDesc, decoDesc, bindingDesc, additions,
  foliation, condition, ...) are not used.

  Background:
    Given I write a TEI file for manuscript "MS_Arabic_1"

  Scenario: The material attribute
    Given the <physDesc> contains:
      """xml
      <objectDesc><supportDesc material="paper"/></objectDesc>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's physical description is "Material: paper"

  Scenario: <support>, with watermarks
    Markup inside the support and watermarks (such as <ref>) is reduced to its
    text. <measure> elements, such as chain-line distances, are left out.
    Given the <physDesc> contains:
      """xml
      <objectDesc><supportDesc>
        <support>Paper, folded in 2.
          <watermark>Similar to <ref>Mosin and Traljic 6947</ref> (saucisson).</watermark>
          <measure type="chainline">Chain distance 43 mm.</measure>
        </support>
      </supportDesc></objectDesc>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's physical description is "Paper, folded in 2.; Watermarks: Similar to Mosin and Traljic 6947 (saucisson)."

  Scenario: <extent> with height and width
    Width is shown before height, whatever order you write them in. The unit is
    added to each value, unless the value already ends with it.
    Given the <physDesc> contains:
      """xml
      <objectDesc><supportDesc>
        <extent>3 pages
          <dimensions unit="mm" type="leaf">
            <height>100mm</height>
            <width>300</width>
          </dimensions>
        </extent>
      </supportDesc></objectDesc>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's physical description is "3 pages; leaf dimensions: width 300 mm, height 100mm"

  Scenario: <extent> with <dim> elements
    Each dim is shown with its type, in the order written.
    Given the <physDesc> contains:
      """xml
      <objectDesc><supportDesc>
        <extent>
          <dimensions unit="cm">
            <dim type="width">35</dim>
            <dim type="length">35 cm</dim>
          </dimensions>
        </extent>
      </supportDesc></objectDesc>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's physical description is "dimensions: width 35 cm, length 35 cm"

  Scenario: Plain-text extent
    Given the <physDesc> contains:
      """xml
      <objectDesc><supportDesc><extent>3 pages and 5 leaves</extent></supportDesc></objectDesc>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's physical description is "3 pages and 5 leaves"

  Scenario: All three pieces together
    Given the <physDesc> contains:
      """xml
      <objectDesc><supportDesc material="chart">
        <support>Paper</support>
        <extent>3 pages<dimensions unit="mm" type="leaf"><height>100</height><width>300</width></dimensions></extent>
      </supportDesc></objectDesc>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's physical description is "Paper; Material: chart; 3 pages; leaf dimensions: width 300 mm, height 100 mm"

  Scenario: Only the first supportDesc with any content is used
    Given the <physDesc> contains:
      """xml
      <objectDesc>
        <supportDesc/>
        <supportDesc material="paper"/>
        <supportDesc material="vellum"/>
      </objectDesc>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's physical description is "Material: paper"

Feature: The <TEI> root element
  Each TEI file describes one manuscript, and becomes one catalogue entry.

  The xml:id on the root <TEI> element is the manuscript's permanent identifier
  in the catalogue. If you change it, the catalogue treats the file as a
  different manuscript.

  Every catalogue entry made from a TEI file has the format
  "Archives and manuscripts".

  Scenario: The xml:id identifies the manuscript
    Given I write a TEI file "manuscript_15651.xml" containing:
      """xml
      <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_15651">
        <teiHeader>
          <fileDesc>
            <publicationStmt><idno type="msID">MS Arabic 1</idno></publicationStmt>
            <sourceDesc><msDesc/></sourceDesc>
          </fileDesc>
        </teiHeader>
      </TEI>
      """
    When the TEI file is published to the catalogue
    Then the catalogue has an entry identified as "tei-manuscript-id" "manuscript_15651"
    And the entry's format is "Archives and manuscripts"

  Scenario: Spaces around the xml:id are ignored
    Given I write a TEI file whose root element has xml:id " manuscript_15651 "
    When the TEI file is published to the catalogue
    Then the catalogue has an entry identified as "tei-manuscript-id" "manuscript_15651"

  Scenario: A TEI file without an xml:id is rejected
    If a file is rejected, the catalogue is not updated: it keeps showing the
    last version that was accepted.
    Given I write a TEI file whose root element has no xml:id
    When the TEI file is published to the catalogue
    Then the TEI file is rejected

  Scenario: The xml:id must match the id the TEI repository knows the file by
    Given the TEI repository knows a file as "manuscript_15651"
    And that file's root element has xml:id "manuscript_99999"
    When the TEI file is published to the catalogue
    Then the TEI file is rejected

  Scenario: A TEI file that is not well-formed XML is rejected
    Given I write a TEI file that is missing a closing tag
    When the TEI file is published to the catalogue
    Then the TEI file is rejected

  Scenario: Deleting the TEI file removes the manuscript from the catalogue
    Given a TEI file "manuscript_15651" has been published to the catalogue
    When I delete the TEI file from the repository
    Then the catalogue entry "manuscript_15651" is removed, with the reason "Deleted by TEI source"

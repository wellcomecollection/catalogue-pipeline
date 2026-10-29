Feature: <titleStmt> and <publicationStmt> in fileDesc
  Two things in the fileDesc header matter to the catalogue:
    - <idno type="msID"> is the manuscript's reference number, and also its title
    - <idno type="catalogue"> says which printed catalogue the manuscript belongs to.
      If one of these is "Fihrist", the file is handled as an Arabic (Fihrist)
      manuscript.

  Nothing in <titleStmt> is used.

  Background:
    Given I write a TEI file for manuscript "manuscript_15651"

  Rule: <idno type="msID"> is required, and is the title and reference number

    Scenario: The reference number becomes the title
      Given the <publicationStmt> contains:
        """xml
        <idno>UkLW</idno>
        <idno type="msID">WMS_Arabic_404</idno>
        <idno type="catalogue">Fihrist</idno>
        """
      When the TEI file is published to the catalogue
      Then the manuscript's title is "WMS_Arabic_404"
      And the top of the manuscript's collection hierarchy is labelled "WMS_Arabic_404"

    Scenario: Extra spaces and line breaks in the reference number are removed
      Given the <publicationStmt> contains:
        """xml
        <idno type="msID">
          Wellcome   Malay 7
        </idno>
        """
      When the TEI file is published to the catalogue
      Then the manuscript's title is "Wellcome Malay 7"

    Scenario Outline: The file is rejected without exactly one non-empty msID
      Given the <publicationStmt> contains:
        """xml
        <idnos>
        """
      When the TEI file is published to the catalogue
      Then the TEI file is rejected with "<message>"
      Examples:
        | idnos                                                                | message                                       |
        | <idno>UkLW</idno>                                                    | No <idno type='msID'> found!                  |
        | <idno type="msID">  </idno>                                          | Empty <idno type='msID'> found!               |
        | <idno type="msID">WMS_Arabic_404</idno><idno type="msID">WMS_Arabic_405</idno> | Multiple instances of <idno type='msID'> found! |

    Scenario: The msID is found wherever it appears in the file
      In practice it should always be in publicationStmt. An msID anywhere else
      in the file also counts, and a second one will cause the file to be rejected.
      Given the <publicationStmt> contains:
        """xml
        <idno type="msID">WMS_Arabic_404</idno>
        """
      And the <msIdentifier> contains:
        """xml
        <idno type="msID">WMS_Arabic_404</idno>
        """
      When the TEI file is published to the catalogue
      Then the TEI file is rejected with "Multiple instances of <idno type='msID'> found!"

  Rule: <titleStmt> is not used

    Scenario: The titleStmt title does not appear in the catalogue
      On Arabic manuscripts the titleStmt title is just "Wellcome Library", and on
      other manuscripts it repeats the reference number.
      Given the <titleStmt> contains:
        """xml
        <title>Wellcome Library</title>
        """
      And the <publicationStmt> contains:
        """xml
        <idno type="msID">WMS_Arabic_404</idno>
        """
      When the TEI file is published to the catalogue
      Then the manuscript's title is "WMS_Arabic_404"

  Rule: <idno type="catalogue"> can mark a Fihrist manuscript

    Scenario Outline: Fihrist is recognised regardless of case or surrounding spaces
      Being in the Fihrist catalogue changes how <author key="..."> and nested
      <msItem> elements are read. See author.feature and msItem.feature.
      Given the <publicationStmt> contains:
        """xml
        <idno type="msID">WMS_Arabic_404</idno>
        <idno type="catalogue"><catalogue></idno>
        """
      When the TEI file is published to the catalogue
      Then the manuscript <is_or_is_not> handled as a Fihrist manuscript
      Examples:
        | catalogue                                    | is_or_is_not |
        | Fihrist                                      | is           |
        | FIHRIST                                      | is           |
        | The Hervey Malay Collection in the Wellcome Institute | is not |

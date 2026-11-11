Feature: <origin> in history
  The <origin> says where and when the manuscript was made. It is read from
  msDesc/history/origin (the manuscript) and msPart/history/origin (that part).

  From <origPlace>, only these are read:
    - <country>, <region>, <settlement>: shown together as the place,
      separated by ", " (any punctuation you put between them is not used)
    - <orgName>: shown as the organisation that produced it
  From <origDate>, only the one with calendar="Gregorian" (in any case) is
  read. Dates in other calendars are not shown. A <note> inside origDate is
  left out.

  The Gregorian date is also used to place the manuscript on the catalogue's
  date filter, so write it in a form the catalogue can recognise, e.g. "1732",
  "1732-1763", "18th century".

  Background:
    Given I write a TEI file for manuscript "Wellcome_Alpha_932"

  Scenario: A full origin
    Given the <history> contains:
      """xml
      <origin>
        <origPlace>
          <country>India</country>, <region>Gujarat</region>,
          <settlement>Ahmedabad</settlement>, <orgName>Jain monastery</orgName>
        </origPlace>
        <origDate calendar="Gregorian">1732</origDate>
      </origin>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's production is shown as "India, Gujarat, Ahmedabad, 1732"
    And the production place is "India, Gujarat, Ahmedabad"
    And the producing organisation is "Jain monastery"
    And the production date is "1732"

  Scenario: Only the Gregorian date is used
    Given the <history> contains:
      """xml
      <origin>
        <origDate calendar="Hijri-qamari">1145 AH</origDate>
        <origDate calendar="gregorian">1732</origDate>
      </origin>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's production is shown as "1732"
    And "1145 AH" is not shown

  Scenario: A date with no calendar attribute is not used
    Given the <history> contains:
      """xml
      <origin><origDate>1732</origDate></origin>
      """
    When the TEI file is published to the catalogue
    Then the manuscript has no production information

  Scenario: A note inside the date is left out
    Given the <history> contains:
      """xml
      <origin>
        <origDate calendar="Gregorian">ca.1732-63AD <note>from watermarks</note></origDate>
      </origin>
      """
    When the TEI file is published to the catalogue
    Then the production date is "ca.1732-63AD"

  Scenario: Other origPlace content is not used
    Given the <history> contains:
      """xml
      <origin>
        <origPlace>Probably made in <placeName>Cairo</placeName></origPlace>
      </origin>
      """
    When the TEI file is published to the catalogue
    Then the manuscript has no production information

  Scenario: An organisation alone is shown without a place or date
    Given the <history> contains:
      """xml
      <origin><origPlace><orgName>Jain monastery</orgName></origPlace></origin>
      """
    When the TEI file is published to the catalogue
    Then the producing organisation is "Jain monastery"
    And the manuscript's production is shown with an empty label

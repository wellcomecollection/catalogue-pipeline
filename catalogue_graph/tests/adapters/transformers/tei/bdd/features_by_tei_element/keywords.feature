Feature: <keywords> in profileDesc
  Every <term> inside <keywords> in profileDesc is shown as a subject of the
  manuscript. Subjects are only shown on the manuscript's own entry, not on
  its items or parts.

  The keywords scheme attribute says which subject vocabulary the terms come
  from (in any case):
    - scheme="#LCSH"  Library of Congress Subject Headings
    - scheme="#MeSH"  Medical Subject Headings
  For these schemes, give the term's identifier in key (preferred) or ref.
  The subject is then linked to that heading, so the manuscript appears with
  everything else in the catalogue with the same heading. A leading "subject_"
  and any spaces are removed from the identifier.

  If there is no recognised scheme, or no identifier, the subject is shown by
  its text only. It is then grouped with other subjects that have the same text.

  Background:
    Given I write a TEI file for manuscript "MS_Arabic_1"

  Scenario Outline: Linked subjects
    Given the <profileDesc> contains:
      """xml
      <textClass><keywords scheme="<scheme>"><list><item>
        <term <attribute>="<identifier>">Medicine, Arab</term>
      </item></list></keywords></textClass>
      """
    When the TEI file is published to the catalogue
    Then the manuscript has the subject "Medicine, Arab"
    And the subject is linked to <vocabulary> heading "<heading>"
    Examples:
      | scheme | attribute | identifier         | vocabulary | heading    |
      | #LCSH  | ref       | subject_sh85083116 | LCSH       | sh85083116 |
      | #lcsh  | key       | sh85083116         | LCSH       | sh85083116 |
      | #LCSH  | ref       | sh 85083116        | LCSH       | sh85083116 |
      | #MeSH  | ref       | subject_D008511    | MeSH       | D008511    |

  Scenario: key is used in preference to ref
    Given the <profileDesc> contains:
      """xml
      <textClass><keywords scheme="#LCSH">
        <term key="subject_sh85083116" ref="subject_sh00000000">Medicine, Arab</term>
      </keywords></textClass>
      """
    When the TEI file is published to the catalogue
    Then the subject is linked to LCSH heading "sh85083116"

  Scenario Outline: Unlinked subjects
    Given the <profileDesc> contains:
      """xml
      <textClass><keywords <scheme>>
        <term <identifier>>Medicine, Arab</term>
      </keywords></textClass>
      """
    When the TEI file is published to the catalogue
    Then the manuscript has the subject "Medicine, Arab"
    And the subject is not linked to any vocabulary
    Examples:
      | scheme         | identifier               |
      | scheme="#LCSH" |                          |
      | scheme="#AAT"  | ref="subject_300054241"  |
      |                | ref="subject_sh85083116" |

  Scenario: Line breaks in a term are collapsed, and empty terms are ignored
    Given the <profileDesc> contains:
      """xml
      <textClass><keywords scheme="#MeSH">
        <term ref="D008511">Medicine,
          Arabic</term>
        <term ref="D000001">   </term>
      </keywords></textClass>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's subjects are "Medicine, Arabic"

  Scenario: Subjects are shown in the order written
    Given the <profileDesc> contains:
      """xml
      <textClass>
        <keywords scheme="#LCSH"><term ref="subject_sh85083116">Medicine, Arab</term></keywords>
        <keywords scheme="#MeSH"><term ref="D008511">Medicine, Arabic</term></keywords>
      </textClass>
      """
    When the TEI file is published to the catalogue
    Then the manuscript's subjects are "Medicine, Arab", "Medicine, Arabic"

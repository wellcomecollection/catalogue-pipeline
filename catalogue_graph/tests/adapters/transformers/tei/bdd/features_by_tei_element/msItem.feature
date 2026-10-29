Feature: <msItem>
  Each <msItem> becomes its own catalogue entry, linked to the manuscript in a
  collection hierarchy. msItems inside msItems are nested further:

      WMS Example 1                    (the manuscript)
        ├── On Medicine                (msItem Item_1)
        │     └── Commentary           (msItem Item_1_1)
        └── WMS Example 1 item 2       (msItem Item_2, which has no title)

  Each msItem must have an xml:id. It identifies the item's catalogue entry, so
  keep it stable.

  What the catalogue reads from an msItem:
    - <title>                                  -> the item's title and alternative titles
    - <author>                                 -> the item's authors (see author.feature)
    - <textLang>                               -> the item's languages (see textLang.feature)
    - <locus>, <colophon>, <incipit>, <explicit> -> notes (see colophon_incipit_explicit_locus.feature)
    - scribes whose handNote locus targets the item (see handNote.feature)
    - nested <msItem>s
  Anything else in an msItem, such as summary, note, respStmt, bibl or filiation,
  is not used.

  Background:
    Given I write a TEI file for manuscript "WMS_Example" with msID "WMS Example 1"

  Rule: Every msItem becomes a catalogue entry

    Scenario: msItems, including nested ones, appear in a hierarchy below the manuscript
      Given the <msContents> contains:
        """xml
        <msItem xml:id="Item_1">
          <msItem xml:id="Item_1_1"/>
          <msItem xml:id="Item_1_2">
            <msItem xml:id="Item_1_2_1"/>
          </msItem>
        </msItem>
        <msItem xml:id="Item_2"/>
        """
      When the TEI file is published to the catalogue
      Then the manuscript's collection hierarchy is:
        """
        WMS Example 1
          Item_1
            Item_1_1
            Item_1_2
              Item_1_2_1
          Item_2
        """
      And every item has the format "Archives and manuscripts"

    Scenario: An msItem without an xml:id is rejected
      Given the <msContents> contains:
        """xml
        <msItem n="1"><title>On Medicine</title></msItem>
        """
      When the TEI file is published to the catalogue
      Then the TEI file is rejected with "Could not find an id in node!"

  Rule: Item titles

    Scenario: A single title is used, whatever its type
      Given the <msContents> contains:
        """xml
        <msItem xml:id="Item_1">
          <title xml:lang="ar-Latn-x-lc" key="work_3001" type="standard">Al-Qānūn fī al-ṭibb</title>
        </msItem>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Item_1" has the title "Al-Qānūn fī al-ṭibb"
      And it has no alternative titles

    Scenario: With several titles, mark one type="original" and the others become alternative titles
      type="original" can be in any case.
      Given the <msContents> contains:
        """xml
        <msItem xml:id="Item_1">
          <title type="standard">Al-Qānūn fī al-ṭibb</title>
          <title type="original">القانون في الطب</title>
          <title>The Canon of Medicine</title>
        </msItem>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Item_1" has the title "القانون في الطب"
      And it has the alternative titles "Al-Qānūn fī al-ṭibb", "The Canon of Medicine"

    Scenario Outline: An item with no usable title is numbered
      The number is the item's position among its siblings, counting from 1.
      If there are several titles but not exactly one type="original", the item
      is numbered, and every title that isn't type="original" is still kept as
      an alternative title.
      Given the <msContents> contains:
        """xml
        <msItem xml:id="Item_1"><title>On Medicine</title></msItem>
        <msItem xml:id="Item_2"><titles></msItem>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Item_2" has the title "WMS Example 1 item 2"
      And it has the alternative titles "<alternative_titles>"
      Examples:
        | titles                                                           | alternative_titles |
        |                                                                  |                    |
        | <title>Commentary</title><title type="standard">Sharh</title>    | Commentary, Sharh  |
        | <title type="original">A</title><title type="original">B</title> |                    |

    Scenario: A nested untitled item is numbered within its parent's title
      Given the <msContents> contains:
        """xml
        <msItem xml:id="Item_1">
          <title>On Medicine</title>
          <msItem xml:id="Item_1_1"/>
        </msItem>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Item_1_1" has the title "On Medicine item 1"

  Rule: msItem and msPart

    Scenario: When msContents has msItems, msParts are not used at all
      Use either top-level msItems or msParts in a file, not both.
      Given the <msDesc> contains:
        """xml
        <msContents><msItem xml:id="Item_1"/></msContents>
        <msPart xml:id="Part_1"/>
        """
      When the TEI file is published to the catalogue
      Then the manuscript's collection hierarchy is:
        """
        WMS Example 1
          Item_1
        """

  Rule: Fihrist manuscripts only use top-level msItems
    Arabic manuscripts in the Fihrist catalogue are catalogued in finer detail,
    and their msItems are not always separate works. For these, only the
    msItems directly inside msDesc/msContents get catalogue entries.

    Scenario: Nested msItems in a Fihrist manuscript are not used
      Given the <publicationStmt> contains:
        """xml
        <idno type="catalogue">Fihrist</idno>
        """
      And the <msContents> contains:
        """xml
        <msItem xml:id="Item_1">
          <msItem xml:id="Item_1_1"/>
        </msItem>
        """
      When the TEI file is published to the catalogue
      Then the manuscript's collection hierarchy is:
        """
        WMS Example 1
          Item_1
        """

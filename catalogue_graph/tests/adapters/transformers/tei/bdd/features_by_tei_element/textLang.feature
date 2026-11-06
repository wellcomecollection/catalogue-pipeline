Feature: <textLang>
  <textLang> says what language the text is in. Languages are used to filter
  catalogue search results, so they are matched to the standard (MARC) list of
  language names that the rest of the catalogue uses.

  Where it is read:
    - msDesc/msContents/textLang  -> the manuscript
    - msItem/textLang             -> that item
    - msPart/msContents/textLang  -> that part

  Write the IANA language code in mainLang and/or otherLangs, and the language
  name as the element's text. otherLangs can hold several codes separated by
  spaces. The codes can be in any case.

  Every code on a textLang is checked against the same text. So one textLang
  with several codes can only make the language whose name matches its text
  filterable. If you want each language to be filterable, give each its own
  textLang.

  The code AND the name together must be one of the recognised pairs below.
  If they are not, the language can't be used as a search filter. The name
  you wrote is still shown on the catalogue entry, as a "Language note".
  This is deliberate: a mismatch usually means a mistake in the TEI.

  Background:
    Given I write a TEI file for manuscript "manuscript_15651"

  Rule: Recognised code and name pairs become filterable languages

    Scenario Outline: Code and name pairs that match a standard language
      Given the <msContents> contains:
        """xml
        <textLang mainLang="<code>"><name></textLang>
        """
      When the TEI file is published to the catalogue
      Then the manuscript can be found by filtering on the language "<filter>"
      And the language is shown as "<filter>"
      Examples:
        | code  | name              | filter                   |
        | ar    | Arabic            | Arabic                   |
        | sa    | Sanskrit          | Sanskrit                 |
        | he    | Hebrew            | Hebrew                   |
        | ms    | Malay             | Malay                    |
        | en    | English           | English                  |
        | eng   | English           | English                  |
        | hi    | Hindi             | Hindi                    |
        | ja    | Japanese          | Japanese                 |
        | jv    | Javanese          | Javanese                 |
        | pra   | Prakrit languages | Prakrit languages        |
        | it    | Italian           | Italian                  |
        | ta    | Tamil             | Tamil                    |
        | jpr   | Judeo-Persian     | Judeo-Persian            |
        | la    | Latin             | Latin                    |
        | cop   | Coptic            | Coptic                   |
        | es    | Spanish           | Spanish                  |
        | spq   | Spanish           | Spanish                  |
        | es-es | Spanish Spain     | Spanish                  |
        | btk   | Batak             | Batak                    |
        | fa    | Persian           | Persian                  |
        | ji    | Yiddish           | Yiddish                  |
        | yi    | Yiddish           | Yiddish                  |
        | fr    | French            | French                   |
        | grc   | Ancient Greek     | Greek, Ancient (to 1453) |
        | grc   | Greek             | Greek, Ancient (to 1453) |
        | el    | Greek             | Greek, Modern (1453- )   |

    Scenario Outline: More specific names are shown, but filter under the broader language
      Given the <msContents> contains:
        """xml
        <textLang mainLang="<code>"><name></textLang>
        """
      When the TEI file is published to the catalogue
      Then the manuscript can be found by filtering on the language "<filter>"
      And the language is shown as "<shown>"
      Examples:
        | code | name               | filter   | shown             |
        | ar   | Arabic [Karshuni]  | Arabic   | Arabic [Karshuni] |
        | egy  | Ancient Egyptian   | Egyptian | Ancient Egyptian  |
        | egy  | Egyptian (Ancient) | Egyptian | Ancient Egyptian  |
        | btx  | Karo-Batak         | Batak    | Karo-Batak        |
        | bbc  | Toba-Batak         | Batak    | Toba-Batak        |
        | btk  | Toba-Batak         | Batak    | Toba-Batak        |
        | btd  | Batak Dairi        | Batak    | Batak Dairi       |
        | gu   | (Old) Gujarati     | Gujarati | (Old) Gujarati    |
        | ms   | Middle Malay       | Malay    | Middle Malay      |
        | pka  | Ardhamāgadhi Prakrit | Prakrit languages | Ardhamāgadhi Prakrit |
        | pka  | Ardhamāgadhī Prākrit | Prakrit languages | Ardhamāgadhī Prākrit |
        | itk  | Judeo-Italian      | Italian  | Judeo-Italian     |
        | jv   | Java               | Javanese | Java              |

    Scenario: otherLangs works the same way as mainLang
      Given the <msContents> contains:
        """xml
        <textLang otherLangs="he">Hebrew</textLang>
        """
      When the TEI file is published to the catalogue
      Then the manuscript can be found by filtering on the language "Hebrew"

    Scenario: Several textLangs give several languages, in the order written
      Given the <msContents> contains:
        """xml
        <textLang mainLang="ar">Arabic</textLang>
        <textLang otherLangs="fa">Persian</textLang>
        """
      When the TEI file is published to the catalogue
      Then the manuscript's languages are shown as "Arabic", "Persian"

  Rule: Anything else becomes a Language note

    Scenario Outline: Unrecognised pairs are shown as a note, not a language
      Given the <msContents> contains:
        """xml
        <textLang <attribute>><name></textLang>
        """
      When the TEI file is published to the catalogue
      Then the manuscript has no filterable languages
      And the manuscript has a "Language note" reading "<name>"
      Examples:
        | attribute     | name                              |
        | mainLang="ar" | Persian                           |
        | mainLang="fa" | Farsi                             |
        | mainLang="xx" | Unknown tongue                    |
        |               | Arabic, with some Persian glosses |

    Scenario: Language notes come first among the manuscript's notes
      Given the <msContents> contains:
        """xml
        <colophon>Copied in 1100 AH</colophon>
        <textLang>Arabic, with some Persian glosses</textLang>
        """
      When the TEI file is published to the catalogue
      Then the manuscript's notes are, in order:
        | note          | text                              |
        | Language note | Arabic, with some Persian glosses |
        | Colophon note | Copied in 1100 AH                 |

  Rule: Mistakes

    Scenario: An empty textLang is ignored
      Given the <msContents> contains:
        """xml
        <textLang mainLang="ar"></textLang>
        """
      When the TEI file is published to the catalogue
      Then the manuscript has no filterable languages
      And the manuscript has no notes

  Rule: A textLang can have several codes

    Scenario: A combined name matches none of the codes, so it becomes a Language note
      Given the <msContents> contains:
        """xml
        <textLang mainLang="ar" otherLangs="fa">Arabic and Persian</textLang>
        """
      When the TEI file is published to the catalogue
      Then the manuscript has no filterable languages
      And the manuscript has a "Language note" reading "Arabic and Persian"

    Scenario Outline: Only the code that matches the name becomes a language
      The other codes are ignored, and no Language note is made.
      Given the <msContents> contains:
        """xml
        <textLang <attributes>><name></textLang>
        """
      When the TEI file is published to the catalogue
      Then the manuscript's languages are shown as "<filter>"
      And the manuscript has no notes
      Examples:
        | attributes                    | name    | filter  |
        | mainLang="ar" otherLangs="fa" | Arabic  | Arabic  |
        | mainLang="ar" otherLangs="fa" | Persian | Persian |
        | otherLangs="he FA"            | Persian | Persian |

  Rule: Items and parts can inherit the manuscript's languages

    Scenario: If no item or part gives a language, they all show the manuscript's languages
      Given the <msContents> contains:
        """xml
        <textLang mainLang="ar">Arabic</textLang>
        <msItem xml:id="Item_1"/>
        <msItem xml:id="Item_2"/>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Item_1" has the language "Arabic"
      And the catalogue entry for "Item_2" has the language "Arabic"

    Scenario: If any item or part gives a language, none of the others inherit one
      This is all-or-nothing across every item and part in the file, at every
      level of nesting. If one item has a textLang, give the others one too.
      Given the <msContents> contains:
        """xml
        <textLang mainLang="ar">Arabic</textLang>
        <msItem xml:id="Item_1"><textLang mainLang="fa">Persian</textLang></msItem>
        <msItem xml:id="Item_2"/>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Item_1" has the language "Persian"
      And the catalogue entry for "Item_2" has no languages

    Scenario: A language note does not count as an item giving a language
      Given the <msContents> contains:
        """xml
        <textLang mainLang="ar">Arabic</textLang>
        <msItem xml:id="Item_1"><textLang>Mostly Arabic</textLang></msItem>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Item_1" has the language "Arabic"
      And the catalogue entry for "Item_1" has a "Language note" reading "Mostly Arabic"

Feature: <colophon>, <incipit>, <explicit> and <locus>
  These elements describe the text itself, and each becomes a note on the
  catalogue entry:

    | TEI element         | Note shown as |
    | <colophon>          | Colophon note |
    | <incipit>           | Begins        |
    | <explicit>          | Ends          |
    | <locus> (see below) | Locus         |

  Where they are read:
    - directly inside msDesc/msContents  -> the manuscript
    - directly inside an <msItem>        -> that item
  Inside an <msPart>, these elements are only read when they are direct
  children of <msPart>. They are NOT read from msPart/msContents, which is
  where the TEI schema puts them. So colophons, incipits and explicits written
  inside a part's msContents are not shown on the part, unless they are in
  an <msItem>.

  Line breaks and indentation inside these elements don't matter. They are
  collapsed into single spaces, so you can wrap long transcriptions freely.
  Elements that are empty or only contain whitespace are ignored.

  Background:
    Given I write a TEI file for manuscript "Wellcome_Alpha_932"

  Rule: <colophon>

    Scenario: A colophon is shown as a Colophon note
      A locus inside the colophon is kept as part of the text.
      Given the <msContents> contains:
        """xml
        <colophon><locus>F.44v </locus>iti śrīupadhyānaśrutādhyene cau̎tho
          uddeśo saṃpūrṇaṃ</colophon>
        """
      When the TEI file is published to the catalogue
      Then the manuscript has a "Colophon note" reading "F.44v iti śrīupadhyānaśrutādhyene cau̎tho uddeśo saṃpūrṇaṃ"

    Scenario: Each colophon is a separate note
      Given the <msContents> contains:
        """xml
        <colophon>First colophon</colophon>
        <colophon>Second colophon</colophon>
        """
      When the TEI file is published to the catalogue
      Then the manuscript has 2 "Colophon note" notes

  Rule: <incipit> and <explicit>

    Scenario: The locus is separated from the transcription with a colon
      Comments (such as <!-- transcript -->) are ignored.
      Given the <msContents> contains:
        """xml
        <msItem xml:id="Alpha_932_1">
          <incipit>
            <locus>F. 1v</locus> <!-- transcript -->
            oṃ namaḥ japāpuṣyena saṃkāśaṃ
          </incipit>
          <explicit>
            <locus>F. 3r</locus> <!-- transcript -->
            ||12|| navagrahastotraṃ saṃpūraṇaṃ
          </explicit>
        </msItem>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Alpha_932_1" has the notes, in order:
        | note   | text                                          |
        | Begins | F. 1v: oṃ namaḥ japāpuṣyena saṃkāśaṃ          |
        | Ends   | F. 3r: \|\|12\|\| navagrahastotraṃ saṃpūraṇaṃ |

    Scenario: An incipit without a locus
      Given the <msContents> contains:
        """xml
        <incipit>bismillāh al-raḥmān al-raḥīm</incipit>
        """
      When the TEI file is published to the catalogue
      Then the manuscript has a "Begins" note reading "bismillāh al-raḥmān al-raḥīm"

  Rule: <locus>

    Scenario: A locus directly inside msItem is shown as a Locus note
      This is usually used to say which pages the item runs across.
      A locus inside an incipit, explicit or colophon is part of that note instead.
      Given the <msContents> contains:
        """xml
        <msItem xml:id="Malay_7_Item_2">
          <locus>PP. 1-27.</locus>
          <incipit><locus>P. 1</locus> In the name of God</incipit>
        </msItem>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Malay_7_Item_2" has the notes, in order:
        | note   | text                     |
        | Locus  | PP. 1-27.                |
        | Begins | P. 1: In the name of God |

  Rule: Where these elements are read

    Scenario: Notes appear in a fixed order, whatever order you write them in
      Order: language notes, Locus, Colophon note, Begins and Ends (in the order
      written), Hand note, Ownership note, Acquisition note.
      Given the <msContents> contains:
        """xml
        <explicit>The end</explicit>
        <colophon>Copied in 1100 AH</colophon>
        <incipit>The beginning</incipit>
        <locus>ff. 1-20</locus>
        """
      When the TEI file is published to the catalogue
      Then the manuscript's notes are, in order:
        | note          | text              |
        | Locus         | ff. 1-20          |
        | Colophon note | Copied in 1100 AH |
        | Ends          | The end           |
        | Begins        | The beginning     |

    Scenario: These elements are not read from a part's msContents
      Given the <msDesc> contains:
        """xml
        <msPart xml:id="Part_1">
          <msContents>
            <colophon>Copied in 1100 AH</colophon>
            <incipit>The beginning</incipit>
          </msContents>
        </msPart>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Part_1" has no notes

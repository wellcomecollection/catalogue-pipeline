Feature: <handNote> in physDesc/handDesc
  A <handNote> can do two things:
    - name a scribe, who is shown as a contributor with the role "scribe"
    - describe the handwriting, which is shown as a "Hand note"

  A handNote names a scribe if it has a scribe attribute, or contains a
  <persName role="scr">.
    - With a persName role="scr", the persName is the scribe's name.
    - With only a scribe attribute, the handNote's text is the scribe's name.
  A handNote with a scribe attribute is never shown as a Hand note, even if
  the name is in a persName role="scr". To name the scribe AND describe the
  hand, leave out the scribe attribute and wrap just the name in
  <persName role="scr"> (see "A handNote that names the scribe in a description").

  Which catalogue entry a scribe appears on:
    - no <locus target>          -> the manuscript
    - <locus target="#a #b">     -> the msItems or msParts with those xml:ids
  Scribes are only read from msDesc/physDesc. A scribe named in an msPart's own
  physDesc is not shown anywhere.

  Hand notes are read from msDesc/physDesc (shown on the manuscript) and from
  msPart/physDesc (shown on that part).

  Background:
    Given I write a TEI file for manuscript "Batak_36801"

  Rule: Describing the hand

    Scenario: A handNote with no scribe is shown as a Hand note
      Given the <msDesc> contains:
        """xml
        <physDesc><handDesc>
          <handNote>Written in similar hand to the letters edited by Jac Janssen.</handNote>
        </handDesc></physDesc>
        """
      When the TEI file is published to the catalogue
      Then the manuscript has a "Hand note" reading "Written in similar hand to the letters edited by Jac Janssen."
      And the manuscript has no contributors

    Scenario: A handNote in an msPart is shown on that part
      Given the <msDesc> contains:
        """xml
        <msPart xml:id="Greek_14_A">
          <physDesc><handDesc>
            <handNote scope="sole">Identified as a fourteenth-century Cypriot hand.</handNote>
          </handDesc></physDesc>
        </msPart>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Greek_14_A" has a "Hand note" reading "Identified as a fourteenth-century Cypriot hand."
      And the manuscript has no notes

  Rule: Naming a scribe with persName role="scr"

    Scenario: A handNote that only names the scribe
      It is not also shown as a Hand note, because it would only repeat the name.
      Given the <msDesc> contains:
        """xml
        <physDesc><handDesc>
          <handNote scope="sole"><persName role="scr">Mahādeva Pāṇḍe</persName></handNote>
        </handDesc></physDesc>
        """
      When the TEI file is published to the catalogue
      Then the manuscript shows the contributor "Mahādeva Pāṇḍe" with the role "scribe"
      And the manuscript has no notes

    Scenario: A handNote that names the scribe in a description
      The whole text, including the scribe's name, is also shown as a Hand note.
      Given the <msDesc> contains:
        """xml
        <physDesc><handDesc>
          <handNote>In neat handwriting by <persName role="scr">Mahādeva Pāṇḍe</persName></handNote>
        </handDesc></physDesc>
        """
      When the TEI file is published to the catalogue
      Then the manuscript shows the contributor "Mahādeva Pāṇḍe" with the role "scribe"
      And the manuscript has a "Hand note" reading "In neat handwriting by Mahādeva Pāṇḍe"

    Scenario: With several scribe persNames, the one with type="original" is used
      Given the <msDesc> contains:
        """xml
        <physDesc><handDesc>
          <handNote>
            <persName role="scr">Ibn Bakhtishu</persName>
            <persName role="scr" type="original">ابن بختيشوع</persName>
          </handNote>
        </handDesc></physDesc>
        """
      When the TEI file is published to the catalogue
      Then the manuscript shows the contributor "ابن بختيشوع" with the role "scribe"
      And "Ibn Bakhtishu" is not shown as a contributor

    Scenario: Several scribe persNames without one type="original" are rejected
      Given the <msDesc> contains:
        """xml
        <physDesc><handDesc>
          <handNote>
            <persName role="scr">Ibn Bakhtishu</persName>
            <persName role="scr">Jibril</persName>
          </handNote>
        </handDesc></physDesc>
        """
      When the TEI file is published to the catalogue
      Then the TEI file is rejected with "No persName nodes with type=original"

    Scenario: persName without role="scr" does not name a scribe
      Given the <msDesc> contains:
        """xml
        <physDesc><handDesc>
          <handNote>Similar to the hand of <persName>Datu Poduwon</persName>.</handNote>
        </handDesc></physDesc>
        """
      When the TEI file is published to the catalogue
      Then the manuscript has no contributors
      And the manuscript has a "Hand note" reading "Similar to the hand of Datu Poduwon."

  Rule: Naming a scribe with the scribe attribute

    Scenario: The handNote text, without the locus, is the scribe's name
      The value of the scribe attribute itself ("Scribe_A") is not shown.
      Given the <msDesc> contains:
        """xml
        <physDesc><handDesc>
          <handNote scope="minor" scribe="Scribe_A">Datu Poduwon</handNote>
        </handDesc></physDesc>
        """
      When the TEI file is published to the catalogue
      Then the manuscript shows the contributor "Datu Poduwon" with the role "scribe"
      And the manuscript has no notes

    Scenario: A description in a scribe handNote becomes the scribe's "name"
      This is probably not what you want. Leave out the scribe attribute and wrap
      just the name in persName role="scr" instead.
      Given the <msDesc> contains:
        """xml
        <physDesc><handDesc>
          <handNote scope="minor" scribe="Scribe_B">Another hand, which uses the northern ta.</handNote>
        </handDesc></physDesc>
        """
      When the TEI file is published to the catalogue
      Then the manuscript shows the contributor "Another hand, which uses the northern ta." with the role "scribe"

    Scenario Outline: A scribe attribute with no name text is ignored
      Records exported from Calm often contain empty handNotes like this. They
      produce neither a scribe nor a Hand note.
      Given the <msDesc> contains:
        """xml
        <msContents><msItem xml:id="Item_1"/></msContents>
        <physDesc><handDesc>
          <empty_scribe_hand_note>
        </handDesc></physDesc>
        """
      When the TEI file is published to the catalogue
      Then the manuscript has no contributors
      And the catalogue entry for "Item_1" has no contributors
      And the manuscript has no notes
      Examples:
        | empty_scribe_hand_note                                                     |
        | <handNote scribe="sole"/>                                                  |
        | <handNote scribe="Scribe_A"><locus target="#Item_1">a 2-62</locus></handNote> |

  Rule: Assigning scribes to items and parts with <locus target>

    Scenario: A scribe is shown on each item listed in the locus target
      The target is a space-separated list of "#" followed by an xml:id.
      The locus text (the folio range) is not shown.
      Given the <msDesc> contains:
        """xml
        <msContents>
          <msItem xml:id="Batak_36801_1"/>
          <msItem xml:id="Batak_36801_2"/>
          <msItem xml:id="Batak_36801_3"/>
        </msContents>
        <physDesc><handDesc>
          <handNote scope="minor" scribe="Scribe_A">
            <locus target="#Batak_36801_1 #Batak_36801_2">a 2-62, b 2-7</locus>Datu Poduwon
          </handNote>
          <handNote scope="minor"><locus target="#Batak_36801_3">b 23-35</locus>
            <persName role="scr">Guru Mangaraja</persName>
          </handNote>
        </handDesc></physDesc>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Batak_36801_1" shows the contributor "Datu Poduwon" with the role "scribe"
      And the catalogue entry for "Batak_36801_2" shows the contributor "Datu Poduwon" with the role "scribe"
      And the catalogue entry for "Batak_36801_3" shows the contributor "Guru Mangaraja" with the role "scribe"
      And the manuscript has no contributors
      # Side effect: the second handNote has no scribe attribute and contains
      # text other than the persName (the locus), so it is also shown on the
      # manuscript as a Hand note.
      And the manuscript has a "Hand note" reading "b 23-35 Guru Mangaraja"

    Scenario: A scribe can be assigned to an msPart
      Given the <msDesc> contains:
        """xml
        <msPart xml:id="Part_A"/>
        <physDesc><handDesc>
          <handNote scribe="Scribe_A"><locus target="#Part_A">ff. 1-20</locus>Datu Poduwon</handNote>
        </handDesc></physDesc>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Part_A" shows the contributor "Datu Poduwon" with the role "scribe"

    Scenario: A scribe in an msPart's own physDesc is lost
      Put scribes in msDesc/physDesc and use <locus target> to point at the part.
      Given the <msDesc> contains:
        """xml
        <msPart xml:id="Part_A">
          <physDesc><handDesc>
            <handNote scribe="Scribe_A">Datu Poduwon</handNote>
            <handNote><persName role="scr">Guru Mangaraja</persName></handNote>
          </handDesc></physDesc>
        </msPart>
        """
      When the TEI file is published to the catalogue
      Then the catalogue entry for "Part_A" has no contributors
      And the catalogue entry for "Part_A" has no notes

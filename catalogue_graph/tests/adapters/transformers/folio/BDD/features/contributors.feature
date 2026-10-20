Feature: contributors (MARC 1xx and 7xx names)
  Contributors are built from the main entry (100, 110, 111) and added entry
  (700, 710, 711) personal, corporate and meeting names. Main entries are the
  primary contributors and come first. Otherwise document order is kept, and an
  added entry that repeats a main entry is dropped.

  These scenarios mirror the unit tests of the Scala Sierra transformer this
  replaces (SierraContributorsTest.scala) and, where marked, of the shared
  MarcContributorsTest.scala, including their test data, so the two can be
  compared directly. The scenarios at the end codify where the Python
  implementation deliberately diverges from Scala.

  https://www.loc.gov/marc/bibliographic/bd100.html
  https://www.loc.gov/marc/bibliographic/bd700.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  Scenario: A record with no name fields has no contributors
    When I transform the MARC record
    Then there are no contributors

  Scenario: Personal, corporate and meeting names produce Person, Organisation and Meeting contributors
    Given the MARC record has a 100 field with subfield "a" value "Sarah the soybean"
    And the MARC record has another 100 field with subfield "a" value "Sam the squash," and subfield "c" value "Sir"
    And the MARC record has a 110 field with subfield "a" value "Spinach Solicitors"
    And the MARC record has a 700 field with subfield "a" value "Sebastian the sugarsnap"
    And the MARC record has a 710 field with subfield "a" value "Shallot Swimmers"
    And the MARC record has a 711 field with subfield "a" value "Sammys meet the Sammys" and subfield "c" value "at Sammys"
    When I transform the MARC record
    Then the work has 6 contributors with agent label:
      | Sarah the soybean                |
      | Sam the squash, Sir              |
      | Spinach Solicitors               |
      | Sebastian the sugarsnap          |
      | Shallot Swimmers                 |
      | Sammys meet the Sammys at Sammys |
    And the work has 6 contributors with agent type:
      | Person       |
      | Person       |
      | Organisation |
      | Person       |
      | Organisation |
      | Meeting      |
    And the work has 6 contributors with agent identifier value:
      | sarah the soybean                |
      | sam the squash, sir              |
      | spinach solicitors               |
      | sebastian the sugarsnap          |
      | shallot swimmers                 |
      | sammys meet the sammys at sammys |
    And the 1st contributor is primary
    And the 2nd contributor is primary
    And the 4th contributor is not primary
    And the 5th contributor is not primary

  Scenario: A personal name label combines ǂa, ǂb, ǂc and ǂd
    Given the MARC record has a 100 field with subfields:
      | code | value             |
      | a    | Charles Emmanuel  |
      | b    | III,              |
      | c    | King of Sardinia, |
      | d    | 1701-1773         |
    And the MARC record has a 700 field with subfields:
      | code | value             |
      | a    | Charles Emmanuel  |
      | b    | IV,               |
      | c    | King of Sardinia, |
      | d    | 1796-1802         |
    When I transform the MARC record
    Then the 1st contributor has the agent label "Charles Emmanuel III, King of Sardinia, 1701-1773"
    And it has the agent type "Person"
    And it has 0 roles
    And it is primary
    And the 2nd contributor has the agent label "Charles Emmanuel IV, King of Sardinia, 1796-1802"
    And it has the agent type "Person"
    And it is not primary

  Scenario: A personal name with a title of a work produces an Agent, not a Person
    Based on Sierra b1159639
    Given the MARC record has a 700 field with subfield "a" value "Shakespeare, William," and subfield "d" value "1564-1616." and subfield "t" value "Hamlet."
    When I transform the MARC record
    Then the only contributor has the agent label "Shakespeare, William, 1564-1616. Hamlet."
    And it has the agent type "Agent"

  Scenario: Main entry personal names come first, then added entries in document order
    Given the MARC record has a 700 field with subfield "a" value "Alison the Apple"
    And the MARC record has a 100 field with subfield "a" value "Alfie the Artichoke"
    And the MARC record has another 700 field with subfield "a" value "Archie the Aubergine"
    When I transform the MARC record
    Then the work has 3 contributors with agent label:
      | Alfie the Artichoke  |
      | Alison the Apple     |
      | Archie the Aubergine |
    And the 1st contributor is primary
    And the 2nd contributor is not primary
    And the 3rd contributor is not primary

  Scenario: A personal name takes its roles from ǂe
    Given the MARC record has a 100 field with subfield "a" value "Violet the Vanilla" and subfield "e" value "spice" and subfield "e" value "flavour"
    When I transform the MARC record
    Then the only contributor has the agent label "Violet the Vanilla"
    And it has the agent type "Person"
    And it is primary
    And it has 2 roles
    And its 1st role has the label "spice"
    And its 2nd role has the label "flavour"

  Scenario: A personal name takes a role from ǂj
    Based on Sierra b1202594x
    Given the MARC record has a 700 field with subfield "a" value "Zurbarán, Francisco de," and subfield "d" value "1598-1664," and subfield "j" value "Follower of"
    When I transform the MARC record
    Then the only contributor has the agent label "Zurbarán, Francisco de, 1598-1664"
    And it has 1 roles
    And its 1st role has the label "Follower of"

  Scenario: A personal name takes roles from ǂe and ǂj in document order
    Given the MARC record has a 700 field with subfield "a" value "A made-up leader" and subfield "j" value "Follower of" and subfield "e" value "Disciple of"
    When I transform the MARC record
    Then the only contributor has the agent label "A made-up leader"
    And it has 2 roles
    And its 1st role has the label "Follower of"
    And its 2nd role has the label "Disciple of"

  Scenario: A personal name label includes the fuller form of the name from ǂq
    Based on Sierra b11941820
    Given the MARC record has a 700 field with subfields:
      | code | value               |
      | a    | Faujas-de-St.-Fond, |
      | c    | cit.                |
      | q    | (Barthélemey),      |
      | d    | 1741-1819           |
      | e    | dedicatee.          |
    When I transform the MARC record
    Then the only contributor has the agent label "Faujas-de-St.-Fond, cit. (Barthélemey), 1741-1819"
    And its 1st role has the label "dedicatee"

  Scenario: A personal name with a ǂ0 gets an LC Names identifier
    Given the MARC record has a 100 field with subfield "a" value "Ivan the ivy" and subfield "0" value "nlcsh7101607"
    When I transform the MARC record
    Then the only contributor has the agent label "Ivan the ivy"
    And it has the agent identifier type "lc-names"
    And it has the agent identifier ontology type "Person"
    And it has the agent identifier value "nlcsh7101607"

  Scenario: ǂ0 values that differ only in spacing and punctuation produce one identifier
    Based on Sierra b3017492
    Given the MARC record has a 100 field with subfield "a" value "Wanda the watercress" and subfield "0" value "nlcsh 2055034" and subfield "0" value "  nlcsh2055034 " and subfield "0" value " nlc sh 2055034" and subfield "0" value "nlcsh 2055034.,"
    When I transform the MARC record
    Then the only contributor has the agent label "Wanda the watercress"
    And it has the agent type "Person"
    And it has 0 roles
    And it has the agent identifier type "lc-names"
    And it has the agent identifier value "nlcsh2055034"

  Scenario: Two different ǂ0 values on a personal name produce a label-derived identifier
    Given the MARC record has a 100 field with subfield "a" value "Darren the Dill" and subfield "0" value "nlcsh9069541" and subfield "0" value "nlcsh3384149"
    When I transform the MARC record
    Then the only contributor has the agent label "Darren the Dill"
    And it has the agent type "Person"
    And it has the agent identifier type "label-derived"
    And it has the agent identifier value "darren the dill"

  Scenario: An LC Names URI in ǂ0 resolves to the bare identifier
    Based on Backstage authority-control output
    Given the MARC record has a 100 field with subfield "a" value "Brücke, Ernst Wilhelm von," and subfield "d" value "1819-1892" and subfield "0" value "http://id.loc.gov/authorities/names/n83211267"
    And the MARC record has a 700 field with subfield "a" value "Huxley, Aldous," and subfield "d" value "1894-1963" and subfield "0" value "https://id.loc.gov/authorities/names/n80057246"
    When I transform the MARC record
    Then the work has 2 contributors with agent identifier type:
      | lc-names |
      | lc-names |
    And the work has 2 contributors with agent identifier ontology type:
      | Person |
      | Person |
    And the work has 2 contributors with agent identifier value:
      | n83211267 |
      | n80057246 |

  Scenario: A ǂ0 that is not a valid Library of Congress identifier produces a label-derived identifier
    Given the MARC record has a 100 field with subfield "a" value "Gerald the garlic" and subfield "0" value "shsh85100861"
    When I transform the MARC record
    Then the only contributor has the agent label "Gerald the garlic"
    And it has the agent identifier type "label-derived"
    And it has the agent identifier value "gerald the garlic"

  Scenario: A trailing comma is trimmed from a personal name label
    Given the MARC record has a 100 field with subfield "a" value "George,"
    And the MARC record has a 700 field with subfield "a" value "Sebastian,"
    When I transform the MARC record
    Then the work has 2 contributors with agent label:
      | George    |
      | Sebastian |
    And the work has 2 contributors with agent type:
      | Person |
      | Person |
    And the 1st contributor is primary
    And the 2nd contributor is not primary

  Scenario: A corporate name produces an Organisation
    Given the MARC record has a 110 field with subfield "a" value "Ona the orache"
    When I transform the MARC record
    Then the only contributor has the agent label "Ona the orache"
    And it has the agent type "Organisation"

  Scenario: A corporate name label combines ǂa, ǂb, ǂc and ǂd in document order
    Based on Sierra b1000984
    Given the MARC record has a 110 field with subfields:
      | code | value                                                                               |
      | a    | IARC Working Group on the Evaluation of the Carcinogenic Risk of Chemicals to Man. |
      | b    | Meeting                                                                             |
      | d    | 1972 :                                                                              |
      | c    | Lyon, France                                                                        |
      | n    |   79125097                                                                          |
    When I transform the MARC record
    Then the only contributor has the agent label "IARC Working Group on the Evaluation of the Carcinogenic Risk of Chemicals to Man. Meeting 1972 : Lyon, France"
    And it has the agent type "Organisation"

  Scenario: Main entry corporate names come first, then added entries in document order
    Given the MARC record has a 710 field with subfield "a" value "Mike the mashua"
    And the MARC record has a 110 field with subfield "a" value "Mary the mallow"
    And the MARC record has another 710 field with subfield "a" value "Mickey the mozuku"
    When I transform the MARC record
    Then the work has 3 contributors with agent label:
      | Mary the mallow   |
      | Mike the mashua   |
      | Mickey the mozuku |
    And the 1st contributor is primary
    And the 2nd contributor is not primary
    And the 3rd contributor is not primary

  Scenario: A corporate name takes its roles from ǂe
    Given the MARC record has a 110 field with subfield "a" value "Terry the turmeric" and subfield "e" value "dye" and subfield "e" value "colouring"
    When I transform the MARC record
    Then the only contributor has the agent label "Terry the turmeric"
    And it has the agent type "Organisation"
    And it has 2 roles
    And its 1st role has the label "dye"
    And its 2nd role has the label "colouring"

  Scenario: A corporate name with a ǂ0 gets an LC Names identifier
    Given the MARC record has a 110 field with subfield "a" value "Gerry the Garlic" and subfield "0" value "nlcsh7212"
    When I transform the MARC record
    Then the only contributor has the agent label "Gerry the Garlic"
    And it has the agent type "Organisation"
    And it has 0 roles
    And it has the agent identifier type "lc-names"
    And it has the agent identifier ontology type "Organisation"
    And it has the agent identifier value "nlcsh7212"

  Scenario: ǂ0 values that differ only in spacing on a corporate name produce one identifier
    Given the MARC record has a 110 field with subfield "a" value "Charlie the chive" and subfield "0" value "nlcsh 6791210" and subfield "0" value "  nlcsh6791210 " and subfield "0" value " nlc sh 6791210"
    When I transform the MARC record
    Then the only contributor has the agent label "Charlie the chive"
    And it has the agent identifier type "lc-names"
    And it has the agent identifier value "nlcsh6791210"

  Scenario: Two different ǂ0 values on a corporate name produce a label-derived identifier
    Given the MARC record has a 110 field with subfield "a" value "Luke the lime" and subfield "0" value "nlcsh3349285" and subfield "0" value "nlcsh9059917"
    When I transform the MARC record
    Then the only contributor has the agent label "Luke the lime"
    And it has the agent type "Organisation"
    And it has the agent identifier type "label-derived"
    And it has the agent identifier value "luke the lime"

  Scenario: A trailing comma is trimmed from a corporate name label
    Given the MARC record has a 110 field with subfield "a" value "The organisation,"
    And the MARC record has a 710 field with subfield "a" value "Another organisation,"
    When I transform the MARC record
    Then the work has 2 contributors with agent label:
      | The organisation     |
      | Another organisation |
    And the 1st contributor is primary
    And the 2nd contributor is not primary

  Scenario: A name field with no name produces no contributor
    Based on Sierra b3069865, b3069866, b3069867 and b3069872
    Given the MARC record has a 100 field with subfield "e" value ""
    When I transform the MARC record
    Then there are no contributors

  Scenario: A main entry meeting name produces a Meeting
    Given the MARC record has a 111 field with subfield "a" value "Big meeting"
    When I transform the MARC record
    Then the only contributor has the agent label "Big meeting"
    And it has the agent type "Meeting"
    And it is primary

  Scenario: An added entry meeting name produces a Meeting that is not primary
    Given the MARC record has a 711 field with subfield "a" value "Big meeting"
    When I transform the MARC record
    Then the only contributor has the agent label "Big meeting"
    And it has the agent type "Meeting"
    And it is not primary

  Scenario: A meeting name label combines ǂa, ǂc, ǂd and ǂt
    Given the MARC record has a 111 field with subfields:
      | code | value    |
      | a    | 1        |
      | b    | not used |
      | c    | 2        |
      | d    | 3        |
      | t    | 4        |
    When I transform the MARC record
    Then the only contributor has the agent label "1 2 3 4"
    And it is primary

  Scenario: A meeting name takes its roles from ǂj
    Given the MARC record has a 111 field with subfields:
      | code | value      |
      | a    | label      |
      | e    | not a role |
      | j    | 1st role   |
      | j    | 2nd role   |
    When I transform the MARC record
    Then the only contributor has the agent label "label"
    And it has 2 roles
    And its 1st role has the label "1st role"
    And its 2nd role has the label "2nd role"

  Scenario: A meeting name with a ǂ0 gets an LC Names identifier
    Given the MARC record has a 111 field with subfield "a" value "label" and subfield "0" value "n456"
    When I transform the MARC record
    Then the only contributor has the agent label "label"
    And it has 0 roles
    And it has the agent identifier type "lc-names"
    And it has the agent identifier ontology type "Meeting"
    And it has the agent identifier value "n456"

  Scenario: An added entry that repeats the main entry is dropped
    Based on Sierra b24541758
    Given the MARC record has a 100 field with subfield "a" value "Steele, Richard," and subfield "c" value "Sir," and subfield "d" value "1672-1729."
    And the MARC record has a 700 field with subfield "a" value "Steele, Richard," and subfield "c" value "Sir," and subfield "d" value "1672-1729."
    When I transform the MARC record
    Then the only contributor has the agent label "Steele, Richard, Sir, 1672-1729."
    And it has the agent type "Person"
    And it has 0 roles
    And it is primary

  Scenario: A personal name label includes the part number from ǂn
    Based on Sierra b23042059
    Given the MARC record has a 700 field with subfield "a" value "Brewer, George." and subfield "t" value "Essays after the manner of Goldsmith," and subfield "n" value "No. 1-22."
    When I transform the MARC record
    Then the only contributor has the agent label "Brewer, George. Essays after the manner of Goldsmith, No. 1-22."
    And it is not primary

  Scenario: A personal name label includes the part name from ǂp
    Based on Sierra b14582855. The ǂ0 identifies Hippocrates alone
    Given the MARC record has a 700 field with subfields:
      | code | value                                   |
      | a    | Hippocrates.                            |
      | t    | Epistolae.                              |
      | p    | Ad Ptolemaeum regem de hominis fabrica. |
      | l    | Latin.                                  |
      | f    | 1561.                                   |
      | 0    | n  79005643                             |
    When I transform the MARC record
    Then the only contributor has the agent label "Hippocrates. Epistolae. Ad Ptolemaeum regem de hominis fabrica. Latin."
    And it has the agent type "Agent"
    And it has 0 roles
    And it is not primary
    And it has the agent identifier type "lc-names"
    And it has the agent identifier ontology type "Agent"
    And it has the agent identifier value "n79005643"

  Scenario: A trailing full stop is trimmed from a role
    Based on Sierra b28975005
    Given the MARC record has a 700 field with subfield "a" value "Nurse, Paul," and subfield "d" value "1949-" and subfield "e" value "writer of introduction."
    When I transform the MARC record
    Then the only contributor has the agent label "Nurse, Paul, 1949-"
    And it has 1 roles
    And its 1st role has the label "writer of introduction"

  # The scenarios below are ported from MarcContributorsTest.scala.

  Scenario: Name fields with only whitespace in their subfields produce no contributors
    Given the MARC record has a 110 field with no subfields
    And the MARC record has a 100 field with subfield "a" value "   " and subfield "b" value "" and subfield "c" value "  "
    When I transform the MARC record
    Then there are no contributors

  Scenario: Main entries of every type come first, then added entries in document order
    Given the MARC record has a 711 field with subfield "a" value "Xenophon's Symposium"
    And the MARC record has a 700 field with subfield "a" value "Anil Mendem"
    And the MARC record has a 100 field with subfield "a" value "Euripedes"
    And the MARC record has a 710 field with subfield "a" value "University of Inverdoon"
    And the MARC record has a 110 field with subfield "a" value "Felpersham University"
    And the MARC record has a 111 field with subfield "a" value "Council of Elrond"
    When I transform the MARC record
    Then the work has 6 contributors with agent label:
      | Euripedes               |
      | Felpersham University   |
      | Council of Elrond       |
      | Xenophon's Symposium    |
      | Anil Mendem             |
      | University of Inverdoon |
    And the 3rd contributor is primary
    And the 4th contributor is not primary

  Scenario Outline: The agent type and the role subfields are determined by the tag
    Given the MARC record has a <tag> field with subfield "a" value "Sæmundr fróði" and subfield "e" value "E" and subfield "j" value "J"
    When I transform the MARC record
    Then the only contributor has the agent type "<type>"
    And it has <roles> roles
    And its 1st role has the label "<first role>"
    And its <last> role has the label "<last role>"
    Examples:
      | tag | type         | roles | first role | last | last role |
      | 100 | Person       | 2     | E          | 2nd  | J         |
      | 700 | Person       | 2     | E          | 2nd  | J         |
      | 110 | Organisation | 1     | E          | 1st  | E         |
      | 710 | Organisation | 1     | E          | 1st  | E         |
      | 111 | Meeting      | 1     | J          | 1st  | J         |
      | 711 | Meeting      | 1     | J          | 1st  | J         |

  Scenario: Entries with the same name but a different identifier or role are not duplicates
    Only the two added entries identical to the main entry are dropped
    Given the MARC record has a 100 field with subfield "a" value "Max Bialystock" and subfield "e" value "producer"
    And the MARC record has a 700 field with subfield "a" value "Max Bialystock" and subfield "0" value "no2021024802" and subfield "e" value "producer"
    And the MARC record has another 700 field with subfield "a" value "Max Bialystock" and subfield "e" value "producer"
    And the MARC record has another 700 field with subfield "a" value "Max Bialystock" and subfield "e" value "director"
    And the MARC record has another 700 field with subfield "a" value "Max Bialystock"
    And the MARC record has another 700 field with subfield "a" value "Max Bialystock" and subfield "e" value "producer"
    When I transform the MARC record
    Then the work has 4 contributors with agent identifier type:
      | label-derived |
      | lc-names      |
      | label-derived |
      | label-derived |
    And the 1st contributor is primary
    And the 2nd contributor is not primary
    And the 3rd contributor has the agent label "Max Bialystock"
    And its 1st role has the label "director"
    And the 4th contributor has the agent label "Max Bialystock"
    And it has 0 roles

  Scenario: Identical main entries collapse into one, as do identical added entries
    Given the MARC record has a 100 field with subfield "a" value "Sæmundr fróði"
    And the MARC record has another 100 field with subfield "a" value "Sæmundr fróði"
    And the MARC record has a 700 field with subfield "a" value "Snorri Sturluson"
    And the MARC record has another 700 field with subfield "a" value "Snorri Sturluson"
    When I transform the MARC record
    Then the work has 2 contributors with agent label:
      | Sæmundr fróði    |
      | Snorri Sturluson |
    And the 1st contributor is primary
    And the 2nd contributor is not primary

  Scenario: Added entries that repeat a main entry with the same ǂ0 are dropped
    From MarcContributorsTest.scala
    Given the MARC record has a 100 field with subfield "a" value "Sæmundr fróði" and subfield "0" value "n97018003"
    And the MARC record has another 100 field with subfield "a" value "Snorri Sturluson" and subfield "0" value "n50000553"
    And the MARC record has a 700 field with subfield "a" value "Sæmundr fróði" and subfield "0" value "n97018003"
    And the MARC record has another 700 field with subfield "a" value "Snorri Sturluson" and subfield "0" value "n50000553"
    When I transform the MARC record
    Then the work has 2 contributors with agent label:
      | Sæmundr fróði    |
      | Snorri Sturluson |
    And the 1st contributor is primary
    And the 2nd contributor is primary

  Scenario: An Agent that shares its identifier with a Person becomes a Person
    From MarcContributorsTest.scala. The identifier shows the Agent and the Person
    are the same, so the Agent takes the more specific type
    Given the MARC record has a 100 field with subfield "a" value "Sæmundr fróði" and subfield "t" value "Codex Regius" and subfield "0" value "n97018003"
    And the MARC record has another 100 field with subfield "a" value "Snorri Sturluson" and subfield "t" value "Gylfaginning" and subfield "0" value "n50000553"
    And the MARC record has a 700 field with subfield "a" value "Sæmundr fróði" and subfield "0" value "n97018003"
    And the MARC record has another 700 field with subfield "a" value "Snorri Sturluson" and subfield "0" value "n50000553"
    When I transform the MARC record
    Then the work has 4 contributors with agent type:
      | Person |
      | Person |
      | Person |
      | Person |
    And the 1st contributor has the agent identifier ontology type "Person"

  # Everything below is a deliberate divergence from the Scala implementation.

  Scenario: An LC Names identifier on an analytical added entry keeps its scheme
    Based on Sierra b28536708. On an added entry, second indicator 2 marks an
    analytical entry. The Scala transformer reads it as a subject thesaurus
    and labels this identifier "nlm-mesh"
    Given the MARC record has a 700 field with indicators "1" "2" with subfield "a" value "Cicero, Marcus Tullius." and subfield "t" value "Cato maior de senectute." and subfield "0" value "n  85086880"
    When I transform the MARC record
    Then the only contributor has the agent label "Cicero, Marcus Tullius. Cato maior de senectute."
    And it has the agent type "Agent"
    And it has the agent identifier type "lc-names"
    And it has the agent identifier value "n85086880"

  Scenario: Whitespace around subfield values is stripped from labels, identifiers and roles
    Based on Sierra b20452482 and b33850021. The Scala transformer keeps the
    whitespace, so its labels can end in a space or hold two in a row
    Given the MARC record has a 100 field with subfield "a" value "Mavhunga, Clapperton Chak," and subfield "e" value "author. "
    And the MARC record has a 700 field with subfield "a" value "National Association for the Promotion of Social Science " and subfield "q" value "(Great Britain)"
    When I transform the MARC record
    Then the 1st contributor has the agent label "Mavhunga, Clapperton Chak"
    And its 1st role has the label "author"
    And the 2nd contributor has the agent label "National Association for the Promotion of Social Science (Great Britain)"
    And it has the agent identifier value "national association for the promotion of social science (great britain)"

  Scenario: Every trailing comma is trimmed from a name
    Based on Sierra b28673773 and b33038491. The Scala transformer trims one
    comma from an organisation or meeting and two from a person, so it keeps
    "Regional AIDS Group,"
    Given the MARC record has a 710 field with subfield "a" value "Regional AIDS Group,," and subfield "e" value "author," and subfield "e" value "publisher."
    And the MARC record has a 700 field with subfield "a" value "Oskenbay, Moldir, ," and subfield "e" value "author."
    When I transform the MARC record
    Then the work has 2 contributors with agent label:
      | Regional AIDS Group |
      | Oskenbay, Moldir    |
    And the work has 2 contributors with agent identifier value:
      | regional aids group,, |
      | oskenbay, moldir      |

  Scenario: A Subject Headings identifier on a name field is rejected
    Only an LC Names ǂ0 is accepted on a name field. The Scala transformer
    produces an "lc-subjects" identifier for this contributor
    Given the MARC record has a 700 field with subfield "a" value "Nationalism" and subfield "0" value "sh 85090150"
    When I transform the MARC record
    Then the only contributor has the agent label "Nationalism"
    And it has the agent identifier type "label-derived"
    And it has the agent identifier value "nationalism"

  Scenario: The second indicator of a name field does not affect its identifier
    The Scala transformer leaves this agent unidentified
    Given the MARC record has a 700 field with indicators "1" "1" with subfield "a" value "Caldwell, Charles" and subfield "0" value "n 81140510"
    When I transform the MARC record
    Then the only contributor has the agent label "Caldwell, Charles"
    And it has the agent identifier type "lc-names"
    And it has the agent identifier value "n81140510"

  Scenario: An empty ǂ0 beside a real one is ignored
    The Scala transformer produces a label-derived identifier for this contributor
    Given the MARC record has a 700 field with subfield "a" value "Caldwell, Charles" and subfield "0" value "" and subfield "0" value "n 81140510"
    When I transform the MARC record
    Then the only contributor has the agent label "Caldwell, Charles"
    And it has the agent identifier type "lc-names"
    And it has the agent identifier value "n81140510"

  Scenario: A Person and an Organisation with the same name collapse into one Person
    Their label-derived identifiers match, so the Organisation becomes a Person
    and the two collapse. Scala deduplicates before harmonising, so it keeps a
    primary and a non-primary copy
    Given the MARC record has a 100 field with subfield "a" value "Dora Milaje"
    And the MARC record has a 710 field with subfield "a" value "Dora Milaje"
    When I transform the MARC record
    Then the only contributor has the agent label "Dora Milaje"
    And it has the agent type "Person"
    And it is primary

  Scenario: A subject takes the more specific type of a contributor with the same identifier
    Based on Sierra b21916329. The Scala transformer applies the subject's type
    to the contributor instead, so it demotes this Person to a Concept
    Given the MARC record has a 700 field with subfield "a" value "Cuvierian Natural History Society."
    And the MARC record has a 650 field with indicators "" "0" with subfield "a" value "Cuvierian Natural History Society."
    When I transform the MARC record
    Then the only contributor has the agent label "Cuvierian Natural History Society."
    And it has the agent type "Person"
    And it has the agent identifier value "cuvierian natural history society"
    And the only subject has the label "Cuvierian Natural History Society"
    And it has the id.source_identifier.ontology_type "Person"
    And its only concept has the type "Person"

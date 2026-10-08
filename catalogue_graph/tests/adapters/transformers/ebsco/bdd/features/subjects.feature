Feature: Extracting subjects from 6xx fields
  The subject list is derived from the "Subject Added Entry" fields: 600, 610, 611, 648, 650, 651
  https://www.loc.gov/marc/bibliographic/bd6xx.html

  Background:
    Given a valid MARC record

  Rule: Accept but warn about multiple "a" subfields
    Scenario: A poorly formed genre
    "a" is a non-repeating field. However, if a field has more than one, accept it but log the anomaly.
    There are some records out of our control where multiple "a" has occurred
      Given the MARC record has a 600 field with indicators "" "0" with subfield "a" value "Joseph Pujol" and subfield "a" value "Roland"
      When I transform the MARC record
      Then an error "Repeated non-repeating subfield $a" is logged with tag "600"
      And the only subject has the label "Joseph Pujol Roland"

  Rule: A subject is extracted for each relevant field
    Scenario Outline: A single simple subject
      Given the MARC record has a <code> field with indicators "" "0" with subfield "a" value "A Subject"
      When I transform the MARC record
      Then the only subject has the label "A Subject"
      And it has 1 concept
      And its only concept has the type "<type>"
      And that concept has the label "A Subject"
      Examples:
        | code | type         |
        | 600  | Person       |
        | 610  | Organisation |
        | 611  | Meeting      |
        | 648  | Period       |
        | 650  | Concept      |
        | 651  | Place        |

  Rule: Compound subjects have an appropriately typed concept for each subdivision
    Scenario Outline: A subject with all the subdivisions
      Given the MARC record has a <code> field with indicators "" "0" with subfields:
        | code | value      |
        | a    | A Subject  |
        | v    | Specimens  |
        | x    | Literature |
        | y    | 1897-1900  |
        | z    | Dublin.    |
      When I transform the MARC record
      Then the only subject has the label "A Subject - Specimens - Literature - 1897-1900 - Dublin"
      And it has 5 concepts:
        | type    | label      | id.source_identifier.ontology_type |
        | <type>  | A Subject  | <type>                             |
        | Concept | Specimens  | Concept                            |
        | Concept | Literature | Concept                            |
        | Period  | 1897-1900  | Period                             |
        | Place   | Dublin     | Place                              |
      Examples:
        | code | type    |
        | 648  | Period  |
        | 650  | Concept |
        | 651  | Place   |

  Rule: Organisation, Person, and Meeting subjects all use different sets of subfields to make their labels and main concepts
    Scenario: A 600 field with all the subdivisions yields a Person
    A Person subject ignores v, y, and z.  Most of the other subdivisions form the subject label and the label of the main concept
    Subfield e is the role and does not result in a concept, nor is it part of the main concept
    Subfield x is the general subdivision, and is only included in the label of subject, and creates its own concept
    Person subjects join the subdivisions with a space, not with " - "
      Given the MARC record has a 600 field with indicators "" "2" with subfields:
        | code | value             |
        | a    | Joseph Pujol      |
        | b    | III               |
        | c    | Kt,               |
        | d    | 1857-1945         |
        | t    | O Sole Mio        |
        | p    | intro             |
        | n    | 1                 |
        | q    | Le Pétomane.      |
        | l    | French.           |
        | e    | Performer.        |
        | x    | Von Klinkerhoffen |
        | v    | Specimens         |
        | y    | 1897-1900         |
        | z    | Dublin.           |
      When I transform the MARC record
      Then the only subject has the label "Joseph Pujol III Kt, 1857-1945 O Sole Mio intro 1 Le Pétomane. French. Performer. Von Klinkerhoffen"
      And it has 2 concepts:
        | type    | label                                                                  |
        | Person  | Joseph Pujol III Kt, 1857-1945 O Sole Mio intro 1 Le Pétomane. French. |
        | Concept | Von Klinkerhoffen                                                      |

    Scenario: A 610 field with all the subdivisions yields an Organisation
    Regardless of the subdivisions, an Organisation is only one Concept
      Given the MARC record has a 610 field with indicators "" "0" with subfields:
        | code | value                       |
        | a    | Catholic Church.            |
        | b    | Diocese of Auxerre (France) |
        | c    | Cholet                      |
        | d    | 2025                        |
        | e    | Sponsor                     |
        | v    | Specimens                   |
        | z    | Bezonvaux.                  |
      When I transform the MARC record
      Then the only subject has the label "Catholic Church. Diocese of Auxerre (France) Cholet 2025 Sponsor"
      And it has 1 concept:
        | type         | label                                        |
        | Organisation | Catholic Church. Diocese of Auxerre (France) |

    Scenario: A 611 field with all the subdivisions yields a Meeting
    Regardless of the subdivisions, a Meeting is only one Concept
      Given the MARC record has a 611 field with indicators "" "0" with subfields:
        | code | value                 |
        | a    | Diet of Worms.        |
        | c    | Rhineland-Palatinate, |
        | d    | 1521                  |
        | e    | (Breakout Room 2)     |
        | v    | Specimens             |
        | z    | Bielefeld.            |
      When I transform the MARC record
      Then the only subject has the label "Diet of Worms. Rhineland-Palatinate, 1521"
      And it has 1 concept:
        | type    | label                                     |
        | Meeting | Diet of Worms. Rhineland-Palatinate, 1521 |


  Rule: When there are no valid Subject Added Entry fields, there are no subjects
    Scenario: No subjects
      When I transform the MARC record
      Then there are no subjects

    Scenario: An empty subject is invalid
      Given the MARC record has a 600 field with subfield "a" value ""
      When I transform the MARC record
      Then there are no subjects

  Rule: Not all 6xx fields create a Subject
  We catalogue using LoC (2nd indicator 0) and MeSH (2nd indicator 2) and other (2nd indicator 7).
  So for any of the Subject Added Entry fields we do not take any headings
  with 2nd indicators 1, 3-6, and there are particular rules on 2nd indicator 7 headings.

  We currently keep/use the following 650_7 ǂ2: local, homoit, indig, enslv

  See https://www.loc.gov/standards/sourcelist/subject.html for a list of
  subject sources.

  Consult the Collections Information Team for further information or when
  making changes.

    Scenario Outline: Ignored second indicators
      Given the MARC record has a 650 field with indicators "" "<ind2>" with subfield "a" value "<source>"
      When I transform the MARC record
      Then there are no subjects
      Examples:
        | ind2 | source                                                            |
        | 1    | Library of Congress Children's and Young Adults' Subject Headings |
        | 3    | National Agricultural Library subject authority file              |
        | 4    | Source not specified                                              |
        | 5    | Canadian Subject Headings                                         |
        | 6    | Répertoire de vedettes- matière                                   |

    Scenario Outline: Unconditional second indicators
      Given the MARC record has a 650 field with indicators "" "<ind2>" with subfield "a" value "<source>"
      When I transform the MARC record
      Then there is 1 subject
      Examples:
        | ind2 | source                               |
        | 0    | Library of Congress Subject Headings |
        | 2    | Medical Subject Headings             |

    Scenario Outline: Conditional second indicator
    Only a select few "source specified in subfield" sources are retained,
    all others are discarded
      Given the MARC record has a 650 field with indicators "" "7" with subfields:
        | code | value     |
        | a    | A Subject |
        | 2    | <source>  |
      When I transform the MARC record
      Then there are <n> subjects
      Examples:
        | n | source   |
        | 1 | local    |
        | 1 | homoit   |
        | 1 | indig    |
        | 1 | enslv    |
        | 0 | kadoc    |
        | 0 | galestne |

  Rule: Identical subjects are deduplicated
    Scenario: Two headings that produce the same subject
    Based on Sierra b2506728x: the second indicators say LCSH and MeSH, but without a ǂ0
    both headings yield the same label-derived subject, and only the first is kept
      Given the MARC record has a 650 field with indicators "" "0" with subfield "a" value "Medicine"
      And the MARC record has another 650 field with indicators "" "2" with subfield "a" value "Medicine."
      When I transform the MARC record
      Then the only subject has the label "Medicine"
      And that subject has the source identifier value "medicine"
      And that subject's only concept has the identifier value "medicine"

  Rule: A subject with a single concept shares its identifier with that concept
    Scenario: A Person subject with a role
    Based on Sierra b10769286: the role is part of the subject label but not of the concept label,
    and the concept still takes the subject's label-derived identifier
      Given the MARC record has a 600 field with indicators "" "0" with subfields:
        | code | value           |
        | a    | Stanton, Louisa, |
        | e    | defendant       |
      When I transform the MARC record
      Then the only subject has the label "Stanton, Louisa, defendant"
      And that subject has the source identifier value "stanton, louisa, defendant"
      And that subject's only concept has the label "Stanton, Louisa,"
      And that subject's only concept has the identifier value "stanton, louisa, defendant"

  Rule: Blank subfields are left out of the labels
    Scenario: A personal name with an empty subfield
    Based on Sierra b24000802. The previous implementation kept the blank subfield in the subject
    label, giving it a leading space, and only left it out of the concept label and identifier
      Given the MARC record has a 600 field with indicators "" "0" with subfields:
        | code | value        |
        | a    |              |
        | a    | Turner, John |
      When I transform the MARC record
      Then the only subject has the label "Turner, John"
      And that subject has the source identifier value "turner, john"
      And that subject's only concept has the label "Turner, John"

  Rule: Subjects are listed by heading type: concept headings, then personal, corporate and meeting names
    Scenario: Headings of several types in record order
    Based on Sierra b10629725
      Given the MARC record has a 600 field with indicators "" "0" with subfields:
        | code | value               |
        | a    | Le Tellier, Michel, |
        | d    | 1603-1685.          |
      And the MARC record has a 610 field with indicators "" "0" with subfield "a" value "Collège de Clermont (Paris, France)"
      And the MARC record has a 650 field with indicators "" "0" with subfield "a" value "Philosophy."
      And the MARC record has a 611 field with indicators "" "0" with subfield "a" value "Council of Trent"
      When I transform the MARC record
      Then the work has 4 subjects with label:
        | Philosophy                          |
        | Le Tellier, Michel, 1603-1685.      |
        | Collège de Clermont (Paris, France) |
        | Council of Trent                    |

  Rule: Each heading type trims its own trailing punctuation from the main concept
  Personal names are kept as catalogued, meeting names lose a trailing comma, corporate names lose
  a trailing comma and then a trailing full stop, and other headings lose a trailing full stop

    Scenario: A Person concept keeps its trailing comma
    Based on Sierra b10769286
      Given the MARC record has a 600 field with indicators "" "0" with subfields:
        | code | value          |
        | a    | Stanton, Henry, |
        | e    | plaintiff      |
        | 0    | nb2002074447   |
      When I transform the MARC record
      Then the only subject has the label "Stanton, Henry, plaintiff"
      And that subject's only concept has the label "Stanton, Henry,"

    Scenario: A Meeting concept keeps its trailing full stop
    Based on Sierra b10462909
      Given the MARC record has a 611 field with indicators "" "0" with subfields:
        | code | value                           |
        | a    | International Medical Congress. |
        | 0    | n  87106542                     |
      When I transform the MARC record
      Then the only subject has the label "International Medical Congress"
      And that subject's only concept has the label "International Medical Congress."

    Scenario: An Organisation concept loses a trailing comma and then a trailing full stop
    Based on Sierra b24973841
      Given the MARC record has a 610 field with indicators "" "0" with subfields:
        | code | value      |
        | a    | Cotes, E., |
        | e    | printer.   |
      When I transform the MARC record
      Then the only subject has the label "Cotes, E., printer"
      And that subject's only concept has the label "Cotes, E"

  Rule: In a subdivided concept heading the ǂ0 belongs to the subject, not to the primary concept
    Scenario: A MeSH heading with a qualifier
    Based on Sierra b1058478x: D014076Q000175 identifies "Tooth Diseases/diagnosis" as a whole,
    so the concept "Tooth Diseases" gets a label-derived identifier instead
      Given the MARC record has a 650 field with indicators "" "2" with subfields:
        | code | value          |
        | a    | Tooth Diseases |
        | x    | diagnosis      |
        | 0    | D014076Q000175 |
      When I transform the MARC record
      Then the only subject has the label "Tooth Diseases - diagnosis"
      And that subject has the source identifier value "D014076Q000175"
      And that subject has the source identifier type "nlm-mesh"
      And it has 2 concepts:
        | label          | id.source_identifier.value | id.source_identifier.identifier_type.id |
        | Tooth Diseases | tooth diseases             | label-derived                           |
        | diagnosis      | diagnosis                  | label-derived                           |

  Rule: In a subdivided personal name heading the ǂ0 belongs to the person
    Scenario: A Person subject with a general subdivision and an LC Names identifier
    Based on Sierra b10730217
      Given the MARC record has a 600 field with indicators "" "0" with subfields:
        | code | value                        |
        | a    | Woolf, Virginia,             |
        | d    | 1882-1941.                   |
        | t    | To the lighthouse            |
        | x    | Criticism and interpretation. |
        | 0    | n  79041870                  |
      When I transform the MARC record
      Then the only subject has the label "Woolf, Virginia, 1882-1941. To the lighthouse Criticism and interpretation."
      And that subject has the source identifier value "n79041870"
      And it has 2 concepts:
        | type    | label                                         | id.type        |
        | Person  | Woolf, Virginia, 1882-1941. To the lighthouse | Identifiable   |
        | Concept | Criticism and interpretation.                 | Unidentifiable |
      And that subject's 1st concept has the identifier value "n79041870"

  Rule: Trailing full stops are removed in Subjects, apart from Person subjects, and also in the concepts that make up a subject
  This is a bug in the previous implementation that we need to replicate for comparison purposes.
  Once the comparison is successful we should be able to remove the dot from a Person as well.

    Scenario: A Person Subject with a trailing dot
      Given the MARC record has a 600 field with indicators "" "0" with subfield "a" value "John II Comnenus, Emperor of the East, 1087 or 1088-1143."
      When I transform the MARC record
      Then the only subject has the label "John II Comnenus, Emperor of the East, 1087 or 1088-1143."

    Scenario Outline: A Subject with a trailing dot
      Given the MARC record has a <code> field with indicators "" "0" with subfield "a" value "Quirkafleeg."
      When I transform the MARC record
      Then the only subject has the label "Quirkafleeg"
      Scenarios:
        | code |
        | 610  |
        | 611  |
        | 648  |
        | 650  |
        | 651  |

    Scenario: A Subject with a trailing dot and whitespace
    Based on Sierra b11148810
      Given the MARC record has a 650 field with indicators "" "2" with subfield "a" value "Herniorrhaphy. "
      When I transform the MARC record
      Then the only subject has the label "Herniorrhaphy"
      And that subject's only concept has the label "Herniorrhaphy"

    Scenario: A Subject with trailing dots in all the concepts
      Given the MARC record has a 650 field with indicators "" "0" with subfields:
        | code | value       |
        | a    | A Subject.  |
        | v    | Specimens.  |
        | x    | Literature. |
        | z    | Dublin.     |
      When I transform the MARC record
      Then the only subject has the label "A Subject. - Specimens. - Literature. - Dublin"
      And it has 4 concepts:
        | label      |
        | A Subject  |
        | Specimens  |
        | Literature |
        | Dublin     |

    Scenario: A Person Subject with trailing dots in all the concepts
    As with the main label, person subjects also preserve the dots in their subdivision concepts

      Given the MARC record has a 600 field with indicators "" "0" with subfields:
        | code | value             |
        | a    | Slartibartfast.   |
        | e    | Fjord Specialist. |
        | x    | Literature.       |
      When I transform the MARC record
      Then the only subject has the label "Slartibartfast. Fjord Specialist. Literature."
      And it has 2 concepts:
        | label           |
        | Slartibartfast. |
        | Literature.     |

  Rule: Subdivisions of a Person Subject do not create identifiable concepts

    Scenario: A Subject with a general subdivision
      Given the MARC record has a 600 field with indicators "" "0" with subfields:
        | code | value              |
        | a    | Joseph Pujol       |
        | e    | Performer          |
        | x    | Artistic Flatulism |
      When I transform the MARC record
      Then the only subject has the label "Joseph Pujol Performer Artistic Flatulism"
      And it has 2 concepts:
        | label              | id.type        |
        | Joseph Pujol       | Identifiable   |
        | Artistic Flatulism | Unidentifiable |

  Rule: Chronological Subdivisions yield periods with ranges
  This is a rule with questionable value inherited from the previous implementation.
  I don't think there is anything that examines the actual date range of a period like this.
    Scenario Outline: A Subject with a Chronological Subdivision
      Given the MARC record has a <code> field with indicators "" "0" with subfields:
        | code | value         |
        | a    | Blackwork     |
        | y    | 17th century. |
      When I transform the MARC record
      Then the only subject has the label "Blackwork - 17th century"
      And that subject's 1st concept has the type "<type>"
      And that subject's 2nd concept has a range from 1600-01-01T00:00:00Z to 1699-12-31T23:59:59.999999999Z
      Examples:
        | code | type    |
        | 650  | Concept |
        | 651  | Place   |

  Rule: If a standard id in a scheme we recognise is provided, use it.
    Scenario Outline: A subject has a MeSH identifier
      Given the MARC record has a <code> field with indicators "" "2" with subfields:
        | code | value       |
        | a    | Quirkafleeg |
        | 0    | D003027     |
      When I transform the MARC record
      Then the only subject has the label "Quirkafleeg"
      And that subject's only concept has the type "<type>"
      And that subject's only concept has the identifier value "D003027"
      And that subject's only concept has the identifier type "nlm-mesh"
      Examples:
        | code | type         |
        | 600  | Person       |
        | 610  | Organisation |
        | 611  | Meeting      |
        | 648  | Period       |
        | 650  | Concept      |
        | 651  | Place        |

    Scenario: A subject has an LCSH identifier
      Given the MARC record has a 650 field with indicators "" "0" with subfields:
        | code | value      |
        | a    | Medicine   |
        | 0    | sh85083064 |
      When I transform the MARC record
      Then the only subject has the label "Medicine"
      And that subject has the source identifier value "sh85083064"
      And that subject has the source identifier type "lc-subjects"
      And that subject's only concept has the label "Medicine"
      And that subject's only concept has the identifier value "sh85083064"
      And that subject's only concept has the identifier type "lc-subjects"

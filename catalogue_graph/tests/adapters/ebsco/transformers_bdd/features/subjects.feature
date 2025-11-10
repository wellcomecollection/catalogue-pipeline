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
      Then an error "Repeated Non-repeating field $a found in 600 field" is logged
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

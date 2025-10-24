Feature: Extracting subjects from 6xx fields
  The subject list is derived from the "Subject Added Entry" fields: 600, 610, 611, 648, 650, 651
  https://www.loc.gov/marc/bibliographic/bd6xx.html

  Background:
    Given a valid MARC record

  Rule: Accept but warn about multiple "a" subfields
    Scenario: A poorly formed genre
    "a" is a non-repeating field. However, if a field has more than one, accept it but log the anomaly.
    There are some records out of our control where multiple "a" has occurred
      Given the MARC record has a 600 field with subfield "a" value "Joseph Pujol" and subfield "a" value "Roland"
      When I transform the MARC record
      Then an error "Repeated Non-repeating field $a found in 600 field" is logged
      And the only subject has the label "Joseph Pujol Roland"

  Rule: A subject is extracted for each relevant field
    Scenario Outline: A single simple subject
      Given the MARC record has a <code> field with subfield "a" value "A Subject"
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
      Given the MARC record has a <code> field with subfields:
        | code | value      |
        | a    | A Subject  |
        | v    | Specimens  |
        | x    | Literature |
        | y    | 1897-1900  |
        | z    | Dublin.    |
      When I transform the MARC record
      Then the only subject has the label "A Subject Specimens Literature 1897-1900 Dublin."
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
      Given the MARC record has a 600 field with subfields:
        | code | value              |
        | a    | Joseph Pujol       |
        | b    | III                |
        | c    | Kt,                |
        | d    | 1857-1945          |
        | t    | O Sole Mio         |
        | p    | intro              |
        | n    | 1                  |
        | q    | Le Pétomane.       |
        | l    | French.            |
        | e    | Performer.         |
        | x    | Von Klinkerhoffen. |
        | v    | Specimens          |
        | y    | 1897-1900          |
        | z    | Dublin.            |
      When I transform the MARC record
      Then the only subject has the label "Joseph Pujol III Kt, 1857-1945 O Sole Mio intro 1 Le Pétomane. French. Performer. Von Klinkerhoffen."
      And it has 2 concepts:
        | type    | label                                                                  |
        | Person  | Joseph Pujol III Kt, 1857-1945 O Sole Mio intro 1 Le Pétomane. French. |
        | Concept | Von Klinkerhoffen                                                      |

    Scenario: A 610 field with all the subdivisions yields an Organisation
    Regardless of the subdivisions, an Organisation is only one Concept
      Given the MARC record has a 610 field with subfields:
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
      Given the MARC record has a 611 field with subfields:
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

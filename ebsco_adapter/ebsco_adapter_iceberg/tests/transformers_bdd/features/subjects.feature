Feature: Extracting subjects from 6xx fields
  The subject list is derived from the "Subject Added Entry" fields: 600, 610, 611, 648, 650, 651
  https://www.loc.gov/marc/bibliographic/bd6xx.html

  Background:
    Given a valid MARC record

  Rule: When there are no valid Subject Added Entry fields, there are no subjects
    Scenario: No subjects
      When I transform the MARC record
      Then there are no subjects

  Rule: Accept but warn about multiple "a" subfields
    Scenario: A poorly formed genre
    "a" is a non-repeating field. if there is more than one, accept it but log the anomaly
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
        | 648  | Concept      |
        | 650  | Concept      |
        | 651  | Concept      |

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
        | type    | label      |
        | <type>  | A Subject  |
        | Concept | Specimens  |
        | Concept | Literature |
        | Period  | 1897-1900  |
        | Place   | Dublin     |
      Examples:
        | code | type         |
        | 600  | Person       |
        | 610  | Organisation |
        | 611  | Meeting      |
        | 648  | Concept      |
        | 650  | Concept      |
        | 651  | Concept      |


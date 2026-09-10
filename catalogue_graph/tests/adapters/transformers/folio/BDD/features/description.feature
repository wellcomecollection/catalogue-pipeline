Feature: description (MARC 520)
  Ported from the Scala MarcDescription transformer, keeping the test data of
  its unit tests (MarcDescriptionTest.scala) so the two can be compared
  directly. Deliberate divergences are grouped at the end.

  https://www.loc.gov/marc/bibliographic/bd520.html

  Background:
    Given a MARC record with field 001 "abc123"
    And the MARC record has a 999 field with indicators "f" "f" with subfield "i" value "10000000-0000-0000-0000-000000000001"
    And the MARC record has a 245 field with subfield "a" value "Some Title"

  # Ported from MarcDescriptionTest.scala.

  Scenario: A record with no 520 field has no description
    When I transform the MARC record
    Then the work's description is absent

  Scenario: A 520 with none of ǂa, ǂb, ǂc or ǂu has no description
    Given the MARC record has a 520 field with subfield "7" value "not me!"
    When I transform the MARC record
    Then the work's description is absent

  Scenario: A description is built from ǂa on its own
    Given the MARC record has a 520 field with subfield "a" value "Descriptions..are definitions of a more lax and fanciful kind."
    When I transform the MARC record
    Then the work's description is "<p>Descriptions..are definitions of a more lax and fanciful kind.</p>"

  Scenario: Subfields ǂa, ǂb, ǂc and ǂu are concatenated
    Given the MARC record has a 520 field with subfield "a" value "As there is a fine name, now-a-days, for every thing," and subfield "b" value "I suppose that ‘Hygeist’ is the polite description of quack doctor." and subfield "c" value "London Magazine, 1826" and subfield "u" value "http://example.com/"
    When I transform the MARC record
    Then the work's description is "<p>As there is a fine name, now-a-days, for every thing, I suppose that ‘Hygeist’ is the polite description of quack doctor. London Magazine, 1826 <a href="http://example.com/">http://example.com/</a></p>"

  Scenario: Repeated ǂu subfields are all linked
    Given the MARC record has a 520 field with subfield "a" value "For her owne person, It beggerd all discription." and subfield "u" value "http://example.com/6347939" and subfield "u" value "http://example.com/5877688"
    When I transform the MARC record
    Then the work's description is "<p>For her owne person, It beggerd all discription. <a href="http://example.com/6347939">http://example.com/6347939</a> <a href="http://example.com/5877688">http://example.com/5877688</a></p>"

  Scenario: Whitespace surrounding the description is trimmed
    Given the MARC record has a 520 field with subfield "a" value "	   Shapen in maner of a lop-webbe aftur the olde descripcioun.   "
    When I transform the MARC record
    Then the work's description is "<p>Shapen in maner of a lop-webbe aftur the olde descripcioun.</p>"

  Scenario: Each 520 field becomes its own paragraph
    Given the MARC record has a 520 field with subfield "a" value "This place perfectly answers the Description of the Elysian fields"
    And the MARC record has another 520 field with subfield "a" value "Wherein also we have interspersed, under the several heads of villains, such descriptions and cautions as may better serve to promote this good end."
    When I transform the MARC record
    Then the work's description is:
      """
      <p>This place perfectly answers the Description of the Elysian fields</p>
      <p>Wherein also we have interspersed, under the several heads of villains, such descriptions and cautions as may better serve to promote this good end.</p>
      """

  # Not one of the Scala unit tests, but both implementations leave a non-URL ǂu
  # as plain text. They disagree on what counts as one: Scala takes any scheme
  # the JVM handles (ftp, file, jar, mailto), where Python only takes http(s).
  # No work is affected: every 520 ǂu in FOLIO is http(s), except for three bare
  # hostnames that neither implementation accepts as a URL.

  Scenario: A ǂu that does not look like a URL is left as plain text
    Given the MARC record has a 520 field with subfield "a" value "For her owne person," and subfield "u" value "not a url"
    When I transform the MARC record
    Then the work's description is "<p>For her owne person, not a url</p>"

  # Everything below is a deliberate divergence from the Scala implementation.

  # The Scala sorts ǂa, ǂb, ǂc into that order. No 520 field in FOLIO or Sierra
  # has them out of order, so sorting only added complexity.

  Scenario: ǂa, ǂb and ǂc keep the order they appear in, and ǂu comes last
    Given the MARC record has a 520 field with subfield "u" value "http://example.com/" and subfield "b" value "I suppose that ‘Hygeist’ is the polite description of quack doctor." and subfield "a" value "As there is a fine name, now-a-days, for every thing," and subfield "c" value "London Magazine, 1826"
    When I transform the MARC record
    Then the work's description is "<p>I suppose that ‘Hygeist’ is the polite description of quack doctor. As there is a fine name, now-a-days, for every thing, London Magazine, 1826 <a href="http://example.com/">http://example.com/</a></p>"

  # The Scala throws, making the whole work invisible. The Python pipeline logs
  # an error and keeps the first occurrence, as it does for other non-repeatable
  # fields. One FOLIO record (Sierra b28676178) needs this.

  Scenario Outline: A 520 repeating a non-repeatable subfield uses the first occurrence
    Given the MARC record has a 520 field with subfield "<code>" value "Cyntaf" and subfield "<code>" value "Ail"
    When I transform the MARC record
    Then the work's description is "<p>Cyntaf</p>"
    And an error "Multiple instances of non-repeatable subfield in field 520" is logged with subfield "<code>"

    Examples:
      | code |
      | a    |
      | b    |
      | c    |

  # The Scala implementation emits an empty paragraph in the first two cases
  # and a stray blank separator in the third.

  Scenario: A 520 whose subfields are blank contributes no empty paragraph
    Given the MARC record has a 520 field with subfield "a" value "   "
    When I transform the MARC record
    Then the work's description is absent

  Scenario: A 520 with multiple blank subfields contributes no empty paragraph
    Given the MARC record has a 520 field with subfield "a" value "   " and subfield "b" value "   " and subfield "u" value "   "
    When I transform the MARC record
    Then the work's description is absent

  Scenario: An empty 520 between two populated ones adds no blank separator
    Given the MARC record has a 520 field with subfield "a" value "One"
    And the MARC record has another 520 field with subfield "7" value "not me!"
    And the MARC record has another 520 field with subfield "a" value "Two"
    When I transform the MARC record
    Then the work's description is:
      """
      <p>One</p>
      <p>Two</p>
      """

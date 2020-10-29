package uk.ac.wellcome.platform.transformer.calm.periods

import org.scalatest.Inspectors
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class LexerTest extends AnyFunSpec with Matchers with Inspectors {

  val validExamples: Seq[(String, Seq[PeriodToken])] = Seq(
    ("1900s", Seq(CENTURY(20))),
    ("1800s-1900s", Seq(CENTURY(19), RANGESEPARATOR, CENTURY(20))),
    ("1910s", Seq(DECADE(1910))),
    ("1910s-1920s", Seq(DECADE(1910), RANGESEPARATOR, DECADE(1920))),
    (
      "jan-may 1999",
      Seq(MONTH("jan"), RANGESEPARATOR, MONTH("may"), YEAR(1999))),
    ("april 1456", Seq(MONTH("apr"), YEAR(1456))),
    (
      "january 1256-february 2002",
      Seq(MONTH("jan"), YEAR(1256), RANGESEPARATOR, MONTH("feb"), YEAR(2002))),
    (
      "jan 1689-23 december 2001",
      Seq(
        MONTH("jan"),
        YEAR(1689),
        RANGESEPARATOR,
        NUMBER(23),
        MONTH("dec"),
        YEAR(2001))),
    (
      "jan 1689-23rd december 2001",
      Seq(
        MONTH("jan"),
        YEAR(1689),
        RANGESEPARATOR,
        ORDINAL(23),
        MONTH("dec"),
        YEAR(2001))),
    ("late 13th century", Seq(QUALIFIER.LATE, CENTURY(13))),
    ("12/6/1278", Seq(NUMBER(12), SLASH, NUMBER(6), SLASH, YEAR(1278))),
    (
      "13/01/1245-23/08/1678",
      Seq(
        NUMBER(13),
        SLASH,
        NUMBER(1),
        SLASH,
        YEAR(1245),
        RANGESEPARATOR,
        NUMBER(23),
        SLASH,
        NUMBER(8),
        SLASH,
        YEAR(1678))),
    (
      "12 dec-16 dec 1435",
      Seq(
        NUMBER(12),
        MONTH("dec"),
        RANGESEPARATOR,
        NUMBER(16),
        MONTH("dec"),
        YEAR(1435))),
    ("15 jul 1678", Seq(NUMBER(15), MONTH("jul"), YEAR(1678))),
    (
      "13 jun-15 december 1778",
      Seq(
        NUMBER(13),
        MONTH("jun"),
        RANGESEPARATOR,
        NUMBER(15),
        MONTH("dec"),
        YEAR(1778))),
    ("13 aug 1787", Seq(NUMBER(13), MONTH("aug"), YEAR(1787))),
    (
      "14 sep 1357-jan 1367",
      Seq(
        NUMBER(14),
        MONTH("sep"),
        YEAR(1357),
        RANGESEPARATOR,
        MONTH("jan"),
        YEAR(1367))),
    (
      "23 apr 1278-28 feb 1456",
      Seq(
        NUMBER(23),
        MONTH("apr"),
        YEAR(1278),
        RANGESEPARATOR,
        NUMBER(28),
        MONTH("feb"),
        YEAR(1456))),
    (
      "23 jan 1300-1301",
      Seq(NUMBER(23), MONTH("jan"), YEAR(1300), RANGESEPARATOR, YEAR(1301))),
    ("12-13 century", Seq(NUMBER(12), RANGESEPARATOR, CENTURY(13))),
    (
      "23-27 jan 1987",
      Seq(NUMBER(23), RANGESEPARATOR, NUMBER(27), MONTH("jan"), YEAR(1987))),
    ("1974 nov 30", Seq(YEAR(1974), MONTH("nov"), NUMBER(30))),
    ("early 12th century", Seq(QUALIFIER.EARLY, CENTURY(12))),
    (
      "12th century-mid 20th century",
      Seq(CENTURY(12), RANGESEPARATOR, QUALIFIER.MID, CENTURY(20))),
    (
      "29th oct-30th oct 2002",
      Seq(
        ORDINAL(29),
        MONTH("oct"),
        RANGESEPARATOR,
        ORDINAL(30),
        MONTH("oct"),
        YEAR(2002))),
    (
      "10th dec 2002 a.d.",
      Seq(ORDINAL(10), MONTH("dec"), YEAR(2002), QUALIFIER.ERA("a.d."))),
    (
      "11th jan 1899-12 dec 1999",
      Seq(
        ORDINAL(11),
        MONTH("jan"),
        YEAR(1899),
        RANGESEPARATOR,
        NUMBER(12),
        MONTH("dec"),
        YEAR(1999))),
    (
      "23rd dec 1233-23rd mar 1733",
      Seq(
        ORDINAL(23),
        MONTH("dec"),
        YEAR(1233),
        RANGESEPARATOR,
        ORDINAL(23),
        MONTH("mar"),
        YEAR(1733))),
    (
      "13th jul 1456-1789",
      Seq(ORDINAL(13), MONTH("jul"), YEAR(1456), RANGESEPARATOR, YEAR(1789))),
    ("23rd 12 1899", Seq(ORDINAL(23), NUMBER(12), YEAR(1899))),
    (
      "23rd 12 1899-13th 2 1999",
      Seq(
        ORDINAL(23),
        NUMBER(12),
        YEAR(1899),
        RANGESEPARATOR,
        ORDINAL(13),
        NUMBER(2),
        YEAR(1999))),
    ("12th-16th cent.", Seq(ORDINAL(12), RANGESEPARATOR, CENTURY(16))),
    (
      "12th-13th dec 1678",
      Seq(ORDINAL(12), RANGESEPARATOR, ORDINAL(13), MONTH("dec"), YEAR(1678))),
    ("1456", Seq(YEAR(1456))),
    ("1974 nov", Seq(YEAR(1974), MONTH("nov"))),
    (
      "1974 nov - dec",
      Seq(YEAR(1974), MONTH("nov"), RANGESEPARATOR, MONTH("dec"))),
    (
      "1982 sep - nov 01",
      Seq(YEAR(1982), MONTH("sep"), RANGESEPARATOR, MONTH("nov"), NUMBER(1))),
    (
      "1974 nov - 1975 dec",
      Seq(YEAR(1974), MONTH("nov"), RANGESEPARATOR, YEAR(1975), MONTH("dec"))),
    (
      "1974 nov - 1975 dec 31",
      Seq(
        YEAR(1974),
        MONTH("nov"),
        RANGESEPARATOR,
        YEAR(1975),
        MONTH("dec"),
        NUMBER(31))),
    (
      "1974 nov 01 - dec",
      Seq(YEAR(1974), MONTH("nov"), NUMBER(1), RANGESEPARATOR, MONTH("dec"))),
    (
      "1974 nov 01 - dec 31",
      Seq(
        YEAR(1974),
        MONTH("nov"),
        NUMBER(1),
        RANGESEPARATOR,
        MONTH("dec"),
        NUMBER(31))),
    (
      "1974 nov 01 - 30",
      Seq(YEAR(1974), MONTH("nov"), NUMBER(1), RANGESEPARATOR, NUMBER(30))),
    (
      "1970 mar 01 - 1990",
      Seq(YEAR(1970), MONTH("mar"), NUMBER(1), RANGESEPARATOR, YEAR(1990))),
    (
      "1974 nov 01 - 1975 dec 31",
      Seq(
        YEAR(1974),
        MONTH("nov"),
        NUMBER(1),
        RANGESEPARATOR,
        YEAR(1975),
        MONTH("dec"),
        NUMBER(31))),
    (
      "1974 - 1975 nov",
      Seq(YEAR(1974), RANGESEPARATOR, YEAR(1975), MONTH("nov"))),
    (
      "1974 - 1975 nov 30",
      Seq(YEAR(1974), RANGESEPARATOR, YEAR(1975), MONTH("nov"), NUMBER(30))),
    ("1256-15th century", Seq(YEAR(1256), RANGESEPARATOR, CENTURY(15))),
    ("16th century-1704", Seq(CENTURY(16), RANGESEPARATOR, YEAR(1704))),
    ("1789-1867", Seq(YEAR(1789), RANGESEPARATOR, YEAR(1867))),
    ("spring 1918", Seq(SEASON("spring"), YEAR(1918))),
    (
      "spring 1918-summer 1920",
      Seq(
        SEASON("spring"),
        YEAR(1918),
        RANGESEPARATOR,
        SEASON("summer"),
        YEAR(1920))),
    ("easter 1916", Seq(LAWTERM("easter"), YEAR(1916))),
    (
      "hilary 1966-trinity 1974",
      Seq(
        LAWTERM("hilary"),
        YEAR(1966),
        RANGESEPARATOR,
        LAWTERM("trinity"),
        YEAR(1974)))
  )

  describe("lexer") {
    it("lexes all examples correctly") {
      forEvery(validExamples) {
        case (str, expectedTokens) =>
          PeriodLexer(str) shouldBe Some(expectedTokens)
      }
    }
  }

}

package uk.ac.wellcome.platform.transformer.calm.periods

import org.scalatest.Inspectors
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class LexerTest extends AnyFunSpec with Matchers with Inspectors {

  val validExamples: Seq[(String, Seq[PeriodToken])] = Seq(
    ("1900s", Seq(Century(20))),
    ("1800s-1900s", Seq(Century(19), RangeSeparator, Century(20))),
    ("1910s", Seq(Decade(1910))),
    ("1910s-1920s", Seq(Decade(1910), RangeSeparator, Decade(1920))),
    (
      "jan-may 1999",
      Seq(Month("jan"), RangeSeparator, Month("may"), Year(1999))),
    ("april 1456", Seq(Month("apr"), Year(1456))),
    (
      "january 1256-february 2002",
      Seq(Month("jan"), Year(1256), RangeSeparator, Month("feb"), Year(2002))),
    (
      "jan 1689-23 december 2001",
      Seq(
        Month("jan"),
        Year(1689),
        RangeSeparator,
        Number(23),
        Month("dec"),
        Year(2001))),
    (
      "jan 1689-23rd december 2001",
      Seq(
        Month("jan"),
        Year(1689),
        RangeSeparator,
        Ordinal(23),
        Month("dec"),
        Year(2001))),
    ("late 13th century", Seq(Late, Century(13))),
    ("12/6/1278", Seq(Number(12), Slash, Number(6), Slash, Year(1278))),
    (
      "13/01/1245-23/08/1678",
      Seq(
        Number(13),
        Slash,
        Number(1),
        Slash,
        Year(1245),
        RangeSeparator,
        Number(23),
        Slash,
        Number(8),
        Slash,
        Year(1678))),
    (
      "12 dec-16 dec 1435",
      Seq(
        Number(12),
        Month("dec"),
        RangeSeparator,
        Number(16),
        Month("dec"),
        Year(1435))),
    ("15 jul 1678", Seq(Number(15), Month("jul"), Year(1678))),
    (
      "13 jun-15 december 1778",
      Seq(
        Number(13),
        Month("jun"),
        RangeSeparator,
        Number(15),
        Month("dec"),
        Year(1778))),
    ("13 aug 1787", Seq(Number(13), Month("aug"), Year(1787))),
    (
      "14 sep 1357-jan 1367",
      Seq(
        Number(14),
        Month("sep"),
        Year(1357),
        RangeSeparator,
        Month("jan"),
        Year(1367))),
    (
      "23 apr 1278-28 feb 1456",
      Seq(
        Number(23),
        Month("apr"),
        Year(1278),
        RangeSeparator,
        Number(28),
        Month("feb"),
        Year(1456))),
    (
      "23 jan 1300-1301",
      Seq(Number(23), Month("jan"), Year(1300), RangeSeparator, Year(1301))),
    ("12-13 century", Seq(Number(12), RangeSeparator, Century(13))),
    (
      "23-27 jan 1987",
      Seq(Number(23), RangeSeparator, Number(27), Month("jan"), Year(1987))),
    ("1974 nov 30", Seq(Year(1974), Month("nov"), Number(30))),
    ("early 12th century", Seq(Early, Century(12))),
    (
      "12th century-mid 20th century",
      Seq(Century(12), RangeSeparator, Mid, Century(20))),
    (
      "29th oct-30th oct 2002",
      Seq(
        Ordinal(29),
        Month("oct"),
        RangeSeparator,
        Ordinal(30),
        Month("oct"),
        Year(2002))),
    (
      "10th dec 2002 a.d.",
      Seq(Ordinal(10), Month("dec"), Year(2002), Era("a.d."))),
    (
      "11th jan 1899-12 dec 1999",
      Seq(
        Ordinal(11),
        Month("jan"),
        Year(1899),
        RangeSeparator,
        Number(12),
        Month("dec"),
        Year(1999))),
    (
      "23rd dec 1233-23rd mar 1733",
      Seq(
        Ordinal(23),
        Month("dec"),
        Year(1233),
        RangeSeparator,
        Ordinal(23),
        Month("mar"),
        Year(1733))),
    (
      "13th jul 1456-1789",
      Seq(Ordinal(13), Month("jul"), Year(1456), RangeSeparator, Year(1789))),
    ("23rd 12 1899", Seq(Ordinal(23), Number(12), Year(1899))),
    (
      "23rd 12 1899-13th 2 1999",
      Seq(
        Ordinal(23),
        Number(12),
        Year(1899),
        RangeSeparator,
        Ordinal(13),
        Number(2),
        Year(1999))),
    ("12th-16th cent.", Seq(Ordinal(12), RangeSeparator, Century(16))),
    (
      "12th-13th dec 1678",
      Seq(Ordinal(12), RangeSeparator, Ordinal(13), Month("dec"), Year(1678))),
    ("1456", Seq(Year(1456))),
    ("1974 nov", Seq(Year(1974), Month("nov"))),
    (
      "1974 nov - dec",
      Seq(Year(1974), Month("nov"), RangeSeparator, Month("dec"))),
    (
      "1982 sep - nov 01",
      Seq(Year(1982), Month("sep"), RangeSeparator, Month("nov"), Number(1))),
    (
      "1974 nov - 1975 dec",
      Seq(Year(1974), Month("nov"), RangeSeparator, Year(1975), Month("dec"))),
    (
      "1974 nov - 1975 dec 31",
      Seq(
        Year(1974),
        Month("nov"),
        RangeSeparator,
        Year(1975),
        Month("dec"),
        Number(31))),
    (
      "1974 nov 01 - dec",
      Seq(Year(1974), Month("nov"), Number(1), RangeSeparator, Month("dec"))),
    (
      "1974 nov 01 - dec 31",
      Seq(
        Year(1974),
        Month("nov"),
        Number(1),
        RangeSeparator,
        Month("dec"),
        Number(31))),
    (
      "1974 nov 01 - 30",
      Seq(Year(1974), Month("nov"), Number(1), RangeSeparator, Number(30))),
    (
      "1970 mar 01 - 1990",
      Seq(Year(1970), Month("mar"), Number(1), RangeSeparator, Year(1990))),
    (
      "1974 nov 01 - 1975 dec 31",
      Seq(
        Year(1974),
        Month("nov"),
        Number(1),
        RangeSeparator,
        Year(1975),
        Month("dec"),
        Number(31))),
    (
      "1974 - 1975 nov",
      Seq(Year(1974), RangeSeparator, Year(1975), Month("nov"))),
    (
      "1974 - 1975 nov 30",
      Seq(Year(1974), RangeSeparator, Year(1975), Month("nov"), Number(30))),
    ("1256-15th century", Seq(Year(1256), RangeSeparator, Century(15))),
    ("16th century-1704", Seq(Century(16), RangeSeparator, Year(1704))),
    ("1789-1867", Seq(Year(1789), RangeSeparator, Year(1867))),
    ("spring 1918", Seq(Season("spring"), Year(1918))),
    (
      "spring 1918-summer 1920",
      Seq(
        Season("spring"),
        Year(1918),
        RangeSeparator,
        Season("summer"),
        Year(1920))),
    ("easter 1916", Seq(LawTerm("easter"), Year(1916))),
    (
      "hilary 1966-trinity 1974",
      Seq(
        LawTerm("hilary"),
        Year(1966),
        RangeSeparator,
        LawTerm("trinity"),
        Year(1974)))
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

package weco.pipeline.transformer.parse

import java.time.LocalDate

import org.scalatest.Inspectors
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.InstantRange

class PeriodParserTest extends AnyFunSpec with Matchers with Inspectors {

  // From: http://www.dswebhosting.info/documents/Manuals/ALM/V10/MANUAL/main_menu/basics/period_field_format.htm
  // Can't use a Table() because the list is too long (> 22 elements)
  val documentedExamples: Seq[(String, InstantRange)] = Seq(
    (
      "1900s",
      InstantRange(
        LocalDate of (1900, 1, 1),
        LocalDate of (1999, 12, 31),
        "1900s")),
    (
      "1800s-1900s",
      InstantRange(
        LocalDate of (1800, 1, 1),
        LocalDate of (1999, 12, 31),
        "1800s-1900s")),
    (
      "1910s",
      InstantRange(
        LocalDate of (1910, 1, 1),
        LocalDate of (1919, 12, 31),
        "1910s")),
    (
      "1910s-1920s",
      InstantRange(
        LocalDate of (1910, 1, 1),
        LocalDate of (1929, 12, 31),
        "1910s-1920s")),
    (
      "jan-may 1999",
      InstantRange(
        LocalDate of (1999, 1, 1),
        LocalDate of (1999, 5, 31),
        "jan-may 1999")),
    (
      "april 1456",
      InstantRange(
        LocalDate of (1456, 4, 1),
        LocalDate of (1456, 4, 30),
        "april 1456")),
    (
      "january 1256-february 2002",
      InstantRange(
        LocalDate of (1256, 1, 1),
        LocalDate of (2002, 2, 28),
        "january 1256-february 2002")),
    (
      "jan 1689-23 december 2001",
      InstantRange(
        LocalDate of (1689, 1, 1),
        LocalDate of (2001, 12, 23),
        "jan 1689-23 december 2001")),
    (
      "jan 1689-23rd december 2001",
      InstantRange(
        LocalDate of (1689, 1, 1),
        LocalDate of (2001, 12, 23),
        "jan 1689-23rd december 2001")),
    (
      "late 13th century",
      InstantRange(
        LocalDate of (1260, 1, 1),
        LocalDate of (1299, 12, 31),
        "late 13th century")),
    (
      "12/6/1278",
      InstantRange(
        LocalDate of (1278, 6, 12),
        LocalDate of (1278, 6, 12),
        "12/6/1278")),
    (
      "13/01/1245-23/08/1678",
      InstantRange(
        LocalDate of (1245, 1, 13),
        LocalDate of (1678, 8, 23),
        "13/01/1245-23/08/1678")),
    (
      "12 dec-16 dec 1435",
      InstantRange(
        LocalDate of (1435, 12, 12),
        LocalDate of (1435, 12, 16),
        "12 dec-16 dec 1435")),
    (
      "15 jul 1678",
      InstantRange(
        LocalDate of (1678, 7, 15),
        LocalDate of (1678, 7, 15),
        "15 jul 1678")),
    (
      "13 jun-15 december 1778",
      InstantRange(
        LocalDate of (1778, 6, 13),
        LocalDate of (1778, 12, 15),
        "13 jun-15 december 1778")),
    (
      "13 aug 1787",
      InstantRange(
        LocalDate of (1787, 8, 13),
        LocalDate of (1787, 8, 13),
        "13 aug 1787")),
    (
      "14 sep 1357-jan 1367",
      InstantRange(
        LocalDate of (1357, 9, 14),
        LocalDate of (1367, 1, 31),
        "14 sep 1357-jan 1367")),
    (
      "23 apr 1278-28 feb 1456",
      InstantRange(
        LocalDate of (1278, 4, 23),
        LocalDate of (1456, 2, 28),
        "23 apr 1278-28 feb 1456")),
    (
      "23 jan 1300-1301",
      InstantRange(
        LocalDate of (1300, 1, 23),
        LocalDate of (1301, 12, 31),
        "23 jan 1300-1301")),
    (
      "12-13 century",
      InstantRange(
        LocalDate of (1100, 1, 1),
        LocalDate of (1299, 12, 31),
        "12-13 century")),
    (
      "23-27 jan 1987",
      InstantRange(
        LocalDate of (1987, 1, 23),
        LocalDate of (1987, 1, 27),
        "23-27 jan 1987")),
    (
      "1974 nov 30",
      InstantRange(
        LocalDate of (1974, 11, 30),
        LocalDate of (1974, 11, 30),
        "1974 nov 30")),
    (
      "early 12th century",
      InstantRange(
        LocalDate of (1100, 1, 1),
        LocalDate of (1139, 12, 31),
        "early 12th century")),
    (
      "12th century-mid 20th century",
      InstantRange(
        LocalDate of (1100, 1, 1),
        LocalDate of (1969, 12, 31),
        "12th century-mid 20th century")),
    (
      "29th oct-30th oct 2002",
      InstantRange(
        LocalDate of (2002, 10, 29),
        LocalDate of (2002, 10, 30),
        "29th oct-30th oct 2002")),
    (
      "10th dec 2002 a.d.",
      InstantRange(
        LocalDate of (2002, 12, 10),
        LocalDate of (2002, 12, 10),
        "10th dec 2002 a.d.")),
    (
      "11th jan 1899-12 dec 1999",
      InstantRange(
        LocalDate of (1899, 1, 11),
        LocalDate of (1999, 12, 12),
        "11th jan 1899-12 dec 1999")),
    (
      "23rd dec 1233-23rd mar 1733",
      InstantRange(
        LocalDate of (1233, 12, 23),
        LocalDate of (1733, 3, 23),
        "23rd dec 1233-23rd mar 1733")),
    (
      "13th jul 1456-1789",
      InstantRange(
        LocalDate of (1456, 7, 13),
        LocalDate of (1789, 12, 31),
        "13th jul 1456-1789")),
    (
      "23rd 12 1899",
      InstantRange(
        LocalDate of (1899, 12, 23),
        LocalDate of (1899, 12, 23),
        "23rd 12 1899")),
    (
      "23rd 12 1899-13th 2 1999",
      InstantRange(
        LocalDate of (1899, 12, 23),
        LocalDate of (1999, 2, 13),
        "23rd 12 1899-13th 2 1999")),
    (
      "12th-16th cent.",
      InstantRange(
        LocalDate of (1100, 1, 1),
        LocalDate of (1599, 12, 31),
        "12th-16th cent.")),
    (
      "12th-13th dec 1678",
      InstantRange(
        LocalDate of (1678, 12, 12),
        LocalDate of (1678, 12, 13),
        "12th-13th dec 1678")),
    (
      "1456",
      InstantRange(
        LocalDate of (1456, 1, 1),
        LocalDate of (1456, 12, 31),
        "1456")),
    (
      "1974 nov",
      InstantRange(
        LocalDate of (1974, 11, 1),
        LocalDate of (1974, 11, 30),
        "1974 nov")),
    (
      "1974 nov - dec",
      InstantRange(
        LocalDate of (1974, 11, 1),
        LocalDate of (1974, 12, 31),
        "1974 nov - dec")),
    (
      "1982 sep - nov 01",
      InstantRange(
        LocalDate of (1982, 9, 1),
        LocalDate of (1982, 11, 1),
        "1982 sep - nov 01")),
    (
      "1974 nov - 1975 dec",
      InstantRange(
        LocalDate of (1974, 11, 1),
        LocalDate of (1975, 12, 31),
        "1974 nov - 1975 dec")),
    (
      "1974 nov - 1975 dec 31",
      InstantRange(
        LocalDate of (1974, 11, 1),
        LocalDate of (1975, 12, 31),
        "1974 nov - 1975 dec 31")),
    (
      "1974 nov 01 - dec",
      InstantRange(
        LocalDate of (1974, 11, 1),
        LocalDate of (1974, 12, 31),
        "1974 nov 01 - dec")),
    (
      "1974 nov 01 - dec 31",
      InstantRange(
        LocalDate of (1974, 11, 1),
        LocalDate of (1974, 12, 31),
        "1974 nov 01 - dec 31")),
    (
      "1974 nov 01 - 30",
      InstantRange(
        LocalDate of (1974, 11, 1),
        LocalDate of (1974, 11, 30),
        "1974 nov 01 - 30")),
    (
      "1970 mar 01 - 1990",
      InstantRange(
        LocalDate of (1970, 3, 1),
        LocalDate of (1990, 12, 31),
        "1970 mar 01 - 1990")),
    (
      "1974 nov 01 - 1975 dec 31",
      InstantRange(
        LocalDate of (1974, 11, 1),
        LocalDate of (1975, 12, 31),
        "1974 nov 01 - 1975 dec 31")),
    (
      "1974 - 1975 nov",
      InstantRange(
        LocalDate of (1974, 1, 1),
        LocalDate of (1975, 11, 30),
        "1974 - 1975 nov")),
    (
      "1974 - 1975 nov 30",
      InstantRange(
        LocalDate of (1974, 1, 1),
        LocalDate of (1975, 11, 30),
        "1974 - 1975 nov 30")),
    (
      "1256-15th century",
      InstantRange(
        LocalDate of (1256, 1, 1),
        LocalDate of (1499, 12, 31),
        "1256-15th century")),
    (
      "16th century-1704",
      InstantRange(
        LocalDate of (1500, 1, 1),
        LocalDate of (1704, 12, 31),
        "16th century-1704")),
    (
      "1789-1867",
      InstantRange(
        LocalDate of (1789, 1, 1),
        LocalDate of (1867, 12, 31),
        "1789-1867")),
    (
      "spring 1918",
      InstantRange(
        LocalDate of (1918, 3, 1),
        LocalDate of (1918, 5, 31),
        "spring 1918")),
    (
      "spring 1918-summer 1920",
      InstantRange(
        LocalDate of (1918, 3, 1),
        LocalDate of (1920, 8, 31),
        "spring 1918-summer 1920")),
    (
      "easter 1916",
      InstantRange(
        LocalDate of (1916, 4, 1),
        LocalDate of (1916, 5, 31),
        "easter 1916")),
    (
      "hilary 1966-trinity 1974",
      InstantRange(
        LocalDate of (1966, 1, 1),
        LocalDate of (1974, 7, 31),
        "hilary 1966-trinity 1974"))
  )

  describe("parser") {
    it("parses all documented examples correctly") {
      forEvery(documentedExamples) {
        case (str, expectedTokens) =>
          PeriodParser(str) shouldBe Some(expectedTokens)
      }
    }

    it("strips no-op qualifiers") {
      PeriodParser("fl. 1999-2001 [gaps]") shouldBe Some(
        InstantRange(
          LocalDate of (1999, 1, 1),
          LocalDate of (2001, 12, 31),
          "fl. 1999-2001 [gaps]"))
    }

    it("strips Roman numerals") {
      PeriodParser("MDCCLXXXVII. [1787]") shouldBe Some(
        InstantRange(
          LocalDate of (1787, 1, 1),
          LocalDate of (1787, 12, 31),
          "MDCCLXXXVII. [1787]"))
    }

    it("handles dates from the BC era") {
      PeriodParser("1111 BC") shouldBe Some(
        InstantRange(
          LocalDate of (-1111, 1, 1),
          LocalDate of (-1111, 12, 31),
          "1111 BC"))
    }

    it("handles approximate year qualifiers") {
      PeriodParser("c.1920") shouldBe Some(
        InstantRange(
          LocalDate of (1910, 1, 1),
          LocalDate of (1929, 12, 31),
          "c.1920"))
    }

    it("handles approximate century qualifiers") {
      PeriodParser("circa 17th century") shouldBe Some(
        InstantRange(
          LocalDate of (1590, 1, 1),
          LocalDate of (1709, 12, 31),
          "circa 17th century"))
    }

    it("handles compound qualifiers") {
      PeriodParser("mid-late 19th century") shouldBe Some(
        InstantRange(
          LocalDate of (1830, 1, 1),
          LocalDate of (1899, 12, 31),
          "mid-late 19th century"))
    }

    it("disambiguates centuries and decades") {
      PeriodParser("2000s-2020s") shouldBe Some(
        InstantRange(
          LocalDate of (2000, 1, 1),
          LocalDate of (2029, 12, 31),
          "2000s-2020s"))
    }

    it("handles left-bounded intervals") {
      PeriodParser("1897-") shouldBe Some(
        InstantRange(
          LocalDate of (1897, 1, 1),
          LocalDate of (9999, 12, 31),
          "1897-"))
      PeriodParser("after 1897") shouldBe Some(
        InstantRange(
          LocalDate of (1897, 1, 1),
          LocalDate of (9999, 12, 31),
          "after 1897"))
    }

    it("handles right-bounded intervals") {
      PeriodParser("-1897") shouldBe Some(
        InstantRange(
          LocalDate of (-9999, 1, 1),
          LocalDate of (1897, 12, 31),
          "-1897"))
      PeriodParser("before 1897") shouldBe Some(
        InstantRange(
          LocalDate of (-9999, 1, 1),
          LocalDate of (1897, 12, 31),
          "before 1897"))
    }

    it("parses and combines multiple periods") {
      PeriodParser("1952, 1953, 1955, 1957-1960") shouldBe Some(
        InstantRange(
          LocalDate of (1952, 1, 1),
          LocalDate of (1960, 12, 31),
          "1952, 1953, 1955, 1957-1960"
        )
      )
    }

  }

}

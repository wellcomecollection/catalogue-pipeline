package weco.pipeline.transformer.parse

import org.scalatest.matchers.should.Matchers
import java.time.LocalDate

import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.internal_model.work.InstantRange
import weco.pipeline.transformer.parse.parsers.DateParser

class DateParserTest extends AnyFunSpec with Matchers {

  it("parses year") {
    DateParser("1972") shouldBe Some(
      InstantRange(
        LocalDate of (1972, 1, 1),
        LocalDate of (1972, 12, 31),
        "1972"))
  }

  it("parses inferred year") {
    DateParser("[1240]") shouldBe Some(
      InstantRange(
        LocalDate of (1240, 1, 1),
        LocalDate of (1240, 12, 31),
        "[1240]"))
  }

  it("parses written dates with day first") {
    DateParser("31 July 1980") shouldBe Some(
      InstantRange(
        LocalDate of (1980, 7, 31),
        LocalDate of (1980, 7, 31),
        "31 July 1980"))
  }

  it("parses written dates with month first") {
    DateParser("July 31 1980") shouldBe Some(
      InstantRange(
        LocalDate of (1980, 7, 31),
        LocalDate of (1980, 7, 31),
        "July 31 1980"))
  }

  it("parses written dates with ordinal suffix") {
    DateParser("July 31st 1980") shouldBe Some(
      InstantRange(
        LocalDate of (1980, 7, 31),
        LocalDate of (1980, 7, 31),
        "July 31st 1980"))
  }

  it("parses inferred written dates") {
    DateParser("[July 31st 1980]") shouldBe Some(
      InstantRange(
        LocalDate of (1980, 7, 31),
        LocalDate of (1980, 7, 31),
        "[July 31st 1980]")
    )
  }

  it("parses numeric dates") {
    DateParser("10/02/1913") shouldBe Some(
      InstantRange(
        LocalDate of (1913, 2, 10),
        LocalDate of (1913, 2, 10),
        "10/02/1913"))
  }

  it("parses inferred dates when only closing parentheses") {
    DateParser("10/02/1913]") shouldBe Some(
      InstantRange(
        LocalDate of (1913, 2, 10),
        LocalDate of (1913, 2, 10),
        "10/02/1913]"))
  }

  it("parses written month and year") {
    DateParser("Apr 1920") shouldBe Some(
      InstantRange(
        LocalDate of (1920, 4, 1),
        LocalDate of (1920, 4, 30),
        "Apr 1920"))
  }

  it("parses year ranges") {
    DateParser("1870-1873") shouldBe Some(
      InstantRange(
        LocalDate of (1870, 1, 1),
        LocalDate of (1873, 12, 31),
        "1870-1873"))
  }

  it("parses written date ranges spanning multiple years") {
    DateParser("2 Aug 1914 - 16 Apr 1915") shouldBe Some(
      InstantRange(
        LocalDate of (1914, 8, 2),
        LocalDate of (1915, 4, 16),
        "2 Aug 1914 - 16 Apr 1915"))
  }

  it("parses written date ranges without whitespace") {
    DateParser("2 Aug 1914-16 Apr 1915") shouldBe Some(
      InstantRange(
        LocalDate of (1914, 8, 2),
        LocalDate of (1915, 4, 16),
        "2 Aug 1914-16 Apr 1915"))
  }

  it("parses month ranges spanning multiple years") {
    DateParser("August 1914 - April 1915") shouldBe Some(
      InstantRange(
        LocalDate of (1914, 8, 1),
        LocalDate of (1915, 4, 30),
        "August 1914 - April 1915"))
  }

  it("parses day ranges within a single month") {
    DateParser("2-10 April 1968") shouldBe Some(
      InstantRange(
        LocalDate of (1968, 4, 2),
        LocalDate of (1968, 4, 10),
        "2-10 April 1968"))
  }

  it("parses month ranges within a single year") {
    DateParser("Jan-Mar 1940") shouldBe Some(
      InstantRange(
        LocalDate of (1940, 1, 1),
        LocalDate of (1940, 3, 31),
        "Jan-Mar 1940"))
  }

  it("fails when day is not valid for a particular month") {
    DateParser("31 June 2000") shouldBe None
  }

  it("fails when day in a range is not valid for a particular month") {
    DateParser("31-30 Jun 2020") shouldBe None
  }
}

package weco.pipeline.transformer.sierra.transformers.parsers

import org.scalatest.matchers.should.Matchers
import java.time.LocalDate

import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.internal_model.work.InstantRange

class Marc008DateParserTest extends AnyFunSpec with Matchers {

  it("parses single date") {
    Marc008DateParser("s1757    ") shouldBe Some(
      InstantRange(
        LocalDate of (1757, 1, 1),
        LocalDate of (1757, 12, 31),
        "1757"))
  }

  it("parses partial date consisting of century") {
    Marc008DateParser("s19uu    ") shouldBe Some(
      InstantRange(
        LocalDate of (1900, 1, 1),
        LocalDate of (1999, 12, 31),
        "1900-1999"))
  }

  it("parses partial date consisting of century and deccade") {
    Marc008DateParser("s199u    ") shouldBe Some(
      InstantRange(
        LocalDate of (1990, 1, 1),
        LocalDate of (1999, 12, 31),
        "1990-1999"))
  }

  it("parses multiple dates") {
    Marc008DateParser("m16251700") shouldBe Some(
      InstantRange(
        LocalDate of (1625, 1, 1),
        LocalDate of (1700, 12, 31),
        "1625-1700"))
  }

  it("parses reprint dates") {
    Marc008DateParser("r19961925") shouldBe Some(
      InstantRange(
        LocalDate of (1996, 1, 1),
        LocalDate of (1996, 12, 31),
        "1996"))
  }

  it("parses detailed dates") {
    Marc008DateParser("e19081121") shouldBe Some(
      InstantRange(
        LocalDate of (1908, 11, 21),
        LocalDate of (1908, 11, 21),
        "1908/11/21"))
  }

  it("parses publication and copyright dates") {
    Marc008DateParser("t19071907") shouldBe Some(
      InstantRange(
        LocalDate of (1907, 1, 1),
        LocalDate of (1907, 12, 31),
        "1907"))
  }

  it("parses continuing resource ceased publication") {
    Marc008DateParser("d19161924") shouldBe Some(
      InstantRange(
        LocalDate of (1916, 1, 1),
        LocalDate of (1924, 12, 31),
        "1916-1924"))
  }

  it("parses continuing resource currently published") {
    Marc008DateParser("c20009999") shouldBe Some(
      InstantRange(
        LocalDate of (2000, 1, 1),
        LocalDate of (9999, 12, 31),
        "2000-"))
  }

  it("parses continuing resource status unknown") {
    Marc008DateParser("u1959uuuu") shouldBe Some(
      InstantRange(
        LocalDate of (1959, 1, 1),
        LocalDate of (9999, 12, 31),
        "1959-"))
  }

  it("parses questionable dates") {
    Marc008DateParser("q16001699") shouldBe Some(
      InstantRange(
        LocalDate of (1600, 1, 1),
        LocalDate of (1699, 12, 31),
        "1600-1699"))
  }

  it("parses dates with different release and production") {
    Marc008DateParser("p20082006") shouldBe Some(
      InstantRange(
        LocalDate of (2008, 1, 1),
        LocalDate of (2008, 12, 31),
        "2008"))
  }

  it("parses single date when date2 filled with number sign") {
    Marc008DateParser("s1874####") shouldBe Some(
      InstantRange(
        LocalDate of (1874, 1, 1),
        LocalDate of (1874, 12, 31),
        "1874"))
  }

  it("drops an explicit 9999 from the displayed range") {
    Marc008DateParser("c20179999") shouldBe Some(
      InstantRange(
        LocalDate of (2017, 1, 1),
        LocalDate of (9999, 12, 31),
        "2017-"))
  }
}

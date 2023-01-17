package weco.pipeline.transformer.sierra.transformers.parsers

import org.scalatest.matchers.should.Matchers
import java.time.LocalDate

import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.internal_model.work.{
  InstantRange,
  Period,
  Place,
  ProductionEvent
}

class Marc008ParserTest extends AnyFunSpec with Matchers {

  it("parses marc strings to ProductionEvent") {
    Marc008Parser("790922s1757    enk||||      o00||||eng ccam   ") shouldBe
      Some(
        ProductionEvent(
          label = "1757",
          agents = Nil,
          dates = List(
            Period(
              label = "1757",
              range = InstantRange(
                LocalDate of (1757, 1, 1),
                LocalDate of (1757, 12, 31),
                "1757"))),
          places = List(Place("England")),
          function = None
        ))
  }

  it("parses marc strings to ProductionEvent when empty created date") {
    Marc008Parser("      s2003    enk050        0   vneng dngm a ") shouldBe
      Some(
        ProductionEvent(
          label = "2003",
          agents = Nil,
          dates = List(
            Period(
              label = "2003",
              range = InstantRange(
                LocalDate of (2003, 1, 1),
                LocalDate of (2003, 12, 31),
                "2003"))),
          places = List(Place("England")),
          function = None
        ))
  }

  it("parses marc strings to ProductionEvent when detailed date") {
    Marc008Parser("030623e19081121ua                k0eng dnkm a ") shouldBe
      Some(
        ProductionEvent(
          label = "1908/11/21",
          agents = Nil,
          dates = List(
            Period(
              label = "1908/11/21",
              range = InstantRange(
                LocalDate of (1908, 11, 21),
                LocalDate of (1908, 11, 21),
                "1908/11/21"))),
          places = List(Place("Egypt")),
          function = None
        ))
  }

  it("parses marc strings to ProductionEvent when place not given") {
    Marc008Parser("030818q16001699                  kn    dnka a ") shouldBe
      Some(
        ProductionEvent(
          label = "1600-1699",
          agents = Nil,
          dates = List(
            Period(
              label = "1600-1699",
              range = InstantRange(
                LocalDate of (1600, 1, 1),
                LocalDate of (1699, 12, 31),
                "1600-1699"))),
          places = Nil,
          function = None
        ))
  }

  it("doesnt parse marc strings to ProductionEvent when unknown date") {
    Marc008Parser("090914uuuuuuuuuxx                  engddnteuua") shouldBe
      None
  }
}

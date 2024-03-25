package weco.pipeline.transformer.tei.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.{Organisation, Place, ProductionEvent}
import weco.pipeline.transformer.tei.generators.TeiGenerators
import weco.pipeline.transformer.transformers.ParsedPeriod

class TeiProductionTest extends AnyFunSpec with TeiGenerators with Matchers {
  val id = "id"
  it("extracts the origin country") {

    val elem = teiXml(
      id,
      history =
        Some(history(origPlace = Some(origPlace(country = Some("India")))))
    )
    val result = TeiProduction(elem)

    result shouldBe List(
      ProductionEvent(
        "India",
        places = List(Place("India")),
        agents = Nil,
        dates = Nil
      )
    )
  }

  it("extracts the origin - country region and settlement") {
    val result = TeiProduction(
      teiXml(
        id,
        history = Some(
          history(origPlace =
            Some(
              origPlace(
                country = Some("United Kingdom"),
                region = Some("England"),
                settlement = Some("London")
              )
            )
          )
        )
      )
    )

    val label = "United Kingdom, England, London"
    result shouldBe List(
      ProductionEvent(
        label,
        places = List(Place(label)),
        agents = Nil,
        dates = Nil
      )
    )
  }

  it("ignores text not in country region or settlement") {
    val result = TeiProduction(
      teiXml(
        id,
        history = Some(
          history(origPlace =
            Some(
              origPlace(
                country = Some("United Kingdom"),
                region = Some("England"),
                settlement = Some("London"),
                label = Some("stuff")
              )
            )
          )
        )
      )
    )

    val label = "United Kingdom, England, London"
    result shouldBe List(
      ProductionEvent(
        label,
        places = List(Place(label)),
        agents = Nil,
        dates = Nil
      )
    )
  }

  it("returns an agent if there is an orgName") {
    val result = TeiProduction(
      teiXml(
        id,
        history = Some(
          history(origPlace =
            Some(
              origPlace(
                country = Some("Egypt"),
                settlement = Some("Wadi El Natrun"),
                orgName = Some("Monastery of St Macarius the Great")
              )
            )
          )
        )
      )
    )

    val label = "Egypt, Wadi El Natrun"
    result shouldBe List(
      ProductionEvent(
        label,
        places = List(Place(label)),
        agents = List(Organisation("Monastery of St Macarius the Great")),
        dates = Nil
      )
    )
  }

  it("returns a date") {
    val result = TeiProduction(
      teiXml(
        id,
        history =
          Some(history(originDates = List(originDate("Gregorian", "1756"))))
      )
    )

    result shouldBe List(
      ProductionEvent(
        label = "1756",
        places = Nil,
        agents = Nil,
        dates = List(ParsedPeriod("1756"))
      )
    )
  }

  it("doesn't return a date if the calendar is not Gregorian") {
    val result = TeiProduction(
      teiXml(
        id,
        history = Some(
          history(originDates =
            List(originDate("hijri", "5 Ramaḍān 838 (part 1)"))
          )
        )
      )
    )

    result shouldBe Nil
  }

  it("ignores non text nodes in the label") {
    val result = TeiProduction(
      teiXml(
        id,
        history = Some(
          history(originDates = List(<origDate calendar="gregorian">ca.1732-63AD
          <note>from watermarks</note>
        </origDate>))
        )
      )
    )
    result shouldBe List(
      ProductionEvent(
        label = "ca.1732-63AD",
        places = Nil,
        agents = Nil,
        dates = List(ParsedPeriod("ca.1732-63AD"))
      )
    )
  }
}

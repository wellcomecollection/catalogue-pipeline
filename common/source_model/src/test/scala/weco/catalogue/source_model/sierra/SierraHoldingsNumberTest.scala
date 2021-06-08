package weco.catalogue.source_model.sierra

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.source_model.generators.SierraGenerators

import scala.util.{Failure, Success}

class SierraHoldingsNumberTest
    extends AnyFunSpec
    with Matchers
    with SierraGenerators {
  case class Identity(id: SierraHoldingsNumber)

  it("decodes a String as a HoldingsNumber") {
    fromJson[Identity]("""{"id": "1234567"}""") shouldBe Success(
      Identity(SierraHoldingsNumber("1234567")))
  }

  it("decodes an Int as a HoldingsNumber") {
    fromJson[Identity]("""{"id": 1234567}""") shouldBe Success(
      Identity(SierraHoldingsNumber("1234567")))
  }

  it("decodes an old-style JSON record as a HoldingsNumber") {
    fromJson[Identity]("""{"id": {"recordNumber": "1234567"}}""") shouldBe Success(
      Identity(SierraHoldingsNumber("1234567")))
  }

  it("fails if the Int is the wrong format") {
    fromJson[Identity]("""{"id": 123456789}""") shouldBe a[Failure[_]]
  }

  it("casts a HoldingsNumber to Json and back") {
    val id = createSierraHoldingsNumber

    fromJson[SierraHoldingsNumber](toJson(id).get).get shouldBe id
  }
}

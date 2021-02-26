package weco.catalogue.sierra_adapter.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.sierra_adapter.models.Implicits._

import scala.util.{Failure, Success}

class SierraHoldingsNumberTest extends AnyFunSpec with Matchers {
  case class Identity(id: SierraHoldingsNumber)

  it("decodes a String as a HoldingsNumber") {
    fromJson[Identity]("""{"id": "1234567"}""") shouldBe Success(Identity(SierraHoldingsNumber("1234567")))
  }

  it("decodes an Int as a HoldingsNumber") {
    fromJson[Identity]("""{"id": 1234567}""") shouldBe Success(Identity(SierraHoldingsNumber("1234567")))
  }

  it("fails if the Int is the wrong format") {
    fromJson[Identity]("""{"id": 123456789}""") shouldBe a[Failure[_]]
  }
}

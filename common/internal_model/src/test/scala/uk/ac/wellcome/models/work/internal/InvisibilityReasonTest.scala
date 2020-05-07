package uk.ac.wellcome.models.work.internal

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.internal.InvisibilityReason.{CalmInvalidLevel, CalmMissingLevel}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.Implicits._

import scala.util.Success

class InvisibilityReasonTest extends FunSpec with Matchers {
  it("Encodes / decodes correctly") {
    val reasonNoInfo: InvisibilityReason = CalmMissingLevel
    val reasonWithInfo: InvisibilityReason = CalmInvalidLevel("no level")
    val reasonNoInfoJson = toJson(reasonNoInfo).get
    val reasonWithInfoJson = toJson(reasonWithInfo).get


    reasonNoInfoJson shouldBe """{"type":"CalmMissingLevel"}"""
    reasonWithInfoJson shouldBe """{"info":"no level","type":"CalmInvalidLevel"}"""

    fromJson[InvisibilityReason](reasonNoInfoJson) shouldBe Success(CalmMissingLevel)
    fromJson[InvisibilityReason](reasonWithInfoJson) shouldBe Success(CalmInvalidLevel("no level"))
  }
}

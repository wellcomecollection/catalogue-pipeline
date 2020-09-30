package uk.ac.wellcome.models.work.internal

import enumeratum.scalacheck._
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions

class FormatTest
    extends AnyFunSpec
    with JsonAssertions
    with ScalaCheckPropertyChecks {

  it("serialises Format to JSON") {
    forAll { format: Format =>
      val actualJson = toJson(format).get
      assertJsonStringsAreEqual(actualJson, formatJson(format.id, format.label))
    }
  }

  it("deserialises JSON as Format") {
    forAll { format: Format =>
      val parsedConcept =
        fromJson[Format](formatJson(format.id, format.label)).get
      parsedConcept shouldBe format
    }
  }

  def formatJson(id: String, label: String) =
    s"""{
        "id": "$id",
        "label": "$label"
      }"""
}

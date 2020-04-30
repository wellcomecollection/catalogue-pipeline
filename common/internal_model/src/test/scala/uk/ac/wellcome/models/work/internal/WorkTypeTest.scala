package uk.ac.wellcome.models.work.internal

import enumeratum.scalacheck._
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions

class WorkTypeTest extends AnyFunSpec with JsonAssertions with ScalaCheckPropertyChecks{

  it("serialises WorkType to JSON") {
    forAll { workType: WorkType =>
      val actualJson = toJson(workType).get
      assertJsonStringsAreEqual(
        actualJson,
        workTypeJson(workType.id, workType.label))
    }
  }

  it("deserialises JSON as WorkType") {
    forAll { workType: WorkType =>
      val parsedConcept =
        fromJson[WorkType](workTypeJson(workType.id, workType.label)).get
      parsedConcept shouldBe workType
    }
  }

  def workTypeJson(id: String, label: String) =
    s"""{
        "id": "$id",
        "label": "$label",
        "ontologyType": "WorkType"
      }"""
}

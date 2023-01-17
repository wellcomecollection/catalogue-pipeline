package weco.catalogue.internal_model.work

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.Implicits._
import weco.json.JsonUtil.{fromJson, toJson}
import weco.json.utils.JsonAssertions
import weco.catalogue.internal_model.identifiers.IdState

class ConceptTest extends AnyFunSpec with Matchers with JsonAssertions {

  val concept: Concept[IdState.Minted] = Concept(
    id = IdState.Unidentifiable,
    label = "Woodwork"
  )

  val expectedJson =
    s"""{
        "id": {"type": "Unidentifiable"},
        "label": "Woodwork"
      }"""

  it("serialises Concepts to JSON") {
    val actualJson = toJson(concept).get
    assertJsonStringsAreEqual(actualJson, expectedJson)
  }

  it("deserialises JSON as Concepts") {
    val parsedConcept = fromJson[Concept[IdState.Minted]](expectedJson).get
    parsedConcept shouldBe concept
  }
}

package uk.ac.wellcome.models.work.internal

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil.{toJson, fromJson}
import uk.ac.wellcome.json.utils.JsonAssertions

class ConceptTest extends FunSpec with Matchers with JsonAssertions {

  val concept = Concept[Minted](label = "Woodwork")
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
    val parsedConcept = fromJson[Concept[Minted]](expectedJson).get
    parsedConcept shouldBe concept
  }
}

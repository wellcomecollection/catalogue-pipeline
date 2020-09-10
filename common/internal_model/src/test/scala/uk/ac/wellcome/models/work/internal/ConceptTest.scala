package uk.ac.wellcome.models.work.internal

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil.{fromJson, toJson}
import uk.ac.wellcome.json.utils.JsonAssertions

class ConceptTest extends AnyFunSpec with Matchers with JsonAssertions {

  val concept = Concept[Id.Minted](label = "Woodwork")
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
    val parsedConcept = fromJson[Concept[Id.Minted]](expectedJson).get
    parsedConcept shouldBe concept
  }
}

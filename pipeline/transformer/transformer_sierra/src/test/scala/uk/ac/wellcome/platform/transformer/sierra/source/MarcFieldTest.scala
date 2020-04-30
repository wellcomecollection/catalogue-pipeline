package uk.ac.wellcome.platform.transformer.sierra.source

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions

class MarcFieldTest extends AnyFunSpec with Matchers with JsonAssertions {

  it("reads a JSON string as a long-form VarField") {
    val jsonString = s"""{
      "fieldTag": "n",
      "marcTag": "008",
      "ind1": " ",
      "ind2": " ",
      "subfields": [
        {
          "tag": "a",
          "content": "An armada of armadillos"
        },
        {
          "tag": "b",
          "content": "A bonanza of bears"
        },
        {
          "tag": "c",
          "content": "A cacophany of crocodiles"
        }
      ]
    }"""

    val expectedVarField = VarField(
      fieldTag = Some("n"),
      marcTag = Some("008"),
      indicator1 = Some(" "),
      indicator2 = Some(" "),
      subfields = List(
        MarcSubfield(tag = "a", content = "An armada of armadillos"),
        MarcSubfield(tag = "b", content = "A bonanza of bears"),
        MarcSubfield(tag = "c", content = "A cacophany of crocodiles")
      )
    )

    val varField = fromJson[VarField](jsonString).get
    varField shouldBe expectedVarField
  }

  it("reads a JSON string as a short-form VarField") {
    val jsonString = s"""{
      "fieldTag": "c",
      "content": "Enjoying an event with enormous eagles"
    }"""

    val expectedVarField = VarField(
      fieldTag = Some("c"),
      content = Some("Enjoying an event with enormous eagles")
    )

    fromJson[VarField](jsonString).get shouldBe expectedVarField
  }
}

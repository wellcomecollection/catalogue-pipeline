package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import shapeless.tag
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.marc.{Subfield, VarField}

class SierraAlternativeTitlesTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val (field240, field130, field246) = (
    createVarField("Apples", "240"),
    createVarField("Bananas", "130"),
    createVarField("Cherries", "246")
  )

  it("extracts an alternative title when there is 240") {
    val varFields = List(field240)
    getAlternativeTitles(varFields) shouldBe List("Apples")
  }

  it("extracts an alternative title when there is 130") {
    val varFields = List(field130)
    getAlternativeTitles(varFields) shouldBe List("Bananas")
  }

  it("extracts an alternative title when there is 246") {
    val varFields = List(field246)
    getAlternativeTitles(varFields) shouldBe List("Cherries")
  }

  it("extracts all alternative titles when multiple fields defined") {
    val varFields = List(field130, field240, field246)
    getAlternativeTitles(varFields) shouldBe List(
      "Bananas",
      "Apples",
      "Cherries")
  }

  it("extracts all alternative titles when repeated fields") {
    val varFields = List(field240, createVarField("Durian", "240"))
    getAlternativeTitles(varFields) shouldBe List("Apples", "Durian")
  }

  it("concatenates subfields for alternative titles") {
    val varFields = List(
      createVarFieldWith(
        marcTag = "240",
        subfields = List(
          Subfield(tag = "a", content = "start,"),
          Subfield(tag = "b", content = "end.")
        )
      )
    )
    getAlternativeTitles(varFields) shouldBe List("start, end.")
  }

  it("does not extract any alternative titles when no 240 / 130 / 246") {
    val varFields = List(createVarField("Xigua", "251"))
    getAlternativeTitles(varFields) shouldBe Nil
  }

  it("does not extract any alternative titles when 246 indicator2 is 6") {
    val varFields = List(createVarField("Xigua", "246", indicator2 = "6"))
    getAlternativeTitles(varFields) shouldBe Nil
  }

  it("extracts alternative titles when 240 / 130 indicator2 is 6") {
    val varFields = List(
      createVarField("Apples", "240", indicator2 = "6"),
      createVarField("Bananas", "130", indicator2 = "6")
    )
    getAlternativeTitles(varFields) shouldBe List("Apples", "Bananas")
  }

  it("omits a subfield $5 with content UkLW") {
    val varFields = List(
      createVarFieldWith(
        "246",
        "1",
        List(
          Subfield(tag = "a", content = "Apples"),
          Subfield(tag = "5", content = "Oranges"),
          Subfield(tag = "5", content = "UkLW")
        )
      ),
    )
    val result = getAlternativeTitles(varFields)
    result should have length 1
    result.head should include("Apples")
    result.head should include("Oranges")
    result.head should not include "UkLW"
  }

  it("does not omit a subfield $5 with content != UkLW") {
    val varFields = List(
      createVarFieldWith(
        "246",
        "1",
        List(
          Subfield(tag = "a", content = "Apples"),
          Subfield(tag = "5", content = "Oranges"),
          Subfield(tag = "5", content = "Carrots")
        )
      ),
    )
    val result = getAlternativeTitles(varFields)
    result should have length 1
    result.head should include("Apples")
    result.head should include("Oranges")
    result.head should include("Carrots")
  }

  it("deduplicates alternative titles") {
    // This is based on the MARC record for b1301898x, as retrieved 23 January 2021
    val varFields = List(
      createVarFieldWith(
        marcTag = "240",
        indicator2 = "1",
        subfields = List(
          Subfield(tag = "a", content = "De rerum natura")
        )
      ),
      createVarFieldWith(
        marcTag = "246",
        indicator2 = "1",
        subfields = List(
          Subfield(tag = "a", content = "De rerum natura")
        )
      )
    )

    getAlternativeTitles(varFields) shouldBe List("De rerum natura")
  }

  private def getAlternativeTitles(varFields: List[VarField]) =
    SierraAlternativeTitles(createSierraBibDataWith(varFields = varFields))

  private def createVarField(
    content: String,
    tag: String,
    indicator2: String = "1",
    contentTag: String = "a"
  ) =
    createVarFieldWith(
      tag,
      indicator2,
      Subfield(tag = contentTag, content = content) :: Nil)
}

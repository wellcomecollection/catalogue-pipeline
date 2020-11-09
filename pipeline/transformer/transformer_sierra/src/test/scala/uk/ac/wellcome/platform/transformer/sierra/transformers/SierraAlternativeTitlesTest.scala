package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import shapeless.tag
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}

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

  it("should extract an alternative title when there is 240") {
    val varFields = List(field240)
    getAlternativeTitles(varFields) shouldBe List("Apples")
  }

  it("should extract an alternative title when there is 130") {
    val varFields = List(field130)
    getAlternativeTitles(varFields) shouldBe List("Bananas")
  }

  it("should extract an alternative title when there is 246") {
    val varFields = List(field246)
    getAlternativeTitles(varFields) shouldBe List("Cherries")
  }

  it("should extract all alternative titles when multiple fields defined") {
    val varFields = List(field130, field240, field246)
    getAlternativeTitles(varFields) shouldBe List(
      "Apples",
      "Bananas",
      "Cherries")
  }

  it("should extract all alternative titles when repeated fields") {
    val varFields = List(field240, createVarField("Durian", "240"))
    getAlternativeTitles(varFields) shouldBe List("Apples", "Durian")
  }

  it("should concatenate subfields for alternative titles") {
    val varFields = List(
      createVarFieldWith(
        marcTag = "240",
        subfields = List(
          MarcSubfield(tag = "a", content = "start,"),
          MarcSubfield(tag = "b", content = "end.")
        )
      )
    )
    getAlternativeTitles(varFields) shouldBe List("start, end.")
  }

  it("should not extract any alternative titles when no 240 / 130 / 246") {
    val varFields = List(createVarField("Xigua", "251"))
    getAlternativeTitles(varFields) shouldBe Nil
  }

  it("should not extract any alternative titles when 246 indicator2 is 6") {
    val varFields = List(createVarField("Xigua", "246", indicator2 = "6"))
    getAlternativeTitles(varFields) shouldBe Nil
  }

  it("should still extract alternative titles when 240 / 130 indicator2 is 6") {
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
          MarcSubfield(tag = "a", content = "Apples"),
          MarcSubfield(tag = "5", content = "Oranges"),
          MarcSubfield(tag = "5", content = "UkLW")
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
          MarcSubfield(tag = "a", content = "Apples"),
          MarcSubfield(tag = "5", content = "Oranges"),
          MarcSubfield(tag = "5", content = "Carrots")
        )
      ),
    )
    val result = getAlternativeTitles(varFields)
    result should have length 1
    result.head should include("Apples")
    result.head should include("Oranges")
    result.head should include("Carrots")
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
      MarcSubfield(tag = contentTag, content = content) :: Nil)
}

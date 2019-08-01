package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}

class SierraPartNumberTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val partNumber = "Part Number"

  it("should extract partNumber from 245") {
    val varFields = List(createVarField(partNumber))
    getPartNumber(varFields) shouldBe Some(partNumber)
  }

  it("should extract first partNumber from 245 when multiple defined") {
    val varFields = List(createVarField(partNumber), createVarField("another"))
    getPartNumber(varFields) shouldBe Some(partNumber)
  }

  it("should not extract partNumber from 245 when incorrect contentTag") {
    val varFields = List(createVarField(partNumber, contentTag = "a"))
    getPartNumber(varFields) shouldBe None
  }

  val transformer = new SierraPartNumber {}

  private def getPartNumber(varFields: List[VarField]) =
    transformer getPartNumber (createSierraBibDataWith(
      varFields = varFields))

  private def createVarField(
    content: String,
    tag: String = "245",
    contentTag: String = "n"
  ) =
    createVarFieldWith(
      tag,
      "1",
      MarcSubfield(tag = contentTag, content = content) :: Nil)
}

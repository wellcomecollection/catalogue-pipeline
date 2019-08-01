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

class SierraPartNameTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val partName = "Part Name"

  it("should extract partName from 245") {
    val varFields = List(createVarField(partName))
    getPartName(varFields) shouldBe Some(partName)
  }

  it("should extract first partName from 245 when multiple defined") {
    val varFields = List(createVarField(partName), createVarField("another"))
    getPartName(varFields) shouldBe Some(partName)
  }

  it("should not extract partName from 245 when incorrect contentTag") {
    val varFields = List(createVarField(partName, contentTag = "n"))
    getPartName(varFields) shouldBe None
  }

  val transformer = new SierraPartName {}

  private def getPartName(varFields: List[VarField]) =
    transformer getPartName (createSierraBibDataWith(
      varFields = varFields))

  private def createVarField(
    content: String,
    tag: String = "245",
    contentTag: String = "p"
  ) =
    createVarFieldWith(
      tag,
      "1",
      MarcSubfield(tag = contentTag, content = content) :: Nil)
}

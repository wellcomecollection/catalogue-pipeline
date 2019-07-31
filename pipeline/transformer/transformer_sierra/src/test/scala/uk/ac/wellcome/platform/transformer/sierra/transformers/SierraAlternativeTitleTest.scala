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

class SierraAlternativeTitleTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val alternativeTitle = "Alt title"

  it("should extract alternativeTitle when there is 246 data") {
    val varFields = createVarField(alternativeTitle) :: Nil
    getAlternativeTitle(varFields) shouldBe Some(alternativeTitle)
  }

  it("should not extract alternativeTitle when there no 246") {
    val varFields = createVarField(alternativeTitle, tag = "251") :: Nil
    getAlternativeTitle(varFields) shouldBe None
  }

  it("should not extract alternativeTitle when indicator2 is 6") {
    val varFields = createVarField(alternativeTitle, indicator2 = "6") :: Nil
    getAlternativeTitle(varFields) shouldBe None
  }

  it("should not extract alternativeTitle when there no 'a' subfield in 246") {
    val varFields = createVarField(alternativeTitle, contentTag = "b") :: Nil
    getAlternativeTitle(varFields) shouldBe None
  }

  it("should use first varfields content when multiple 250s defined") {
    val varFields = List(createVarField(alternativeTitle), createVarField("123"))
    getAlternativeTitle(varFields) shouldBe Some(alternativeTitle)
  }

  val transformer = new SierraAlternativeTitle {}

  private def getAlternativeTitle(varFields: List[VarField]) =
    transformer getAlternativeTitle (createSierraBibDataWith(varFields = varFields))

  private def createVarField(
    content: String,
    tag: String = "246",
    indicator2: String = "1",
    contentTag: String = "a",
  ) =
    createVarFieldWith(
      tag,
      indicator2,
      MarcSubfield(tag = contentTag, content = content) :: Nil)
}

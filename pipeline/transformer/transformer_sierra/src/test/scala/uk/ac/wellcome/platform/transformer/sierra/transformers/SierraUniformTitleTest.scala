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

class SierraUniformTitleTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val title = "Shepherd of Salisbury-Plain."

  val altTitle = "Basic documents."

  it("should extract uniform title when there is 240") {
    val varFields = createVarField(title) :: Nil
    uniformTitle(varFields) shouldBe Some(title)
  }

  it("should ignore uniform title when there no 240") {
    val varFields = createVarField(title, tag = "253") :: Nil
    uniformTitle(varFields) shouldBe None
  }

  it("should ignore uniform title when there no 'a' subfield") {
    val varFields = createVarField(title, contentTag = "b") :: Nil
    uniformTitle(varFields) shouldBe None
  }

  it("should use first content varfield when multiple 240s defined") {
    val varFields = List(createVarField(title), createVarField("x"))
    uniformTitle(varFields) shouldBe Some(title)
  }

  it("should extract uniform title from 130 when no 240") {
    val varFields = List(createVarField(title, "130"))
    uniformTitle(varFields) shouldBe Some(title)
  }

  it("should extract uniform title from 240 when both 130 and 240") {
    val varFields = List(createVarField(altTitle, "130"), createVarField(title, "240"))
    uniformTitle(varFields) shouldBe Some(title)
  }

  val transformer = new SierraUniformTitle {}

  private def uniformTitle(varFields: List[VarField]) =
    transformer getUniformTitle (createSierraBibDataWith(varFields = varFields))

  private def createVarField(
    content: String,
    tag: String = "240",
    contentTag: String = "a"
  ) =
    createVarFieldWith(
      tag,
      "1",
      MarcSubfield(tag = contentTag, content = content) :: Nil)
}

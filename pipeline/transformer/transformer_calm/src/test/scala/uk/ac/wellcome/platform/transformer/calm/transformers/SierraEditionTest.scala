package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.transformer.calm.source.{MarcSubfield, VarField}
import uk.ac.wellcome.platform.transformer.calm.generators.{
  MarcGenerators,
  SierraDataGenerators
}

class SierraEditionTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val edition = "1st edition."

  val altEdition = "2nd edition."

  it("should extract edition when there is 250 data") {
    val varFields = createVarField(edition) :: Nil
    getEdition(varFields) shouldBe Some(edition)
  }

  it("should not extract edition when there no 250") {
    val varFields = createVarField(edition, tag = "251") :: Nil
    getEdition(varFields) shouldBe None
  }

  it("should not extract edition when there no 'a' subfield in 250") {
    val varFields = createVarField(edition, contentTag = "b") :: Nil
    getEdition(varFields) shouldBe None
  }

  it("should combine varfields contents when multiple 250s defined") {
    val varFields = List(createVarField(edition), createVarField(altEdition))
    getEdition(varFields) shouldBe Some("1st edition. 2nd edition.")
  }

  private def getEdition(varFields: List[VarField]) =
    SierraEdition(
      createSierraBibNumber,
      createSierraBibDataWith(varFields = varFields))

  private def createVarField(
    content: String,
    tag: String = "250",
    contentTag: String = "a"
  ) =
    createVarFieldWith(
      tag,
      "1",
      MarcSubfield(tag = contentTag, content = content) :: Nil)
}

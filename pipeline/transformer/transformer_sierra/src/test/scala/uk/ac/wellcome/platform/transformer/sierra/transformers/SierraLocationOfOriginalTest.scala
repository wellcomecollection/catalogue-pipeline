package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraBibData,
  VarField
}

class SierraLocationOfOriginalTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val content = "location of original content"

  it("should extract locationOfOriginal from 535") {
    SierraLocationOfOriginal(bibId, bibData(content)) shouldBe Some(content)
  }

  it("should extract first locationOfOriginal from 535 when multiple defined") {
    SierraLocationOfOriginal(
      bibId,
      bibData(varField(content), varField("More"))
    ) shouldBe Some(content)
  }

  it("should not extract locationOfOriginal when incorrect varfield") {
    SierraLocationOfOriginal(bibId, bibData(content, tag = "500")) shouldBe None
  }

  it("should not extract locationOfOriginal when incorrect subfield") {
    SierraLocationOfOriginal(bibId, bibData(content, subfieldTag = "b")) shouldBe None
  }

  def bibId = createSierraBibNumber

  def bibData(content: String,
              tag: String = "535",
              subfieldTag: String = "a"): SierraBibData =
    bibData(varField(content, tag, subfieldTag))

  def bibData(varFields: VarField*): SierraBibData =
    createSierraBibDataWith(varFields = varFields.toList)

  def varField(content: String,
               tag: String = "535",
               subfieldTag: String = "a") =
    createVarFieldWith(
      marcTag = tag,
      subfields = List(MarcSubfield(tag = subfieldTag, content = content))
    )
}

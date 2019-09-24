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

class SierraCiteAsTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val content = "cite as content"

  it("should extract citeaAs from 524") {
    SierraCiteAs(bibId, bibData(content)) shouldBe Some(content)
  }

  it("should extract first citeAs from 524 when multiple defined") {
    SierraCiteAs(
      bibId,
      bibData(varField(content), varField("More"))
    ) shouldBe Some(content)
  }

  it("should not extract citeAs when incorrect varfield") {
    SierraCiteAs(bibId, bibData(content, tag = "500")) shouldBe None
  }

  it("should not extract citeAs when incorrect subfield") {
    SierraCiteAs(bibId, bibData(content, subfieldTag = "b")) shouldBe None
  }

  def bibId = createSierraBibNumber

  def bibData(content: String,
              tag: String = "524",
              subfieldTag: String = "a"): SierraBibData =
    bibData(varField(content, tag, subfieldTag))

  def bibData(varFields: VarField*): SierraBibData =
    createSierraBibDataWith(varFields = varFields.toList)

  def varField(content: String,
               tag: String = "524",
               subfieldTag: String = "a") =
    createVarFieldWith(
      marcTag = tag,
      subfields = List(MarcSubfield(tag = subfieldTag, content = content))
    )
}

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

class SierraDissertationTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val content = "Thesis (M.A.)--University College, London, 1969."

  it("should extract dissertation from 502") {
    SierraDissertation(bibId, bibData(content)) shouldBe Some(content)
  }

  it("should extract first dissertation from 502 when multiple defined") {
    SierraDissertation(
      bibId, bibData(varField(content), varField("More"))
    ) shouldBe Some(content)
  }

  it("should not extract dissertation when incorrect varfield") {
    SierraDissertation(
      bibId, bibData(content, tag = "500")) shouldBe None
  }

  it("should not extract dissertation when incorrect subfield") {
    SierraDissertation(
      bibId, bibData(content, subfieldTag = "b")) shouldBe None
  }

  def bibId = createSierraBibNumber

  def bibData(content: String, tag: String = "502", subfieldTag: String = "a"): SierraBibData =
    bibData(varField(content, tag, subfieldTag))

  def bibData(varFields: VarField*): SierraBibData =
    createSierraBibDataWith(varFields = varFields.toList)

  def varField(content: String, tag: String = "502", subfieldTag: String = "a") =
    createVarFieldWith(
      marcTag = tag,
      subfields = List(MarcSubfield(tag = subfieldTag, content = content))
    )
}

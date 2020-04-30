package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraBibData,
  VarField
}

class SierraDurationTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("should extract duration in milliseconds from 306") {
    SierraDuration(bibId, bibData("011012")) shouldBe Some(4212000)
  }

  it("should use first duration when multiple defined") {
    SierraDuration(
      bibId,
      bibData(varField("001000"), varField("001132"))
    ) shouldBe Some(600000)
  }

  it("should not extract duration when varfield badly formatted") {
    SierraDuration(bibId, bibData("01xx1012", tag = "500")) shouldBe None
  }

  it("should not extract duration when incorrect varfield") {
    SierraDuration(bibId, bibData("011012", tag = "500")) shouldBe None
  }

  it("should not extract duration when incorrect subfield") {
    SierraDuration(bibId, bibData("011012", subfieldTag = "b")) shouldBe None
  }

  def bibId = createSierraBibNumber

  def bibData(content: String,
              tag: String = "306",
              subfieldTag: String = "a"): SierraBibData =
    bibData(varField(content, tag, subfieldTag))

  def bibData(varFields: VarField*): SierraBibData =
    createSierraBibDataWith(varFields = varFields.toList)

  def varField(content: String,
               tag: String = "306",
               subfieldTag: String = "a") =
    createVarFieldWith(
      marcTag = tag,
      subfields = List(MarcSubfield(tag = subfieldTag, content = content))
    )
}

package uk.ac.wellcome.platform.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import weco.catalogue.internal_model.locations.License
import weco.catalogue.source_model.miro.MiroSourceOverrides

class MiroThumbnailTest extends AnyFunSpec with Matchers with MiroRecordGenerators {
  val transformer = new MiroThumbnail {}

  it("uses the source overrides") {
    val miroRecord = createMiroRecordWith(useRestrictions = Some("CC-0"))

    val thumbnail1 = transformer.getThumbnail(
      miroRecord = miroRecord,
      overrides = MiroSourceOverrides.empty
    )

    thumbnail1.license shouldBe Some(License.CC0)

    val thumbnail2 = transformer.getThumbnail(
      miroRecord = miroRecord,
      overrides = MiroSourceOverrides(
        license = Some(License.InCopyright)
      )
    )

    thumbnail2.license shouldBe Some(License.InCopyright)
  }
}

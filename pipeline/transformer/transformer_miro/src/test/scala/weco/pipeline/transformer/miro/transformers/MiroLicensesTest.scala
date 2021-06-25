package weco.pipeline.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.License
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.pipeline.transformer.miro.exceptions.{
  ShouldNotTransformException,
  ShouldSuppressException
}

class MiroLicensesTest extends AnyFunSpec with Matchers {
  it("finds a recognised license") {
    chooseLicense(Some("CC-0")) shouldBe License.CC0
  }

  it("accepts an 'In Copyright' record") {
    chooseLicense(Some("In copyright")) shouldBe License.InCopyright
  }

  it("rejects restrictions 'Do not use'") {
    intercept[ShouldSuppressException] {
      chooseLicense(Some("Do not use"))
    }
  }

  it("rejects restrictions 'Image withdrawn, see notes'") {
    intercept[ShouldSuppressException] {
      chooseLicense(Some("Image withdrawn, see notes"))
    }
  }

  it("rejects the record if the usage restrictions are unspecified") {
    intercept[ShouldNotTransformException] {
      chooseLicense(maybeUseRestrictions = None)
    }
  }

  it("uses the license override, if set") {
    val maybeUseRestrictions = Some("CC-0")

    transformer.chooseLicense(
      maybeUseRestrictions = maybeUseRestrictions,
      overrides = MiroSourceOverrides.empty
    ) shouldBe License.CC0

    transformer.chooseLicense(
      maybeUseRestrictions = maybeUseRestrictions,
      overrides = MiroSourceOverrides(license = Some(License.InCopyright))
    ) shouldBe License.InCopyright
  }

  private def chooseLicense(maybeUseRestrictions: Option[String]): License =
    transformer.chooseLicense(
      maybeUseRestrictions = maybeUseRestrictions,
      overrides = MiroSourceOverrides.empty
    )

  val transformer = new MiroLicenses {}
}

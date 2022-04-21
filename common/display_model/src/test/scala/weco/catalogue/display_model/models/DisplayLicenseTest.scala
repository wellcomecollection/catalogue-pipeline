package weco.catalogue.display_model.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.License

class DisplayLicenseTest extends AnyFunSpec with Matchers {
  it("reads a License as a DisplayLicense") {
    val displayLicense = DisplayLicense(License.CCBY)

    displayLicense.id shouldBe License.CCBY.id
    displayLicense.label shouldBe License.CCBY.label
    displayLicense.url shouldBe License.CCBY.url
    displayLicense.ontologyType shouldBe "License"
  }
}

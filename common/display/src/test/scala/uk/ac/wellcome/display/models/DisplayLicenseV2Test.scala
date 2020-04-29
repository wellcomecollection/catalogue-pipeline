package uk.ac.wellcome.display.models

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.internal.License

class DisplayLicenseV2Test extends FunSpec with Matchers {
  it("reads a License as a DisplayLicenseV2") {
    val displayLicense = DisplayLicenseV2(License.CCBY)

    displayLicense.id shouldBe License.CCBY.id
    displayLicense.label shouldBe License.CCBY.label
    displayLicense.url shouldBe License.CCBY.url
    displayLicense.ontologyType shouldBe "License"
  }
}

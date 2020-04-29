package uk.ac.wellcome.display.models

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.internal.License

class DisplayLicenseTest extends FunSpec with Matchers {
  it("reads a License as a DisplayLicense") {
    val displayLicense = DisplayLicense(License.CCBY)

    displayLicense.id shouldBe License.CCBY.id
    displayLicense.label shouldBe License.CCBY.label
    displayLicense.url shouldBe License.CCBY.url
    displayLicense.ontologyType shouldBe "License"
  }
}

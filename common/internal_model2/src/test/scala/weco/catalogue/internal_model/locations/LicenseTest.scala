package weco.catalogue.internal_model.locations

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.Implicits._
import weco.json.JsonUtil.{fromJson, toJson}

class LicenseTest extends AnyFunSpec with Matchers {

  it("can serialise and then deserialise any license as JSON") {
    License.values.foreach { license =>
      assertRoundTripsLicenseCorrectly(license)
    }
  }

  def assertRoundTripsLicenseCorrectly(license: License): Assertion = {
    val result = toJson[License](license)
    result.isSuccess shouldBe true

    val jsonString = result.get
    val parsedLicense = fromJson[License](jsonString)
    parsedLicense.isSuccess shouldBe true

    parsedLicense.get shouldBe license
  }
}

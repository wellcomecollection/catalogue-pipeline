package weco.catalogue.display_model.locations

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.display_model.Implicits._
import weco.catalogue.display_model.test.util.{
  DisplaySerialisationTestBase,
  JsonMapperTestUtil
}
import weco.catalogue.internal_model.locations._

class DisplayLocationsSerialisationTest
    extends AnyFunSpec
    with DisplaySerialisationTestBase
    with JsonMapperTestUtil {

  it("serialises a physical location") {
    val location = PhysicalLocation(
      locationType = LocationType.ClosedStores,
      label = LocationType.ClosedStores.label
    )

    assertLocationMapsToJson(location, physicalLocation(location))
  }

  it("serialises a digital location") {
    val location = DigitalLocation(
      url = "https://wellcomelibrary.org/iiif/b22015085/manifest",
      locationType = LocationType.IIIFPresentationAPI
    )

    assertLocationMapsToJson(location, digitalLocation(location))
  }

  it("serialises a digital location with a license") {
    val location = DigitalLocation(
      url = "https://wellcomelibrary.org/iiif/b22015085/manifest",
      locationType = LocationType.IIIFPresentationAPI,
      license = Some(License.CC0)
    )

    assertLocationMapsToJson(location, digitalLocation(location))
  }

  it("serialises a digital location with an access condition") {
    val location = DigitalLocation(
      url = "https://wellcomelibrary.org/iiif/b22015085/manifest",
      locationType = LocationType.IIIFPresentationAPI,
      accessConditions = List(
        AccessCondition(
          method = AccessMethod.ViewOnline,
          status = Some(AccessStatus.Restricted),
          terms = Some("Ask politely")
        )
      )
    )

    assertLocationMapsToJson(location, digitalLocation(location))
  }

  private def assertLocationMapsToJson(
    location: Location,
    expectedJson: String
  ): Assertion =
    assertObjectMapsToJson(
      DisplayLocation(location),
      expectedJson = expectedJson
    )
}

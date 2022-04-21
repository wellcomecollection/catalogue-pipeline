package weco.catalogue.display_model.locations

import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.display_model.DisplaySerialisationTestBase
import weco.catalogue.display_model.Implicits._
import weco.catalogue.display_model.test.util.JsonMapperTestUtil
import weco.catalogue.display_model.work.DisplayWork
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work.generators._

class DisplayLocationsSerialisationTest
    extends AnyFunSpec
    with DisplaySerialisationTestBase
    with JsonMapperTestUtil
    with WorkGenerators
    with ItemsGenerators {

  it("serialises a physical location") {
    val physicalLocation = PhysicalLocation(
      locationType = LocationType.ClosedStores,
      label = LocationType.ClosedStores.label
    )

    val item = createIdentifiedItemWith(locations = List(physicalLocation))
    val work = indexedWork().items(List(item))

    val expectedJson = s"""[ ${items(work.data.items)} ]"""

    assertObjectMapsToJson(DisplayWork(work).items, expectedJson)
  }

  it("serialises a digital location") {
    val digitalLocation = DigitalLocation(
      url = "https://wellcomelibrary.org/iiif/b22015085/manifest",
      locationType = LocationType.IIIFPresentationAPI
    )

    val work = indexedWork().items(
      List(createIdentifiedItemWith(locations = List(digitalLocation)))
    )

    val expectedJson = s"""[ ${items(work.data.items)} ]"""

    assertObjectMapsToJson(DisplayWork(work).items, expectedJson)
  }

  it("serialises a digital location with a license") {
    val digitalLocation = DigitalLocation(
      url = "https://wellcomelibrary.org/iiif/b22015085/manifest",
      locationType = LocationType.IIIFPresentationAPI,
      license = Some(License.CC0)
    )

    val work = indexedWork().items(
      List(createIdentifiedItemWith(locations = List(digitalLocation)))
    )

    val expectedJson = s"""[ ${items(work.data.items)} ]"""

    assertObjectMapsToJson(DisplayWork(work).items, expectedJson)
  }

  it("serialises a digital location with an access condition") {
    val digitalLocation = DigitalLocation(
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

    val work = indexedWork().items(
      List(createIdentifiedItemWith(locations = List(digitalLocation)))
    )

    val expectedJson = s"""[ ${items(work.data.items)} ]"""

    assertObjectMapsToJson(DisplayWork(work).items, expectedJson)
  }
}

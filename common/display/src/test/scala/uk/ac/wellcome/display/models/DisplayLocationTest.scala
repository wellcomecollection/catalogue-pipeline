package uk.ac.wellcome.display.models

import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.display.json.DisplayJsonUtil._
import uk.ac.wellcome.display.test.util.JsonMapperTestUtil
import uk.ac.wellcome.models.work.generators.{
  IdentifiersGenerators,
  WorksGenerators
}
import uk.ac.wellcome.models.work.internal._

class DisplayLocationTest
    extends AnyFunSpec
    with DisplaySerialisationTestBase
    with JsonMapperTestUtil
    with IdentifiersGenerators
    with WorksGenerators {
  it("Encodes the DisplayLocation ADT correctly") {
    val digitalResource = DisplayLocation(
      Location.DigitalResource(
        accessConditions = Nil,
        url = "https://api.wellcomecollection.org",
        license = None,
        credit = None,
        format = Some(DigitalResourceFormat.IIIFPresentation)))

    val openShelves = DisplayLocation(
      Location.OpenShelves(
        accessConditions = Nil,
        shelfmark = "shelf mark",
        shelfLocation = "shelf location"))

    val closedStores =
      DisplayLocation(Location.ClosedStores(accessConditions = Nil))

    assertObjectMapsToJson(
      digitalResource,
      expectedJson = """{
                       |  "accessConditions" : [
                       |  ],
                       |  "url" : "https://api.wellcomecollection.org",
                       |  "format" : {
                       |    "label" : "IIIF presentation",
                       |    "type" : "IIIFPresentation"
                       |  },
                       |  "label" : "Online",
                       |  "type" : "DigitalLocation"
                       |}""".stripMargin
    )

    assertObjectMapsToJson(
      openShelves,
      expectedJson = """{
                       |  "accessConditions" : [
                       |  ],
                       |  "shelfmark" : "shelf mark",
                       |  "shelfLocation" : "shelf location",
                       |  "label" : "Open shelves",
                       |  "type" : "OpenShelves"
                       |}""".stripMargin
    )

    assertObjectMapsToJson(
      closedStores,
      expectedJson = """{
                       |  "accessConditions" : [
                       |  ],
                       |  "label" : "Closed stores",
                       |  "type" : "ClosedStores"
                       |}""".stripMargin
    )
  }
}

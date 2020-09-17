package uk.ac.wellcome.display.models

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.display.json.DisplayJsonUtil._
import uk.ac.wellcome.display.test.util.JsonMapperTestUtil
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import WorkState.Identified

class DisplayLocationsSerialisationTestDeprecated
    extends AnyFunSpec
    with DisplaySerialisationTestBase
    with JsonMapperTestUtil
    with WorksGenerators {

  it("serialises a physical location") {
    val physicalLocation = PhysicalLocationDeprecated(
      locationType = LocationType("sgmed"),
      label = "a stack of slick slimes"
    )

    val work = createIdentifiedWorkWith(
      items = List(createIdentifiedItemWith(locations = List(physicalLocation)))
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "alternativeTitles": [],
      | "items": [ ${items(work.data.items)} ]
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson = expectedJson)
  }

  it("serialises a digital location") {
    val digitalLocation = DigitalLocationDeprecated(
      url = "https://wellcomelibrary.org/iiif/b22015085/manifest",
      locationType = LocationType("iiif-image"),
    )

    val work = createIdentifiedWorkWith(
      items = List(createIdentifiedItemWith(locations = List(digitalLocation)))
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "alternativeTitles": [],
      | "items": [ ${items(work.data.items)} ]
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson = expectedJson)
  }

  it("serialises a digital location with a license") {
    val digitalLocation = DigitalLocationDeprecated(
      url = "https://wellcomelibrary.org/iiif/b22015085/manifest",
      locationType = LocationType("iiif-image"),
      license = Some(License.CC0)
    )

    val work = createIdentifiedWorkWith(
      items = List(createIdentifiedItemWith(locations = List(digitalLocation)))
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "alternativeTitles": [],
      | "items": [ ${items(work.data.items)} ]
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson = expectedJson)
  }

  it("serialises a digital location with an access condition") {
    val digitalLocation = DigitalLocationDeprecated(
      url = "https://wellcomelibrary.org/iiif/b22015085/manifest",
      locationType = LocationType("iiif-image"),
      accessConditions = List(
        AccessCondition(
          status = Some(AccessStatus.Restricted),
          terms = Some("Ask politely"),
          to = Some("2024-02-24")
        )
      )
    )

    val work = createIdentifiedWorkWith(
      items = List(createIdentifiedItemWith(locations = List(digitalLocation)))
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "alternativeTitles": [],
      | "items": [ ${items(work.data.items)} ]
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson = expectedJson)
  }

  private def assertWorkMapsToJson(
    work: Work.Visible[Identified],
    expectedJson: String
  ): Assertion =
    assertObjectMapsToJson(
      DisplayWork(work, includes = WorksIncludes(items = true)),
      expectedJson = expectedJson
    )
}

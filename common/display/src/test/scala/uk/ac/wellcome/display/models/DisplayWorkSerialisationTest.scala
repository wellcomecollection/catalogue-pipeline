package uk.ac.wellcome.display.models

import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.display.test.util.JsonMapperTestUtil
import uk.ac.wellcome.models.work.generators.{
  ImageGenerators,
  ProductionEventGenerators,
  SubjectGenerators,
  WorksGenerators
}
import uk.ac.wellcome.models.work.internal.WorkType.{Books, EBooks}
import uk.ac.wellcome.models.work.internal._
import Implicits._

class DisplayWorkSerialisationTest
    extends AnyFunSpec
    with DisplaySerialisationTestBase
    with JsonMapperTestUtil
    with ProductionEventGenerators
    with SubjectGenerators
    with WorksGenerators
    with ImageGenerators {

  it("serialises a DisplayWork") {
    val work = createIdentifiedWorkWith(
      workType = Some(Books),
      description = Some(randomAlphanumeric(100)),
      lettering = Some(randomAlphanumeric(100)),
      createdDate = Some(Period("1901"))
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "description": "${work.data.description.get}",
      | "workType" : ${workType(work.data.workType.get)},
      | "lettering": "${work.data.lettering.get}",
      | "alternativeTitles": [],
      | "createdDate": ${period(work.data.createdDate.get)}
      |}
    """.stripMargin

    assertObjectMapsToJson(DisplayWork(work), expectedJson = expectedJson)
  }

  it("renders an item if the items include is present") {
    val work = createIdentifiedWorkWith(
      items = createIdentifiedItems(count = 1) :+ createUnidentifiableItemWith()
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

    assertObjectMapsToJson(
      DisplayWork(work, includes = WorksIncludes(items = true)),
      expectedJson = expectedJson
    )
  }

  it("includes 'items' if the items include is present, even with no items") {
    val work = createIdentifiedWorkWith(
      items = List()
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "alternativeTitles": [],
      | "items": [ ]
      |}
    """.stripMargin

    assertObjectMapsToJson(
      DisplayWork(work, includes = WorksIncludes(items = true)),
      expectedJson = expectedJson
    )
  }

  it("includes credit information in DisplayWork serialisation") {
    val location = DigitalLocationDeprecated(
      locationType = LocationType("thumbnail-image"),
      url = "",
      credit = Some("Wellcome Collection"),
      license = Some(License.CCBY)
    )
    val item = createIdentifiedItemWith(locations = List(location))
    val workWithCopyright = createIdentifiedWorkWith(
      items = List(item)
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${workWithCopyright.state.canonicalId}",
      | "title": "${workWithCopyright.data.title.get}",
      | "alternativeTitles": [],
      | "items": [
      |   {
      |     "id": "${item.id.canonicalId}",
      |     "type": "Item",
      |     "locations": [
      |       {
      |         "type": "DigitalLocation",
      |         "url": "",
      |         "locationType": ${locationType(location.locationType)},
      |         "license": ${license(location.license.get)},
      |         "credit": "${location.credit.get}",
      |         "accessConditions" : []
      |       }
      |     ]
      |   }
      | ]
      |}
    """.stripMargin

    assertObjectMapsToJson(
      DisplayWork(
        workWithCopyright,
        includes = WorksIncludes(items = true)
      ),
      expectedJson = expectedJson
    )
  }

  it(
    "includes subject information in DisplayWork serialisation with the subjects include") {
    val workWithSubjects = createIdentifiedWorkWith(
      subjects = (1 to 3).map { _ =>
        createSubject
      }.toList
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${workWithSubjects.state.canonicalId}",
      | "title": "${workWithSubjects.data.title.get}",
      | "alternativeTitles": [],
      | "subjects": [${subjects(workWithSubjects.data.subjects)}]
      |}
    """.stripMargin

    assertObjectMapsToJson(
      DisplayWork(
        workWithSubjects,
        includes = WorksIncludes(subjects = true)
      ),
      expectedJson = expectedJson
    )
  }

  it(
    "includes production information in DisplayWork serialisation with the production include") {
    val workWithProduction = createIdentifiedWorkWith(
      production = createProductionEventList(count = 3)
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${workWithProduction.state.canonicalId}",
      | "title": "${workWithProduction.data.title.get}",
      | "alternativeTitles": [],
      | "production": [${production(workWithProduction.data.production)}]
      |}
    """.stripMargin

    assertObjectMapsToJson(
      DisplayWork(
        workWithProduction,
        includes = WorksIncludes(production = true)
      ),
      expectedJson = expectedJson
    )
  }

  it(
    "includes the contributors in DisplayWork serialisation with the contribuotrs include") {
    val work = createIdentifiedWorkWith(
      workType = Some(EBooks),
      description = Some(randomAlphanumeric(100)),
      lettering = Some(randomAlphanumeric(100)),
      createdDate = Some(Period("1901")),
      contributors = List(
        Contributor(agent = Agent(randomAlphanumeric(25)), roles = Nil)
      )
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "description": "${work.data.description.get}",
      | "alternativeTitles": [],
      | "workType" : ${workType(work.data.workType.get)},
      | "lettering": "${work.data.lettering.get}",
      | "createdDate": ${period(work.data.createdDate.get)},
      | "contributors": [${contributor(work.data.contributors.head)}]
      |}
    """.stripMargin

    assertObjectMapsToJson(
      DisplayWork(work, includes = WorksIncludes(contributors = true)),
      expectedJson = expectedJson
    )
  }

  it(
    "includes genre information in DisplayWork serialisation with the genres include") {
    val work = createIdentifiedWorkWith(
      genres = List(
        Genre(
          label = "genre",
          concepts = List(Concept("woodwork"), Concept("etching"))
        )
      )
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "alternativeTitles": [],
      | "genres": [ ${genres(work.data.genres)} ]
      |}
    """.stripMargin

    assertObjectMapsToJson(
      DisplayWork(work, includes = WorksIncludes(genres = true)),
      expectedJson = expectedJson
    )
  }

  it(
    "includes 'notes' if the notes include is present, with similar notes grouped together") {
    val work = createIdentifiedWorkWith(
      notes = List(GeneralNote("A"), FundingInformation("B"), GeneralNote("C"))
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "alternativeTitles": [],
      | "notes": [
      |   {
      |     "noteType": {
      |       "id": "general-note",
      |       "label": "Notes",
      |       "type": "NoteType"
      |     },
      |     "contents": ["A", "C"],
      |     "type": "Note"
      |   },
      |   {
      |     "noteType": {
      |       "id": "funding-info",
      |       "label": "Funding information",
      |       "type": "NoteType"
      |     },
      |     "contents": ["B"],
      |     "type": "Note"
      |   }
      | ]
      |}
    """.stripMargin

    assertObjectMapsToJson(
      DisplayWork(work, includes = WorksIncludes(notes = true)),
      expectedJson = expectedJson
    )
  }

  it("includes a list of identifiers on DisplayWork") {
    val otherIdentifier = createSourceIdentifier
    val work = createIdentifiedWorkWith(
      otherIdentifiers = List(otherIdentifier)
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "alternativeTitles": [],
      | "identifiers": [
      |   ${identifier(work.sourceIdentifier)},
      |   ${identifier(otherIdentifier)}
      | ]
      |}
    """.stripMargin

    assertObjectMapsToJson(
      DisplayWork(work, includes = WorksIncludes(identifiers = true)),
      expectedJson = expectedJson
    )
  }

  it("always includes 'identifiers' with the identifiers include") {
    val work = createIdentifiedWorkWith(
      otherIdentifiers = List()
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "alternativeTitles": [],
      | "identifiers": [ ${identifier(work.sourceIdentifier)} ]
      |}
    """.stripMargin

    assertObjectMapsToJson(
      DisplayWork(work, includes = WorksIncludes(identifiers = true)),
      expectedJson = expectedJson
    )
  }

  it("includes image stubs with the images include") {
    val work = createIdentifiedWorkWith(
      images = (1 to 3).map(_ => createUnmergedImage.toIdentified).toList
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "alternativeTitles": [],
      | "images": [${workImageIncludes(work.data.images)}]
      |}
    """.stripMargin

    assertObjectMapsToJson(
      DisplayWork(work, includes = WorksIncludes(images = true)),
      expectedJson
    )
  }

  it("shows the thumbnail field if available") {
    val work = createIdentifiedWorkWith(
      thumbnail = Some(
        DigitalLocationDeprecated(
          locationType = LocationType("thumbnail-image"),
          url = "https://iiif.example.org/1234/default.jpg",
          license = Some(License.CCBY)
        ))
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "alternativeTitles": [],
      | "thumbnail": ${location(work.data.thumbnail.get)}
      |}
    """.stripMargin

    assertObjectMapsToJson(
      DisplayWork(work, includes = WorksIncludes()),
      expectedJson = expectedJson
    )
  }
}

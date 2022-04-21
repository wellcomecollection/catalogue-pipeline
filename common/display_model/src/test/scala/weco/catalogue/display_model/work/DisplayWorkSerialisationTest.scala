package weco.catalogue.display_model.work

import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.display_model.DisplaySerialisationTestBase
import weco.catalogue.display_model.test.util.JsonMapperTestUtil
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work._
import weco.catalogue.internal_model.work.generators.{ProductionEventGenerators, SubjectGenerators}

import java.time.Instant

class DisplayWorkSerialisationTest
    extends AnyFunSpec
    with DisplaySerialisationTestBase
    with JsonMapperTestUtil
    with ProductionEventGenerators
    with SubjectGenerators
    with ImageGenerators {

  it("serialises a DisplayWork") {
    val work = indexedWork()
      .format(Format.Books)
      .description(randomAlphanumeric(100))
      .lettering(randomAlphanumeric(100))
      .createdDate(Period("1901", InstantRange(Instant.now, Instant.now)))

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "description": "${work.data.description.get}",
      | "workType" : ${format(work.data.format.get)},
      | "lettering": "${work.data.lettering.get}",
      | "createdDate": ${period(work.data.createdDate.get)},
      | "availabilities": [${availabilities(work.state.availabilities)}],
      | "identifiers": ${sourceIdentifiers(work.identifiers)},
      | "alternativeTitles": [],
      | "contributors": [],
      | "genres": [],
      | "holdings": [],
      | "images": [],
      | "items": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson)
  }

  it("includes the items") {
    val work = indexedWork()
      .items(createIdentifiedItems(count = 1) :+ createUnidentifiableItem)

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "items": [ ${items(work.data.items)} ],
      | "availabilities": [${availabilities(work.state.availabilities)}],
      | "identifiers": ${sourceIdentifiers(work.identifiers)},
      | "alternativeTitles": [],
      | "contributors": [],
      | "genres": [],
      | "holdings": [],
      | "images": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson)
  }

  it("includes 'items', even if there aren't any") {
    val work = indexedWork().items(Nil)

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "items": [ ],
      | "availabilities": [${availabilities(work.state.availabilities)}],
      | "identifiers": ${sourceIdentifiers(work.identifiers)},
      | "alternativeTitles": [],
      | "contributors": [],
      | "genres": [],
      | "holdings": [],
      | "images": [],
      | "items": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson)
  }

  it("includes credit information") {
    val location = DigitalLocation(
      locationType = LocationType.OnlineResource,
      url = "",
      credit = Some("Wellcome Collection"),
      license = Some(License.CCBY)
    )
    val item = createIdentifiedItemWith(locations = List(location))
    val workWithCopyright = indexedWork().items(List(item))

    val expectedJson =
      s"""
      |{
      | "type": "Work",
      | "id": "${workWithCopyright.state.canonicalId}",
      | "title": "${workWithCopyright.data.title.get}",
      | "items": [
      |   {
      |     "id": "${item.id.canonicalId}",
      |     "type": "Item",
      |     "identifiers": ${sourceIdentifiers(item.id.allSourceIdentifiers)},
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
      | ],
      | "availabilities": [${availabilities(
           workWithCopyright.state.availabilities
         )}],
      | "identifiers": ${sourceIdentifiers(workWithCopyright.identifiers)},
      | "alternativeTitles": [],
      | "contributors": [],
      | "genres": [],
      | "holdings": [],
      | "images": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(workWithCopyright, expectedJson)
  }

  it("includes subject information") {
    val workWithSubjects = indexedWork().subjects(
      (1 to 3).map(_ => createSubject).toList
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${workWithSubjects.state.canonicalId}",
      | "title": "${workWithSubjects.data.title.get}",
      | "subjects": [${subjects(workWithSubjects.data.subjects)}],
      | "availabilities": [${availabilities(
                            workWithSubjects.state.availabilities
                          )}],
      | "identifiers": ${sourceIdentifiers(workWithSubjects.identifiers)},
      | "alternativeTitles": [],
      | "contributors": [],
      | "genres": [],
      | "holdings": [],
      | "images": [],
      | "items": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "succeededBy": []
      |}
    """.stripMargin

    println(expectedJson)

    assertWorkMapsToJson(workWithSubjects, expectedJson)
  }

  it("includes production information") {
    val workWithProduction = indexedWork().production(
      createProductionEventList(count = 3)
    )

    val expectedJson =
      s"""
      |{
      | "type": "Work",
      | "id": "${workWithProduction.state.canonicalId}",
      | "title": "${workWithProduction.data.title.get}",
      | "production": [${production(workWithProduction.data.production)}],
      | "availabilities": [${availabilities(
           workWithProduction.state.availabilities
         )}],
      | "identifiers": ${sourceIdentifiers(workWithProduction.identifiers)},
      | "alternativeTitles": [],
      | "contributors": [],
      | "genres": [],
      | "holdings": [],
      | "images": [],
      | "items": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(workWithProduction, expectedJson)
  }

  it("includes the contributors") {
    val work = indexedWork()
      .format(Format.EBooks)
      .description(randomAlphanumeric(100))
      .lettering(randomAlphanumeric(100))
      .createdDate(Period("1901", InstantRange(Instant.now, Instant.now)))
      .contributors(
        List(
          Contributor(agent = Agent(randomAlphanumeric(25)), roles = Nil)
        )
      )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "description": "${work.data.description.get}",
      | "workType" : ${format(work.data.format.get)},
      | "lettering": "${work.data.lettering.get}",
      | "createdDate": ${period(work.data.createdDate.get)},
      | "contributors": [${contributor(work.data.contributors.head)}],
      | "availabilities": [${availabilities(work.state.availabilities)}],
      | "identifiers": ${sourceIdentifiers(work.identifiers)},
      | "alternativeTitles": [],
      | "genres": [],
      | "holdings": [],
      | "images": [],
      | "items": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson)
  }

  it("includes genres") {
    val work = indexedWork().genres(
      List(
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
      | "genres": [ ${genres(work.data.genres)} ],
      | "availabilities": [${availabilities(work.state.availabilities)}],
      | "identifiers": ${sourceIdentifiers(work.identifiers)},
      | "alternativeTitles": [],
      | "contributors": [],
      | "holdings": [],
      | "images": [],
      | "items": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson)
  }

  it("includes 'notes', with similar notes grouped together") {
    val work = indexedWork().notes(
      List(
        Note(contents = "A", noteType = NoteType.GeneralNote),
        Note(contents = "B", noteType = NoteType.FundingInformation),
        Note(contents = "C", noteType = NoteType.GeneralNote)
      )
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
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
      | ],
      | "availabilities": [${availabilities(work.state.availabilities)}],
      | "identifiers": ${sourceIdentifiers(work.identifiers)},
      | "alternativeTitles": [],
      | "contributors": [],
      | "genres": [],
      | "holdings": [],
      | "images": [],
      | "items": [],
      | "languages": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson)
  }

  it("includes a list of identifiers") {
    val otherIdentifier = createSourceIdentifier
    val work = indexedWork().otherIdentifiers(List(otherIdentifier))

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "identifiers": [
      |   ${identifier(work.sourceIdentifier)},
      |   ${identifier(otherIdentifier)}
      | ],
      | "availabilities": [${availabilities(work.state.availabilities)}],
      | "alternativeTitles": [],
      | "contributors": [],
      | "genres": [],
      | "holdings": [],
      | "images": [],
      | "items": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson)
  }

  it("always includes 'identifiers', even if otherIdentifiers is empty") {
    val work = indexedWork().otherIdentifiers(Nil)

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "identifiers": [ ${identifier(work.sourceIdentifier)} ],
      | "availabilities": [${availabilities(work.state.availabilities)}],
      | "alternativeTitles": [],
      | "contributors": [],
      | "genres": [],
      | "holdings": [],
      | "images": [],
      | "items": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson)
  }

  it("includes image stubs") {
    val work = indexedWork().imageData(
      (1 to 3).map(_ => createImageData.toIdentified).toList
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "images": [${workImageIncludes(work.data.imageData)}],
      | "availabilities": [${availabilities(work.state.availabilities)}],
      | "identifiers": ${sourceIdentifiers(work.identifiers)},
      | "alternativeTitles": [],
      | "contributors": [],
      | "genres": [],
      | "holdings": [],
      | "items": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson)
  }

  it("shows the thumbnail field if available") {
    val work = indexedWork().thumbnail(
      DigitalLocation(
        locationType = LocationType.ThumbnailImage,
        url = "https://iiif.example.org/1234/default.jpg",
        license = Some(License.CCBY)
      )
    )

    val expectedJson = s"""
      |{
      | "type": "Work",
      | "id": "${work.state.canonicalId}",
      | "title": "${work.data.title.get}",
      | "thumbnail": ${location(work.data.thumbnail.get)},
      | "availabilities": [${availabilities(work.state.availabilities)}],
      | "identifiers": ${sourceIdentifiers(work.identifiers)},
      | "alternativeTitles": [],
      | "contributors": [],
      | "genres": [],
      | "holdings": [],
      | "images": [],
      | "items": [],
      | "languages": [],
      | "notes": [],
      | "parts": [],
      | "partOf": [],
      | "precededBy": [],
      | "production": [],
      | "subjects": [],
      | "succeededBy": []
      |}
    """.stripMargin

    assertWorkMapsToJson(work, expectedJson)
  }
}

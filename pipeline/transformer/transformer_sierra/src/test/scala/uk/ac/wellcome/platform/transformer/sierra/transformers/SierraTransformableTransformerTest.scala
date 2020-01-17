package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.models.transformable.sierra.{
  SierraBibNumber,
  SierraBibRecord,
  SierraItemRecord
}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.SierraTransformableTransformer
import uk.ac.wellcome.platform.transformer.sierra.exceptions.SierraTransformerException
import uk.ac.wellcome.platform.transformer.sierra.generators.MarcGenerators
import uk.ac.wellcome.platform.transformer.sierra.source.MarcSubfield
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal.WorkType.{Books, Pictures}

class SierraTransformableTransformerTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraGenerators
    with SierraTransformableTestBase
    with WorksGenerators {

  it("performs a transformation on a work with physical items") {
    val itemRecords = List(
      createSierraItemRecord,
      createSierraItemRecord
    )

    val sierraTransformable = createSierraTransformableWith(
      maybeBibRecord = Some(createSierraBibRecord),
      itemRecords = itemRecords
    )

    val expectedIdentifiers = itemRecords.map { record =>
      createSierraSystemSourceIdentifierWith(
        value = record.id.withCheckDigit,
        ontologyType = "Item"
      )
    }

    val work = transformToWork(sierraTransformable)

    work shouldBe a[UnidentifiedWork]

    val actualIdentifiers = work
      .asInstanceOf[UnidentifiedWork]
      .data
      .items
      .map { _.id.asInstanceOf[Identifiable].sourceIdentifier }

    actualIdentifiers should contain theSameElementsAs expectedIdentifiers
  }

  it("trims whitespace from the materialType code") {
    val id = createSierraBibNumber
    val title = "Hi Diddle Dee Dee"

    val data =
      s"""
         |{
         | "id": "$id",
         | "title": "$title",
         | "materialType": {"code":"k  "}
         |}
        """.stripMargin

    val bibRecord = createSierraBibRecordWith(id = id, data = data)

    val expectedWorkType = Pictures

    val triedWork = SierraTransformableTransformer(
      createSierraTransformableWith(id, Some(bibRecord)),
      1)
    triedWork.isSuccess shouldBe true

    triedWork.get.asInstanceOf[UnidentifiedWork].data.workType shouldBe Some(
      expectedWorkType)
  }

  it("extracts information from items") {
    val bibId = createSierraBibNumber
    val itemId = createSierraItemNumber
    val locationType = LocationType("sgmed")
    val locationLabel = "A museum of mermaids"
    val itemData =
      s"""
         |{
         |  "id": "$itemId",
         |  "location": {
         |    "code": "${locationType.id}",
         |    "name": "$locationLabel"
         |  }
         |}
         |""".stripMargin

    val itemRecord = createSierraItemRecordWith(
      id = itemId,
      data = itemData,
      bibIds = List(bibId)
    )

    val bibRecord = createSierraBibRecordWith(id = bibId)

    val transformable = createSierraTransformableWith(
      sierraId = bibId,
      maybeBibRecord = Some(bibRecord),
      itemRecords = List(itemRecord)
    )

    val work = transformToWork(transformable)
    work shouldBe a[UnidentifiedWork]
    val unidentifiedWork = work.asInstanceOf[UnidentifiedWork]
    unidentifiedWork.data.items should have size 1

    val expectedSourceIdentifier = createSierraSystemSourceIdentifierWith(
      value = itemId.withCheckDigit,
      ontologyType = "Item"
    )

    val expectedOtherIdentifiers = List(
      createSierraIdentifierSourceIdentifierWith(
        value = itemId.withoutCheckDigit,
        ontologyType = "Item"
      )
    )

    unidentifiedWork.data.items.head shouldBe Item(
      id = Identifiable(
        sourceIdentifier = expectedSourceIdentifier,
        otherIdentifiers = expectedOtherIdentifiers),
      locations = List(PhysicalLocation(locationType, locationLabel))
    )
  }

  it("extracts title from items") {
    val bibId = createSierraBibNumber
    val itemId = createSierraItemNumber
    val locationType = LocationType("sgmed")
    val locationLabel = "A museum of mermaids"
    val itemData =
      s"""
         |{
         |  "id": "$itemId",
         |  "location": {
         |    "code": "${locationType.id}",
         |    "name": "$locationLabel"
         |  },
         |  "varFields": [
         |    {
         |        "fieldTag": "v",
         |        "content": "Envelope"
         |    }
         |]
         |}
         |""".stripMargin

    val itemRecord = createSierraItemRecordWith(
      id = itemId,
      data = itemData,
      bibIds = List(bibId)
    )

    val bibRecord = createSierraBibRecordWith(id = bibId)

    val transformable = createSierraTransformableWith(
      sierraId = bibId,
      maybeBibRecord = Some(bibRecord),
      itemRecords = List(itemRecord)
    )

    val unidentifiedWork =
      transformToWork(transformable).asInstanceOf[UnidentifiedWork]

    unidentifiedWork.data.items.head.title shouldBe Some("Envelope")
  }

  it("returns an InvisibleWork if there isn't any bib data") {
    assertTransformReturnsInvisibleWork(
      maybeBibRecord = None
    )
  }

  it(
    "does not perform a transformation without bibData, even if some itemData is present") {
    assertTransformReturnsInvisibleWork(
      maybeBibRecord = None,
      itemRecords = List(createSierraItemRecord)
    )
  }

  it("performs a transformation on a work using all varfields") {
    val id = createSierraBibNumber
    val title = "Hi Diddle Dee Dee"
    val lettering = "An actor's life for me"

    val productionField = createVarFieldWith(
      marcTag = "260",
      subfields = List(
        MarcSubfield(tag = "b", content = "Peaceful Poetry"),
        MarcSubfield(tag = "c", content = "1923")
      )
    )

    val descriptionField = createVarFieldWith(
      marcTag = "520",
      subfields = List(
        MarcSubfield(
          tag = "a",
          content = "A delightful description of a dead daisy."),
        MarcSubfield(tag = "c", content = "1923")
      )
    )

    val letteringField = createVarFieldWith(
      marcTag = "246",
      indicator2 = "6",
      subfields = List(
        MarcSubfield(tag = "a", content = lettering)
      )
    )

    val notesField = createVarFieldWith(
      marcTag = "500",
      subfields = List(
        MarcSubfield(tag = "a", content = "It's a note")
      )
    )

    val marcFields =
      List(productionField, descriptionField, letteringField, notesField)

    val data =
      s"""
         |{
         | "id": "$id",
         | "title": "$title",
         | "varFields": ${toJson(marcFields).get}
         |}
        """.stripMargin

    val sourceIdentifier = createSierraSystemSourceIdentifierWith(
      value = id.withCheckDigit
    )
    val sierraIdentifier = createSierraIdentifierSourceIdentifierWith(
      value = id.withoutCheckDigit
    )

    val work = transformDataToWork(id = id, data = data)

    work shouldBe createUnidentifiedWorkWith(
      title = Some(title),
      sourceIdentifier = sourceIdentifier,
      otherIdentifiers = List(sierraIdentifier),
      description = Some("A delightful description of a dead daisy."),
      production = List(
        ProductionEvent(
          label = "Peaceful Poetry 1923",
          places = List(),
          agents = List(Agent(label = "Peaceful Poetry")),
          dates = List(Period("1923")),
          function = None
        )
      ),
      notes = List(GeneralNote("It's a note")),
      lettering = Some(lettering)
    )
  }

  it("makes deleted works invisible") {
    val id = createSierraBibNumber
    val title = "Hi Diddle Dee Dee"
    val data =
      s"""
         |{
         | "id": "$id",
         | "title": "$title",
         | "varFields": [],
         | "deleted": true
         |}
        """.stripMargin

    val work = transformDataToWork(id = id, data = data)
    work shouldBe a[UnidentifiedInvisibleWork]
  }

  it("makes suppressed works invisible") {
    val id = createSierraBibNumber
    val title = "Hi Diddle Dee Dee"
    val data =
      s"""
         |{
         | "id": "$id",
         | "title": "$title",
         | "varFields": [],
         | "suppressed": true
         |}
        """.stripMargin

    val work = transformDataToWork(id = id, data = data)
    work shouldBe a[UnidentifiedInvisibleWork]
  }

  it("transforms bib records that don't have a title") {
    // This example is taken from a failure observed in the transformer,
    // based on real records from Sierra.
    val id = createSierraBibNumber
    val data =
      s"""
         |{
         |  "id": "$id",
         |  "deletedDate": "2017-02-20",
         |  "deleted": true,
         |  "orders": [],
         |  "locations": [],
         |  "fixedFields": {},
         |  "varFields": []
         |}
        """.stripMargin

    val work = transformDataToWork(id = id, data = data)
    work shouldBe a[UnidentifiedInvisibleWork]
  }

  it("includes the physical description, if present") {
    val id = createSierraBibNumber
    val physicalDescription = "A dusty depiction of dodos"

    val data =
      s"""
        | {
        |   "id": "$id",
        |   "title": "Doddering dinosaurs are daring in dance",
        |   "varFields": [
        |     {
        |       "fieldTag": "b",
        |       "marcTag": "300",
        |       "ind1": " ",
        |       "ind2": " ",
        |       "subfields": [
        |         {
        |           "tag": "b",
        |           "content": "$physicalDescription"
        |         }
        |       ]
        |     }
        |   ]
        | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.physicalDescription shouldBe Some(physicalDescription)
  }

  it("includes the work type, if present") {
    val id = createSierraBibNumber
    val workTypeId = "a"
    val workTypeValue = "Books"

    val expectedWorkType = Books

    val data =
      s"""
         | {
         |   "id": "$id",
         |   "title": "Doddering dinosaurs are daring in dance",
         |    "materialType": {
         |      "code": "$workTypeId",
         |      "value": "$workTypeValue"
         |    },
         |   "varFields": []
         | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.workType shouldBe Some(expectedWorkType)
  }

  it("includes the alternative title, if present") {
    val id = createSierraBibNumber
    val alternativeTitle = "English Earwigs"

    val data =
      s"""
        | {
        |   "id": "$id",
        |   "title": "English earwigs earn evidence of evil",
        |   "varFields": [
        |     {
        |       "fieldTag": "a",
        |       "marcTag": "240",
        |       "ind1": " ",
        |       "ind2": " ",
        |       "subfields": [
        |         {
        |           "tag": "a",
        |           "content": "$alternativeTitle"
        |         }
        |       ]
        |     }
        |   ]
        | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.alternativeTitles shouldBe List(alternativeTitle)
  }

  it("includes the edition, if present") {
    val id = createSierraBibNumber
    val edition = "3rd edition"

    val data =
      s"""
        | {
        |   "id": "$id",
        |   "title": "Title",
        |   "varFields": [
        |     {
        |       "fieldTag": "a",
        |       "marcTag": "250",
        |       "ind1": " ",
        |       "ind2": " ",
        |       "subfields": [
        |         {
        |           "tag": "a",
        |           "content": "$edition"
        |         }
        |       ]
        |     }
        |   ]
        | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.edition shouldBe Some(edition)
  }

  it("uses the full Sierra system number as the source identifier") {
    val id = createSierraBibNumber
    val data = s"""{"id": "$id", "title": "A title"}"""

    val expectedSourceIdentifier = createSierraSystemSourceIdentifierWith(
      value = id.withCheckDigit
    )

    val work = transformDataToWork(id = id, data = data)
    work.sourceIdentifier shouldBe expectedSourceIdentifier
  }

  it("uses the lang for the language field") {
    val id = createSierraBibNumber
    val data =
      s"""{
         |  "id": "$id",
         |  "lang": {
         |    "code": "fra",
         |    "name": "French"
         |  },
         |  "title": "A title"
         |}""".stripMargin

    val expectedLanguage = Language(
      id = "fra",
      label = "French"
    )

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.language.get shouldBe expectedLanguage
  }

  it("extracts contributor information if present") {
    val id = createSierraBibNumber
    val name = "Rincewind"

    val data =
      s"""
          | {
          |   "id": "$id",
          |   "title": "English earwigs earn evidence of evil",
          |   "varFields": [
          |     {
          |       "fieldTag": "",
          |       "marcTag": "100",
          |       "ind1": " ",
          |       "ind2": " ",
          |       "subfields": [
          |         {
          |           "tag": "a",
          |           "content": "$name"
          |         }
          |       ]
          |     }
          |   ]
          | }
       """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.contributors shouldBe List(Contributor(Person(name), roles = Nil))
  }

  it("extracts subjects if present") {
    val id = createSierraBibNumber
    val content = "A content"

    val data =
      s"""
         | {
         |   "id": "$id",
         |   "title": "Dastardly Danish dogs draw dubious doughnuts",
         |   "varFields": [
         |     {
         |       "fieldTag": "",
         |       "marcTag": "650",
         |       "ind1": " ",
         |       "ind2": " ",
         |       "subfields": [
         |         {
         |           "tag": "a",
         |           "content": "$content"
         |         }
         |       ]
         |     }
         |   ]
         | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.subjects shouldBe List(
      Subject(content, List(Concept(content)))
    )
  }

  it("extracts person subjects if present") {
    val id = createSierraBibNumber
    val content = "Nostradamus"

    val data =
      s"""
         | {
         |   "id": "$id",
         |   "title": "Dastardly Danish dogs draw dubious doughnuts",
         |   "varFields": [
         |     {
         |       "fieldTag": "",
         |       "marcTag": "600",
         |       "ind1": " ",
         |       "ind2": " ",
         |       "subfields": [
         |         {
         |           "tag": "a",
         |           "content": "$content"
         |         }
         |       ]
         |     }
         |   ]
         | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.subjects shouldBe List(
      Subject(content, List(Person(content)))
    )
  }

  it("extracts organisation subjects if present") {
    val id = createSierraBibNumber
    val content = "ACME CORP"

    val data =
      s"""
         | {
         |   "id": "$id",
         |   "title": "Wacky Racers",
         |   "varFields": [
         |     {
         |       "fieldTag": "",
         |       "marcTag": "610",
         |       "ind1": " ",
         |       "ind2": " ",
         |       "subfields": [
         |         {
         |           "tag": "a",
         |           "content": "$content"
         |         }
         |       ]
         |     }
         |   ]
         | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.subjects shouldBe List(
      Subject(
        label = content,
        concepts = List(Organisation(content))
      )
    )
  }

  it("extracts meeting subjects if present") {
    val id = createSierraBibNumber
    val content = "Big Meeting"

    val data =
      s"""
         | {
         |   "id": "$id",
         |   "title": "Proceedings of 3rd Big Meeting",
         |   "varFields": [
         |     {
         |       "fieldTag": "",
         |       "marcTag": "611",
         |       "ind1": " ",
         |       "ind2": " ",
         |       "subfields": [
         |         {
         |           "tag": "a",
         |           "content": "$content"
         |         }
         |       ]
         |     }
         |   ]
         | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.subjects shouldBe List(
      Subject(
        label = content,
        concepts = List(Meeting(content))
      )
    )
  }

  it("extracts brand name subjects if present") {
    val id = createSierraBibNumber
    val content = "ACME"

    val data =
      s"""
         | {
         |   "id": "$id",
         |   "title": "Wacky Racers",
         |   "varFields": [
         |     {
         |       "fieldTag": "",
         |       "marcTag": "652",
         |       "ind1": " ",
         |       "ind2": " ",
         |       "subfields": [
         |         {
         |           "tag": "a",
         |           "content": "$content"
         |         }
         |       ]
         |     }
         |   ]
         | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.subjects shouldBe List(
      Subject(
        label = content,
        concepts  = List(Concept(content))
      )
    )
  }

  it("adds production events if possible") {
    val id = createSierraBibNumber
    val placeLabel = "London"

    val data =
      s"""
         | {
         |   "id": "$id",
         |   "title": "Loosely lamenting the lemons of London",
         |   "varFields": [
         |     {
         |       "fieldTag": "",
         |       "marcTag": "260",
         |       "ind1": " ",
         |       "ind2": " ",
         |       "subfields": [
         |         {
         |           "tag": "a",
         |           "content": "$placeLabel"
         |         }
         |       ]
         |     }
         |   ]
         | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.production shouldBe List(
      ProductionEvent(
        label = placeLabel,
        places = List(Place(placeLabel)),
        agents = List(),
        dates = List(),
        function = None
      )
    )
  }

  it("extracts merge candidates from 776 subfield $$w") {
    val id = createSierraBibNumber
    val mergeCandidateBibNumber = "b21414440"
    val data =
      s"""
         | {
         |   "id": "$id",
         |   "title": "Loosely lamenting the lemons of London",
         |   "varFields": [
         |     {
         |       "fieldTag": "",
         |       "marcTag": "776",
         |       "ind1": " ",
         |       "ind2": " ",
         |       "subfields": [
         |         {
         |           "tag": "w",
         |           "content": "(UkLW)$mergeCandidateBibNumber"
         |         }
         |       ]
         |     }
         |   ]
         | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.mergeCandidates shouldBe List(
      MergeCandidate(
        identifier = createSierraSystemSourceIdentifierWith(
          value = mergeCandidateBibNumber
        ),
        reason = Some("Physical/digitised Sierra work")
      )
    )
  }

  it("extracts merge candidates from 962 subfield $$u if material type is k") {
    val id = createSierraBibNumber
    val miroId = "V0021476"
    val data =
      s"""
         | {
         |   "id": "$id",
         |   "title": "Loosely lamenting the lemons of London",
         |   "varFields": [
         |     {
         |       "fieldTag": "",
         |       "marcTag": "962",
         |       "ind1": " ",
         |       "ind2": " ",
         |       "subfields": [
         |         {
         |           "tag": "u",
         |           "content": "http://wellcomeimages.org/indexplus/image/$miroId.html"
         |         }
         |       ]
         |     }
         |   ],
         |   "materialType": {"code": "k"}
         | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.mergeCandidates shouldBe List(
      MergeCandidate(
        identifier = createMiroSourceIdentifierWith(value = miroId),
        reason = Some("Single page Miro/Sierra work")
      )
    )
  }

  it(
    "extracts merge candidates from 962 subfield $$u if material type is k followed by spaces") {
    val id = createSierraBibNumber
    val miroId = "V0021476"
    val data =
      s"""
         | {
         |   "id": "$id",
         |   "title": "Loosely lamenting the lemons of London",
         |   "varFields": [
         |     {
         |       "fieldTag": "",
         |       "marcTag": "962",
         |       "ind1": " ",
         |       "ind2": " ",
         |       "subfields": [
         |         {
         |           "tag": "u",
         |           "content": "http://wellcomeimages.org/indexplus/image/$miroId.html"
         |         }
         |       ]
         |     }
         |   ],
         |   "materialType": {"code": "k  "}
         | }
      """.stripMargin

    val work = transformDataToUnidentifiedWork(id = id, data = data)
    work.data.mergeCandidates shouldBe List(
      MergeCandidate(
        identifier = createMiroSourceIdentifierWith(value = miroId),
        reason = Some("Single page Miro/Sierra work")
      )
    )
  }

  it("returns an InvisibleWork if bibData has no title") {
    val id = createSierraBibNumber
    val bibData =
      s"""
        |{
        | "id": "$id",
        | "deleted": false,
        | "suppressed": false
        |}
      """.stripMargin
    val bibRecord = createSierraBibRecordWith(
      id = id,
      data = bibData
    )

    assertTransformReturnsInvisibleWork(
      maybeBibRecord = Some(bibRecord)
    )
  }

  // This is based on a real failure -- our initial implementation of
  // workType for Sierra was unable to find these workTypes.
  //
  it("finds the WorkType if the materialType field only contains a code") {
    val id = createSierraBibNumber
    val bibData =
      s"""
         |{
         |  "id": "$id",
         |  "title": "${randomAlphanumeric(50)}",
         |  "materialType": {
         |    "code": "k  "
         |  }
         |}
       """.stripMargin

    val work = transformDataToWork(id = id, data = bibData)
    work shouldBe a[UnidentifiedWork]
    work.asInstanceOf[UnidentifiedWork].data.workType shouldBe Some(
      Pictures
    )
  }

  describe("throws a TransformerException when passed invalid data") {
    it("an item record") {
      val bibRecord = createSierraBibRecord
      val transformable = SierraTransformable(
        sierraId = bibRecord.id,
        maybeBibRecord = Some(bibRecord),
        itemRecords = Map(
          createSierraItemNumber -> createSierraItemRecordWith(
            data = "Not valid JSON")
        )
      )

      val result = SierraTransformableTransformer(transformable, version = 1)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[SierraTransformerException]
      result.failed.get
        .asInstanceOf[SierraTransformerException]
        .e
        .getMessage should include("Unable to parse item data")
    }

    it("one of several item records") {
      val bibRecord = createSierraBibRecord
      val transformable = SierraTransformable(
        sierraId = bibRecord.id,
        maybeBibRecord = Some(bibRecord),
        itemRecords = Map(
          createSierraItemNumber -> createSierraItemRecord,
          createSierraItemNumber -> createSierraItemRecordWith(
            data = "Not valid JSON"),
          createSierraItemNumber -> createSierraItemRecord
        )
      )

      val result = SierraTransformableTransformer(transformable, version = 1)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[SierraTransformerException]
      result.failed.get
        .asInstanceOf[SierraTransformerException]
        .e
        .getMessage should include("Unable to parse item data")
    }

    it("the bib record") {
      val bibRecord = createSierraBibRecordWith(
        data = "Not a valid JSON string"
      )
      val transformable = SierraTransformable(
        bibRecord = bibRecord
      )

      val result = SierraTransformableTransformer(transformable, version = 1)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[SierraTransformerException]
      result.failed.get
        .asInstanceOf[SierraTransformerException]
        .e
        .getMessage should include("Unable to parse bib data")
    }
  }

  private def transformDataToWork(id: SierraBibNumber,
                                  data: String): TransformedBaseWork = {
    val bibRecord = createSierraBibRecordWith(
      id = id,
      data = data
    )

    val sierraTransformable = SierraTransformable(
      bibRecord = bibRecord
    )

    transformToWork(sierraTransformable)
  }

  private def assertTransformReturnsInvisibleWork(
    maybeBibRecord: Option[SierraBibRecord],
    itemRecords: List[SierraItemRecord] = List()) = {
    val id = createSierraBibNumber

    val sierraTransformable = createSierraTransformableWith(
      sierraId = id,
      maybeBibRecord = maybeBibRecord,
      itemRecords = itemRecords
    )

    val triedMaybeWork =
      SierraTransformableTransformer(sierraTransformable, version = 1)
    triedMaybeWork.isSuccess shouldBe true

    triedMaybeWork.get shouldBe UnidentifiedInvisibleWork(
      sourceIdentifier = createSierraSystemSourceIdentifierWith(
        value = id.withCheckDigit
      ),
      version = 1,
      data = WorkData()
    )
  }

  private def transformDataToUnidentifiedWork(
    id: SierraBibNumber,
    data: String): UnidentifiedWork = {

    val work = transformDataToWork(id = id, data = data)
    work shouldBe a[UnidentifiedWork]
    work.asInstanceOf[UnidentifiedWork]
  }
}

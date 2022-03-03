package weco.pipeline.transformer.sierra.transformers

import java.time.Instant
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.WorkState.Source
import org.scalatest.Assertion
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  ReferenceNumber,
  SourceIdentifier
}
import weco.catalogue.internal_model.identifiers.IdState.Unidentifiable
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.locations.LocationType.{
  ClosedStores,
  OnlineResource
}
import weco.catalogue.internal_model.work.DeletedReason.{
  DeletedFromSource,
  SuppressedFromSource
}
import weco.catalogue.internal_model.work.Format.{Books, Pictures}
import weco.catalogue.internal_model.work.InvisibilityReason.{
  SourceFieldMissing,
  UnableToTransform
}
import weco.catalogue.internal_model.work._
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.catalogue.source_model.sierra._
import weco.json.JsonUtil._
import weco.pipeline.transformer.sierra.SierraTransformer
import weco.pipeline.transformer.sierra.exceptions.SierraTransformerException
import weco.pipeline.transformer.transformers.ParsedPeriod
import weco.sierra.generators.MarcGenerators
import weco.sierra.models.identifiers.{SierraBibNumber, SierraItemNumber}
import weco.sierra.models.marc.{Subfield, VarField}

class SierraTransformerTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraRecordGenerators
    with SierraTransformableTestBase
    with WorkGenerators {

  it("transforms a work with physical items") {
    val bibId = createSierraBibNumber
    val itemRecords = List(
      createSierraItemRecordWith(bibIds = List(bibId)),
      createSierraItemRecordWith(bibIds = List(bibId))
    )

    val sierraTransformable = createSierraTransformableWith(
      bibRecord = createSierraBibRecordWith(id = bibId),
      itemRecords = itemRecords
    )

    val expectedIdentifiers = itemRecords.map { record =>
      createSierraSystemSourceIdentifierWith(
        value = record.id.withCheckDigit,
        ontologyType = "Item"
      )
    }

    val work = transformToWork(sierraTransformable)

    work shouldBe a[Work.Visible[_]]

    val actualIdentifiers = work
      .asInstanceOf[Work.Visible[_]]
      .data
      .items
      .map { _.id.asInstanceOf[IdState.Identifiable].sourceIdentifier }

    actualIdentifiers should contain theSameElementsAs expectedIdentifiers
  }
  it("transforms a work with empty code in the lang field") {
    val bibId = createSierraBibNumber
    val data =
      s"""
         |{
         |  "id": "$bibId",
         |  "lang": {
         |    "code": " "
         |  }
         |}
         |""".stripMargin
    val sierraTransformable = createSierraTransformableWith(
      bibRecord = createSierraBibRecordWith(id = bibId, data = data),
    )

    val work = transformToWork(sierraTransformable)

    work
      .asInstanceOf[Work.Invisible[_]]
      .data
      .languages shouldBe empty
  }

  it("trims whitespace from the materialType code") {
    val id = createSierraBibNumber

    val data =
      s"""
         |{
         | "id": "$id",
         | "materialType": {"code":"k  "},
         | "varFields": [${createTitleVarfield()}]
         |}
        """.stripMargin

    val bibRecord = createSierraBibRecordWith(id = id, data = data)

    val triedWork =
      SierraTransformer(
        transformable = createSierraTransformableWith(bibRecord = bibRecord),
        version = 1)
    triedWork.isSuccess shouldBe true

    triedWork.get.asInstanceOf[Work.Visible[_]].data.format shouldBe Some(
      Pictures)
  }

  it("extracts information from items") {
    val bibId = createSierraBibNumber
    val itemId = createSierraItemNumber

    def itemData(itemId: SierraItemNumber,
                 modifiedDate: Instant,
                 bibIds: List[SierraBibNumber]) =
      s"""
         |{
         |  "id": "$itemId",
         |  "location": {
         |    "code": "sgmed",
         |    "name": "Closed stores Med."
         |  },
         |  "fixedFields": {
         |    "88": {
         |      "label": "STATUS",
         |      "value": "-",
         |      "display": "Available"
         |    },
         |    "108": {
         |      "label": "OPACMSG",
         |      "value": "f",
         |      "display": "Online request"
         |    }
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
      bibRecord = bibRecord,
      itemRecords = List(itemRecord)
    )

    val work = transformToWork(transformable)
    work shouldBe a[Work.Visible[_]]
    val unidentifiedWork = work.asInstanceOf[Work.Visible[_]]
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
      id = IdState.Identifiable(
        sourceIdentifier = expectedSourceIdentifier,
        otherIdentifiers = expectedOtherIdentifiers),
      locations = List(
        PhysicalLocation(
          locationType = LocationType.ClosedStores,
          label = LocationType.ClosedStores.label,
          accessConditions = List(
            AccessCondition(
              method = AccessMethod.OnlineRequest,
              status = AccessStatus.Open))
        )
      )
    )
  }

  it("extracts title from items") {
    val bibId = createSierraBibNumber
    val itemId = createSierraItemNumber
    val locationType = LocationType.ClosedStores
    val locationLabel = "A museum of mermaids"
    def itemData(itemId: SierraItemNumber,
                 modifiedDate: Instant,
                 bibIds: List[SierraBibNumber]) =
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
      bibRecord = bibRecord,
      itemRecords = List(itemRecord)
    )

    val unidentifiedWork =
      transformToWork(transformable).asInstanceOf[Work.Visible[Source]]

    unidentifiedWork.data.items.head.title shouldBe Some("Envelope")
  }

  it("puts items in the right order") {
    val bibId = SierraBibNumber("1000024")

    def itemData(itemId: SierraItemNumber,
                 modifiedDate: Instant,
                 bibIds: List[SierraBibNumber]) =
      s"""
         |{
         |  "id": "$itemId",
         |  "location": {
         |    "code": "sgmed",
         |    "name": "Closed stores Med."
         |  },
         |  "fixedFields": {
         |    "88": {
         |      "label": "STATUS",
         |      "value": "-",
         |      "display": "Available"
         |    },
         |    "108": {
         |      "label": "OPACMSG",
         |      "value": "f",
         |      "display": "Online request"
         |    }
         |  }
         |}
         |""".stripMargin

    val itemRecords = Seq("1874354", "1874355", "1000031", "1874353").map {
      itemNumber =>
        createSierraItemRecordWith(
          id = SierraItemNumber(itemNumber),
          data = itemData,
          bibIds = List(bibId)
        )
    }

    val bibRecord = createSierraBibRecordWith(id = bibId)

    val transformable = createSierraTransformableWith(
      bibRecord = bibRecord,
      itemRecords = itemRecords
    )

    val work = transformToWork(transformable)
    work shouldBe a[Work.Visible[_]]
    val unidentifiedWork = work.asInstanceOf[Work.Visible[_]]

    val identifiers = unidentifiedWork.data.items
      .collect {
        case Item(IdState.Identifiable(sourceIdentifier, _, _), _, _, _) =>
          sourceIdentifier
      }

    identifiers shouldBe Seq("i18743547", "i18743559", "i10000318", "i18743535")
      .map { id =>
        SourceIdentifier(
          identifierType = IdentifierType.SierraSystemNumber,
          value = id,
          ontologyType = "Item"
        )
      }
  }

  it("returns an InvisibleWork if there isn't any bib data") {
    val bibId = createSierraBibNumber
    val transformable = createSierraTransformableStubWith(
      bibId = bibId,
      itemRecords = List(createSierraItemRecordWith(bibIds = List(bibId)))
    )

    assertTransformReturnsInvisibleWork(
      transformable,
      modifiedDate = Instant.EPOCH,
      invisibilityReasons = List(SourceFieldMissing("bibRecord"))
    )
  }

  it("transforms a work using all varfields") {
    val id = createSierraBibNumber
    val title = "Hi Diddle Dee Dee"
    val lettering = "An actor's life for me"

    val titleField = VarField(
      marcTag = "245",
      subfields = List(
        Subfield(tag = "a", content = title)
      )
    )

    val productionField = VarField(
      marcTag = "260",
      subfields = List(
        Subfield(tag = "b", content = "Peaceful Poetry"),
        Subfield(tag = "c", content = "1923")
      )
    )

    val descriptionField = VarField(
      marcTag = "520",
      subfields = List(
        Subfield(
          tag = "a",
          content = "A delightful description of a dead daisy."),
        Subfield(tag = "c", content = "1923")
      )
    )

    val letteringField = createVarFieldWith(
      marcTag = "246",
      indicator2 = "6",
      subfields = List(
        Subfield(tag = "a", content = lettering)
      )
    )

    val notesField = VarField(
      marcTag = "500",
      subfields = List(
        Subfield(tag = "a", content = "It's a note")
      )
    )

    val langField = """{"code": "eng", "name": "English"}"""
    val langVarFields = List(
      VarField(
        marcTag = "041",
        subfields = List(
          Subfield(tag = "a", content = "ger"),
          Subfield(tag = "a", content = "fre"),
        )
      )
    )

    val expectedLanguages = List(
      Language(label = "English", id = "eng"),
      Language(label = "German", id = "ger"),
      Language(label = "French", id = "fre")
    )

    val marcFields =
      List(
        titleField,
        productionField,
        descriptionField,
        letteringField,
        notesField) ++ langVarFields

    val data =
      s"""
         |{
         | "id": "$id",
         | "varFields": ${toJson(marcFields).get},
         | "lang": $langField
         |}
        """.stripMargin

    val sourceIdentifier = createSierraSystemSourceIdentifierWith(
      value = id.withCheckDigit
    )
    val sierraIdentifier = createSierraIdentifierSourceIdentifierWith(
      value = id.withoutCheckDigit
    )

    val work = transformDataToWork(id = id, data = data, now)

    work shouldBe sourceWork(sourceIdentifier, now)
      .withVersion(1)
      .title(title)
      .otherIdentifiers(List(sierraIdentifier))
      .description("<p>A delightful description of a dead daisy.</p>")
      .production(
        List(
          ProductionEvent(
            label = "Peaceful Poetry 1923",
            places = List(),
            agents = List(Agent(label = "Peaceful Poetry")),
            dates = List(ParsedPeriod("1923")),
            function = None
          )
        )
      )
      .notes(List(
        Note(contents = "It's a note", noteType = NoteType.GeneralNote)))
      .lettering(lettering)
      .languages(expectedLanguages)
  }

  it("deletes works with 'deleted': true") {
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

    work shouldBe a[Work.Deleted[_]]
    val deletedWork = work.asInstanceOf[Work.Deleted[_]]
    deletedWork.deletedReason shouldBe DeletedFromSource("Sierra")
  }

  it("suppresses works with 'suppressed': true") {
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

    work shouldBe a[Work.Deleted[_]]
    val deletedWork = work.asInstanceOf[Work.Deleted[_]]
    deletedWork.deletedReason shouldBe SuppressedFromSource("Sierra")
  }

  it("transforms bib records that don't have a title") {
    // This example is taken from a failure observed in the transformer,
    // based on real records from Sierra.
    val id = createSierraBibNumber
    val data =
      s"""
         |{
         |  "id": "$id",
         |  "deleted": false,
         |  "orders": [],
         |  "locations": [],
         |  "fixedFields": {},
         |  "varFields": []
         |}
        """.stripMargin

    val work = transformDataToWork(id = id, data = data)
    work shouldBe a[Work.Invisible[_]]
  }

  it("includes the physical description, if present") {
    val id = createSierraBibNumber
    val physicalDescription = "A dusty depiction of dodos"

    val data =
      s"""
        | {
        |   "id": "$id",
        |   "varFields": [
        |     ${createTitleVarfield()},
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

    val work = transformDataToSourceWork(id = id, data = data)
    work.data.physicalDescription shouldBe Some(physicalDescription)
  }

  it("includes the work type, if present") {
    val id = createSierraBibNumber
    val formatId = "a"
    val formatValue = "Books"

    val data =
      s"""
         | {
         |   "id": "$id",
         |    "materialType": {
         |      "code": "$formatId",
         |      "value": "$formatValue"
         |    },
         |   "varFields": [${createTitleVarfield()}]
         | }
      """.stripMargin

    val work = transformDataToSourceWork(id = id, data = data)
    work.data.format shouldBe Some(Books)
  }

  it("includes the alternative title, if present") {
    val id = createSierraBibNumber
    val alternativeTitle = "English Earwigs"

    val data =
      s"""
        | {
        |   "id": "$id",
        |   "varFields": [
        |     ${createTitleVarfield()},
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

    val work = transformDataToSourceWork(id = id, data = data)
    work.data.alternativeTitles shouldBe List(alternativeTitle)
  }

  it("includes the edition, if present") {
    val id = createSierraBibNumber
    val edition = "3rd edition"

    val data =
      s"""
        | {
        |   "id": "$id",
        |   "varFields": [
        |     ${createTitleVarfield()},
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

    val work = transformDataToSourceWork(id = id, data = data)
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
         |  "varFields": [${createTitleVarfield()}],
         |  "lang": {
         |    "code": "fra",
         |    "name": "French"
         |  }
         |}""".stripMargin

    val work = transformDataToSourceWork(id = id, data = data)
    work.data.languages shouldBe Seq(Language(id = "fra", label = "French"))
  }

  it("extracts contributor information if present") {
    val id = createSierraBibNumber
    val name = "Rincewind"

    val data =
      s"""
          | {
          |   "id": "$id",
          |   "varFields": [
          |     ${createTitleVarfield()},
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

    val work = transformDataToSourceWork(id = id, data = data)
    work.data.contributors shouldBe List(Contributor(Person(name), roles = Nil))
  }

  it("extracts subjects if present") {
    val id = createSierraBibNumber
    val content = "A content"

    val data =
      s"""
         | {
         |   "id": "$id",
         |   "varFields": [
         |     ${createTitleVarfield()},
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

    val work = transformDataToSourceWork(id = id, data = data)
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
         |   "varFields": [
         |     ${createTitleVarfield()},
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

    val work = transformDataToSourceWork(id = id, data = data)
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
         |   "varFields": [
         |     ${createTitleVarfield()},
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

    val work = transformDataToSourceWork(id = id, data = data)
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
         |   "varFields": [
         |     ${createTitleVarfield()},
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

    val work = transformDataToSourceWork(id = id, data = data)
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
         |   "varFields": [
         |     ${createTitleVarfield()},
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

    val work = transformDataToSourceWork(id = id, data = data)
    work.data.subjects shouldBe List(
      Subject(
        label = content,
        concepts = List(Concept(content))
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
         |   "varFields": [
         |     ${createTitleVarfield()},
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

    val work = transformDataToSourceWork(id = id, data = data)
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
         |   "varFields": [
         |     ${createTitleVarfield()},
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

    val work = transformDataToSourceWork(id = id, data = data)
    work.state.mergeCandidates shouldBe List(
      MergeCandidate(
        identifier = createSierraSystemSourceIdentifierWith(
          value = mergeCandidateBibNumber
        ),
        reason = "Physical/digitised Sierra work"
      )
    )
  }

  it("extracts merge candidates from 962 subfield $$u") {
    val id = createSierraBibNumber
    val miroId = "V0021476"
    val data =
      s"""
         | {
         |   "id": "$id",
         |   "varFields": [
         |     ${createTitleVarfield()},
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
         |   ]
         | }
      """.stripMargin

    val work = transformDataToSourceWork(id = id, data = data)
    work.state.mergeCandidates shouldBe List(
      MergeCandidate(
        identifier = createMiroSourceIdentifierWith(value = miroId),
        reason = "Miro/Sierra work"
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

    val transformable = createSierraTransformableWith(bibRecord = bibRecord)

    assertTransformReturnsInvisibleWork(
      transformable,
      modifiedDate = bibRecord.modifiedDate,
      invisibilityReasons =
        List(UnableToTransform("Could not find field 245 to create title"))
    )
  }

  // This is based on a real failure -- our initial implementation of
  // format for Sierra was unable to find these formats.
  //
  it("finds the Format if the materialType field only contains a code") {
    val id = createSierraBibNumber
    val bibData =
      s"""
         |{
         |  "id": "$id",
         |  "varFields": [${createTitleVarfield()}],
         |  "materialType": {
         |    "code": "k  "
         |  }
         |}
       """.stripMargin

    val work = transformDataToWork(id = id, data = bibData)
    work shouldBe a[Work.Visible[_]]
    work.asInstanceOf[Work.Visible[Source]].data.format shouldBe Some(
      Pictures
    )
  }

  it("creates an item from field 856") {
    val id = createSierraBibNumber
    val data =
      s"""
         |{
         |  "id": "$id",
         |  "varFields": [
         |    ${createTitleVarfield()},
         |    {
         |      "marcTag": "856",
         |      "subfields": [
         |        {"tag": "u", "content": "https://example.org/journal"},
         |        {"tag": "z", "content": "View this journal"}
         |      ]
         |    }
         |  ]
         |}
       """.stripMargin

    val sierraTransformable = createSierraTransformableWith(
      bibRecord = createSierraBibRecordWith(id = id, data = data)
    )

    val work = transformToWork(sierraTransformable)

    val items = work.data.items

    items should contain(
      Item(
        title = None,
        locations = List(
          DigitalLocation(
            url = "https://example.org/journal",
            linkText = Some("View this journal"),
            locationType = OnlineResource,
            accessConditions = List(
              AccessCondition(
                method = AccessMethod.ViewOnline,
                status = AccessStatus.LicensedResources())
            )
          )
        )
      ))
  }

  describe("includes the holdings") {
    it("includes the holdings") {
      val holdingsId = createSierraHoldingsNumber
      val holdingsData =
        s"""
           |{
           |  "id": "$holdingsId",
           |  "fixedFields": {
           |    "40": {"label": "LOCATION", "value": "stax "}
           |  },
           |  "varFields": [
           |    {
           |      "marcTag": "866",
           |      "subfields": [
           |        {"tag": "a", "content": "A Jubilant Journal"}
           |      ]
           |    }
           |  ]
           |}
       """.stripMargin

      val bibId = createSierraBibNumber

      val transformable = createSierraTransformableWith(
        bibRecord = createSierraBibRecordWith(id = bibId),
        holdingsRecords = List(
          SierraHoldingsRecord(
            id = holdingsId,
            data = holdingsData,
            modifiedDate = Instant.now(),
            bibIds = List(bibId),
            unlinkedBibIds = List()
          )
        )
      )

      val work = transformToWork(transformable)

      work.data.holdings shouldBe List(
        Holdings(
          note = None,
          enumeration = List("A Jubilant Journal"),
          location = Some(
            PhysicalLocation(
              locationType = ClosedStores,
              label = ClosedStores.label
            )
          )
        )
      )
    }
  }

  it("includes items based on the order records") {
    val orderId = createSierraOrderNumber
    val orderData =
      s"""
         |{
         |  "id": "$orderId",
         |  "fixedFields": {
         |    "5": {"label": "COPIES", "value": "1"},
         |    "13": {"label": "ODATE", "value": "2001-01-01"},
         |    "20": {"label": "STATUS", "value": "o"}
         |  }
         |}
       """.stripMargin

    val bibId = createSierraBibNumber

    val transformable = createSierraTransformableWith(
      bibRecord = createSierraBibRecordWith(id = bibId),
      orderRecords = List(
        SierraOrderRecord(
          id = orderId,
          data = orderData,
          modifiedDate = Instant.now(),
          bibIds = List(bibId)
        )
      )
    )

    val work = transformToWork(transformable)

    work.data.items shouldBe List(
      Item(
        id = Unidentifiable,
        title = None,
        locations = List(
          PhysicalLocation(
            locationType = LocationType.OnOrder,
            label = "Ordered for Wellcome Collection on 1 January 2001"
          )
        )
      )
    )
  }

  // This test is based on a real failure, when we were adding an item for
  // b32496485.  This Work didn't have any items in Sierra, but it did get
  // a digitised item from the METS.
  it(
    "does not create an item from the order record if there are no items but there is a CAT DATE on the bib") {
    val id = createSierraBibNumber

    val transformable = createSierraTransformableWith(
      bibRecord = createSierraBibRecordWith(
        id = id,
        data = s"""
               |{
               |  "id": "$id",
               |  "fixedFields": {
               |    "28": {"label": "CAT DATE", "value": "2021-05-17"}
               |  }
               |}
               |""".stripMargin
      ),
      orderRecords = List(createSierraOrderRecordWith(bibIds = List(id)))
    )

    val work = transformToWork(transformable)
    work.data.items shouldBe empty
  }

  it("suppresses the shelfmark from 949 ǂa if there's an iconographic number") {
    val bibId = createSierraBibNumber
    val bibData =
      s"""
         |{
         |  "id": "$bibId",
         |  "materialType": {"code": "k", "label": "Pictures"},
         |  "varFields": [
         |    ${createTitleVarfield()},
         |    {
         |      "marcTag": "001",
         |      "content": "12345i"
         |    }
         |  ]
         |}
       """.stripMargin

    val itemId = createSierraItemNumber
    val itemData =
      s"""
         |{
         |  "id": "$itemId",
         |  "location": {
         |    "code": "sgicon",
         |    "name": "Closed stores Iconographic"
         |  },
         |  "varFields": [
         |    {
         |      "marcTag": "949",
         |      "subfields": [
         |        {"tag": "a", "content": "12345i"}
         |      ]
         |    }
         |  ]
         |}
         |""".stripMargin

    val transformable = createSierraTransformableWith(
      bibRecord = SierraBibRecord(
        id = bibId,
        data = bibData,
        modifiedDate = Instant.now()),
      itemRecords = List(
        SierraItemRecord(
          id = itemId,
          data = itemData,
          modifiedDate = Instant.now(),
          bibIds = List(bibId)
        )
      )
    )

    val work = transformToWork(transformable)

    work.data.referenceNumber shouldBe Some(ReferenceNumber("12345i"))
    work.data.collectionPath shouldBe None

    work.data.otherIdentifiers should contain(
      SourceIdentifier(
        identifierType = IdentifierType.IconographicNumber,
        value = "12345i",
        ontologyType = "Work"
      )
    )

    work.data.items should have size 1

    val item = work.data.items.head
    item.locations should have size 1

    val location = item.locations.head.asInstanceOf[PhysicalLocation]
    location.shelfmark shouldBe None
  }

  it("transforms a work with an unrecognised material type") {
    val bibId = createSierraBibNumber
    val bibData =
      s"""
         |{
         |  "id": "$bibId",
         |  "materialType": {"code": "-", "label": "Pictures"},
         |  "varFields": [
         |    ${createTitleVarfield()}
         |  ]
         |}
       """.stripMargin

    val transformable = SierraTransformable(
      bibRecord = SierraBibRecord(
        id = createSierraBibNumber,
        data = bibData,
        modifiedDate = Instant.now()
      )
    )

    val work = transformToWork(transformable)

    work.data.format shouldBe None
  }

  it("extracts parent Series Relations") {
    val id = createSierraBibNumber
    val data =
      s"""{
         |  "id": "$id",
         |  "varFields": [
         |  ${createTitleVarfield()},
         |    {
         |      "marcTag": "440",
         |      "content": "Series via 440"
         |    },
         |    {
         |      "marcTag": "490",
         |      "content": "Series via 490"
         |    },
         |    {
         |      "marcTag": "773",
         |      "content": "Series via 773"
         |    },
         |    {
         |      "marcTag": "830",
         |      "content": "Series via 830"
         |    }
         |  ]
         |}""".stripMargin

    val work = transformDataToSourceWork(id = id, data = data)
    work.state.relations shouldBe Relations(
      ancestors = List(
        SeriesRelation("Series via 440"),
        SeriesRelation("Series via 490"),
        SeriesRelation("Series via 773"),
        SeriesRelation("Series via 830")
      )
    )
  }

  describe("throws a TransformerException when passed invalid data") {
    it("an item record") {
      val bibRecord = createSierraBibRecord
      val transformable = createSierraTransformableWith(
        bibRecord = bibRecord,
        itemRecords = List(
          createSierraItemRecordWith(
            data = (_, _, _) => "Not valid JSON",
            bibIds = List(bibRecord.id)
          )
        )
      )

      val result = SierraTransformer(transformable, version = 1)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[SierraTransformerException]
      result.failed.get
        .asInstanceOf[SierraTransformerException]
        .e
        .getMessage should include("Unable to parse item data")
    }

    it("one of several item records") {
      val bibRecord = createSierraBibRecord
      val transformable = createSierraTransformableWith(
        bibRecord = bibRecord,
        itemRecords = List(
          createSierraItemRecordWith(bibIds = List(bibRecord.id)),
          createSierraItemRecordWith(
            data = (_, _, _) => "Not valid JSON",
            bibIds = List(bibRecord.id)
          ),
          createSierraItemRecordWith(bibIds = List(bibRecord.id))
        )
      )

      val result = SierraTransformer(transformable, version = 1)
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

      val result = SierraTransformer(transformable, version = 1)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[SierraTransformerException]
      result.failed.get
        .asInstanceOf[SierraTransformerException]
        .e
        .getMessage should include("Unable to parse bib data")
    }
  }

  private def transformDataToWork(
    id: SierraBibNumber,
    data: String,
    modifiedDate: Instant = olderDate): Work[Source] = {
    val bibRecord = createSierraBibRecordWith(
      id = id,
      data = data,
      modifiedDate = modifiedDate
    )

    val sierraTransformable = SierraTransformable(
      bibRecord = bibRecord
    )

    transformToWork(sierraTransformable)
  }

  private def assertTransformReturnsInvisibleWork(
    t: SierraTransformable,
    modifiedDate: Instant,
    invisibilityReasons: List[InvisibilityReason]): Assertion = {
    val triedMaybeWork = SierraTransformer(t, version = 1)
    triedMaybeWork.isSuccess shouldBe true

    triedMaybeWork.get shouldBe Work.Invisible[Source](
      state = Source(
        createSierraSystemSourceIdentifierWith(
          value = t.sierraId.withCheckDigit
        ),
        modifiedDate
      ),
      version = 1,
      data = WorkData(),
      invisibilityReasons = invisibilityReasons
    )
  }

  private def transformDataToSourceWork(id: SierraBibNumber,
                                        data: String): Work.Visible[Source] = {

    val work = transformDataToWork(id = id, data = data)
    work shouldBe a[Work.Visible[_]]
    work.asInstanceOf[Work.Visible[Source]]
  }
}

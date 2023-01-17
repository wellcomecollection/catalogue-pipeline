package weco.pipeline.transformer.calm

import org.scalatest.EitherValues

import java.time.LocalDate
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import weco.catalogue.source_model.generators.CalmRecordGenerators
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work.DeletedReason._
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.calm.models.CalmSourceData
import weco.pipeline.transformer.generators.LabelDerivedIdentifiersGenerators

class CalmTransformerTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with CalmRecordGenerators
    with LabelDerivedIdentifiersGenerators {

  val version = 3

  it("transforms to a work") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued",
      "Language" -> "English, with Russian commentary"
    )
    CalmTransformer(record, version) shouldBe Right(
      Work.Visible[Source](
        version = version,
        state = Source(
          SourceIdentifier(
            value = record.id,
            identifierType = IdentifierType.CalmRecordIdentifier,
            ontologyType = "Work"
          ),
          record.retrievedAt
        ),
        data = WorkData[DataState.Unidentified](
          title = Some("abc"),
          format = Some(Format.ArchivesAndManuscripts),
          collectionPath = Some(
            CollectionPath(
              path = "a/b/c",
              label = Some("a.b.c")
            )
          ),
          referenceNumber = Some(ReferenceNumber("a.b.c")),
          otherIdentifiers = List(
            SourceIdentifier(
              value = "a/b/c",
              identifierType = IdentifierType.CalmRefNo,
              ontologyType = "Work"
            ),
            SourceIdentifier(
              value = "a.b.c",
              identifierType = IdentifierType.CalmAltRefNo,
              ontologyType = "Work"
            )
          ),
          items = List(
            Item(
              title = None,
              locations = List(
                PhysicalLocation(
                  locationType = LocationType.ClosedStores,
                  label = "Closed stores",
                  accessConditions = Nil
                )
              )
            )
          ),
          workType = WorkType.Collection,
          languages = List(
            Language(label = "English", id = "eng"),
            Language(label = "Russian", id = "rus")
          ),
          notes = List(
            Note(
              contents = "English, with Russian commentary",
              noteType = NoteType.LanguageNote
            )
          )
        )
      )
    )
  }

  it("transforms multiple identifiers") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "BNumber" -> "b456",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.otherIdentifiers shouldBe
      List(
        SourceIdentifier(
          value = "a/b/c",
          identifierType = IdentifierType.CalmRefNo,
          ontologyType = "Work"
        ),
        SourceIdentifier(
          value = "a.b.c",
          identifierType = IdentifierType.CalmAltRefNo,
          ontologyType = "Work"
        ),
        SourceIdentifier(
          value = "b456",
          identifierType = IdentifierType.SierraSystemNumber,
          ontologyType = "Work"
        )
      )
  }

  it("transforms merge candidates") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "BNumber" -> "b12345672",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.state.mergeCandidates shouldBe
      List(
        MergeCandidate(
          identifier = SourceIdentifier(
            value = "b12345672",
            identifierType = IdentifierType.SierraSystemNumber,
            ontologyType = "Work"
          ),
          reason = "CALM/Sierra harvest work"
        ),
      )
  }

  it("transforms access conditions") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "AccessStatus" -> "Restricted",
      "UserDate1" -> "10/10/2050",
      "AccessConditions" -> "nope.",
      "AccessConditions" -> "nope.",
      "CatalogueStatus" -> "Catalogued"
    )
    val termsOfUse = CalmTransformer(record, version).right.get.data.notes
      .collectFirst { case Note.TermsOfUse(contents) => contents }

    termsOfUse shouldBe Some("nope. nope. Restricted until 10 October 2050.")
  }

  it("transforms description") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Description" -> "description of the thing",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.description shouldBe
      Some("description of the thing")
  }

  it("transforms physical description") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Extent" -> "long",
      "UserWrapped6" -> "thing",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.physicalDescription shouldBe
      Some("long thing")
  }

  it("transforms production dates") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Date" -> "c.1900 and 1914",
      "CatalogueStatus" -> "catalogued"
    )
    CalmTransformer(record, version).right.get.data.production shouldBe
      List(
        ProductionEvent(
          dates = List(
            Period(
              label = "c.1900 and 1914",
              range = InstantRange(
                LocalDate of (1890, 1, 1),
                LocalDate of (1914, 12, 31),
                "c.1900 and 1914"
              )
            )
          ),
          label = "c.1900 and 1914",
          places = Nil,
          agents = Nil,
          function = None
        )
      )
  }

  it("transforms subjects, stripping all HTML") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Subject" -> "<p>botany",
      "Subject" -> "<i>anatomy</i>",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.subjects should contain theSameElementsAs List(
      Subject("anatomy", List(Concept("anatomy"))),
      Subject("botany", List(Concept("botany")))
    )
  }

  it("finds a single language") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "English",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.languages shouldBe Seq(
      Language(label = "English", id = "eng")
    )
  }

  it("finds multiple languages") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "English",
      "Language" -> "German",
      "Language" -> "French",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.languages shouldBe Seq(
      Language(label = "English", id = "eng"),
      Language(label = "German", id = "ger"),
      Language(label = "French", id = "fre")
    )
  }

  it("only preserves i HTML tags when transforming title") {
    val record = createCalmRecordWith(
      "Title" -> "<p> The <i>title</i> of the <strong>work</strong>",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "English",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.title shouldBe Some(
      "The <i>title</i> of the work"
    )
  }

  it("strips whitespace from the langauge") {
    val recordA = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "English ",
      "CatalogueStatus" -> "Catalogued"
    )
    val recordB = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "  ",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(recordA, version).right.get.data.languages shouldBe Seq(
      Language(label = "English", id = "eng")
    )
    CalmTransformer(recordB, version).right.get.data.languages shouldBe empty
  }

  it("parses language codes that can have various labels") {
    val recordA = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "Dutch",
      "CatalogueStatus" -> "Catalogued"
    )
    val recordB = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "Flemish",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(recordA, version).right.get.data.languages shouldBe Seq(
      Language(label = "Dutch", id = "dut")
    )
    CalmTransformer(recordB, version).right.get.data.languages shouldBe Seq(
      Language(label = "Flemish", id = "dut")
    )
  }

  it("transforms multiple contributors") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CreatorName" -> "Bebop",
      "CreatorName" -> "Rocksteady",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.contributors should contain theSameElementsAs List(
      Contributor(
        agent =
          Agent(id = labelDerivedAgentIdentifier("bebop"), label = "Bebop"),
        roles = Nil
      ),
      Contributor(
        agent = Agent(
          id = labelDerivedAgentIdentifier("rocksteady"),
          label = "Rocksteady"),
        roles = Nil
      )
    )
  }

  it("transforms multiple notes") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Copyright" -> "no copyright",
      "ReproductionConditions" -> "reproduce at will",
      "Arrangement" -> "meet at midnight",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.notes should contain theSameElementsAs List(
      Note(contents = "no copyright", noteType = NoteType.CopyrightNote),
      Note(contents = "meet at midnight", noteType = NoteType.ArrangementNote)
    )
  }

  it("ignores case when transforming workType") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Subseries",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.workType shouldBe
      WorkType.Series
  }

  it("Returns an empty deleted work when isDeleted = true") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Subseries",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )

    val result = CalmTransformer(
      record.id,
      CalmSourceData(record, isDeleted = true),
      version
    ).right.get
    result shouldBe a[Work.Deleted[_]]
    val deletedWork = result.asInstanceOf[Work.Deleted[_]]

    deletedWork.deletedReason shouldBe DeletedFromSource("Calm")
    deletedWork.data shouldBe WorkData()
  }

  it("transforms to deleted work when CatalogueStatus is suppressible") {
    val recordA = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Catalogued"
    )
    val recordB = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Not yet available"
    )
    val recordC = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Partially catalogued"
    )
    val recordD = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "   caTAlogued  "
    )
    val recordE = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "pArtialLy catalogued "
    )
    val recordF = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Third-party metadata"
    )
    val suppressibleRecordA = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Blonk"
    )
    val suppressibleRecordB = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c"
    )

    val examples = Table(
      ("-record-", "-suppressed-"),
      (recordA, false),
      (recordB, false),
      (recordC, false),
      (recordD, false),
      (recordE, false),
      (recordF, false),
      (suppressibleRecordA, true),
      (suppressibleRecordB, true)
    )

    forAll(examples) { (record, suppressed) =>
      CalmTransformer(record, version).right.get match {
        case _: Work.Deleted[Source] => suppressed shouldBe true
        case _                       => suppressed shouldBe false
      }
    }
  }

  it("Returns Work.Invisible[Source] when missing required source fields") {
    val noTitle = createCalmRecordWith(
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Catalogued"
    )
    val noLevel = createCalmRecordWith(
      "Title" -> "Stay calm",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Catalogued"
    )
    val noRefNo = createCalmRecordWith(
      "Title" -> "Stay calm",
      "Level" -> "Collection",
      "CatalogueStatus" -> "Catalogued"
    )

    List(noTitle, noLevel, noRefNo) map { record =>
      CalmTransformer(record, version).right.get shouldBe a[Work.Invisible[_]]
    }
  }

  it("returns a Work.Invisible[Source] if no title") {
    val record = createCalmRecordWith(
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get shouldBe a[Work.Invisible[_]]
  }

  it("returns a Work.Invisible[Source] if no format") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get shouldBe a[Work.Invisible[_]]
  }

  it("returns a Work.Invisible[Source] if invalid format") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "TopLevel",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get shouldBe a[Work.Invisible[_]]
  }

  it("returns a Work.Invisible[Source] if no RefNo") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get shouldBe a[Work.Invisible[_]]
  }

  it("returns a Work.Invisible[Source] for 'Group of Pieces'-level works") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Group of Pieces",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    val result = CalmTransformer(record, version).right.get
    result shouldBe a[Work.Invisible[_]]
    result
      .asInstanceOf[Work.Invisible[_]]
      .invisibilityReasons should contain only
      InvisibilityReason.UnableToTransform(
        "Calm:Suppressed level - group of pieces"
      )
  }

  it("does not add language code if language not recognised") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "Some freeform discussion of the language",
      "CatalogueStatus" -> "Catalogued"
    )

    val workData = CalmTransformer(record, version).right.get.data

    workData.languages shouldBe empty
    workData.notes should contain(
      Note(
        contents = "Some freeform discussion of the language",
        noteType = NoteType.LanguageNote
      )
    )
  }

  it("suppresses Archives and Manuscripts Resource Guide works") {
    val record = createCalmRecordWith(
      "Title" -> "Should suppress",
      "Level" -> "Section",
      "RefNo" -> "AMSG/X/Y",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version) shouldBe Right(
      Work.Deleted[Source](
        state = Source(
          SourceIdentifier(
            value = record.id,
            identifierType = IdentifierType.CalmRecordIdentifier,
            ontologyType = "Work"
          ),
          record.retrievedAt
        ),
        version = version,
        deletedReason = SuppressedFromSource("Calm")
      )
    )
  }

  it("unpicks bad encoding in the source record") {
    // This is based on a real record: 995b1ac1-fdaa-4e6d-91c9-056c6030f6fb
    val record = createCalmRecordWith(
      "Title" -> "'Correspondence re \u0093Junk\u0094'",
      "Level" -> "Section",
      "RefNo" -> "PPCRI/H/6/13/8",
      "CatalogueStatus" -> "Catalogued"
    )

    val work = CalmTransformer(record, version).value

    work.data.title shouldBe Some("'Correspondence re “Junk”'")
  }
}

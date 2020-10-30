package uk.ac.wellcome.platform.transformer.calm

import java.time.Instant

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import uk.ac.wellcome.models.work.internal._
import WorkState.Source

class CalmTransformerTest extends AnyFunSpec with Matchers {

  val version = 3
  val id = "123"

  it("transforms to a work") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version) shouldBe Right(
      Work.Visible[Source](
        version = version,
        state = Source(
          SourceIdentifier(
            value = id,
            identifierType = CalmIdentifierTypes.recordId,
            ontologyType = "SourceIdentifier"
          ),
          record.retrievedAt
        ),
        data = WorkData[DataState.Unidentified](
          title = Some("abc"),
          format = Some(Format.ArchivesAndManuscripts),
          collectionPath = Some(
            CollectionPath(
              path = "a/b/c",
              level = Some(CollectionLevel.Collection),
              label = Some("a.b.c")
            )
          ),
          otherIdentifiers = List(
            SourceIdentifier(
              value = "a/b/c",
              identifierType = CalmIdentifierTypes.refNo,
              ontologyType = "SourceIdentifier"),
            SourceIdentifier(
              value = "a.b.c",
              identifierType = CalmIdentifierTypes.altRefNo,
              ontologyType = "SourceIdentifier"),
          ),
          items = List(
            Item(
              title = None,
              locations = List(
                PhysicalLocationDeprecated(
                  locationType = LocationType("scmac"),
                  label = "Closed stores Arch. & MSS",
                  accessConditions = Nil
                )
              )
            )
          ),
          workType = WorkType.Collection
        )
      )
    )
  }

  it("transforms multiple identifiers") {
    val record = calmRecord(
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
          identifierType = CalmIdentifierTypes.refNo,
          ontologyType = "SourceIdentifier"),
        SourceIdentifier(
          value = "a.b.c",
          identifierType = CalmIdentifierTypes.altRefNo,
          ontologyType = "SourceIdentifier"),
        SourceIdentifier(
          value = "b456",
          identifierType = IdentifierType("sierra-system-number"),
          ontologyType = "SourceIdentifier"),
      )
  }

  it("transforms merge candidates") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "BNumber" -> "b456",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.mergeCandidates shouldBe
      List(
        MergeCandidate(
          SourceIdentifier(
            value = "b456",
            identifierType = IdentifierType("sierra-system-number"),
            ontologyType = "Work"
          )
        )
      )
  }

  it("transforms access conditions") {
    val record = calmRecord(
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
    val item = CalmTransformer(record, version).right.get.data.items.head
    item.locations.head.accessConditions shouldBe List(
      AccessCondition(
        status = Some(AccessStatus.Restricted),
        terms = Some("nope. nope."),
        to = Some("10/10/2050")
      )
    )
  }

  it("transforms description") {
    val record = calmRecord(
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
    val record = calmRecord(
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
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Date" -> "1980-1991"
    )
    CalmTransformer(record, version).right.get.data.production shouldBe
      List(
        ProductionEvent(
          dates = List(Period("1980-1991")),
          label = "1980-1991",
          places = Nil,
          agents = Nil,
          function = None))
  }

  it("transforms subjects, stripping all HTML") {
    val record = calmRecord(
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

  it("transforms language") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "English",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.language shouldBe Some(
      Language("English", Some("en"))
    )
  }

  it("only preserves i HTML tags when transforming title") {
    val record = calmRecord(
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

  it("strips whitespace when transforming language") {
    val recordA = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "English ",
      "CatalogueStatus" -> "Catalogued"
    )
    val recordB = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "  ",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(recordA, version).right.get.data.language shouldBe Some(
      Language("English", Some("en"))
    )
    CalmTransformer(recordB, version).right.get.data.language shouldBe None
  }

  it("parses language codes that can have various labels") {
    val recordA = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "Dutch",
      "CatalogueStatus" -> "Catalogued"
    )
    val recordB = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "Flemish",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(recordA, version).right.get.data.language shouldBe Some(
      Language("Dutch", Some("nl"))
    )
    CalmTransformer(recordB, version).right.get.data.language shouldBe Some(
      Language("Flemish", Some("nl"))
    )
  }

  it("transforms multiple contributors") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CreatorName" -> "Bebop",
      "CreatorName" -> "Rocksteady",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.contributors should contain theSameElementsAs List(
      Contributor(Agent("Bebop"), Nil),
      Contributor(Agent("Rocksteady"), Nil),
    )
  }

  it("transforms multiple notes") {
    val record = calmRecord(
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
      CopyrightNote("no copyright"),
      TermsOfUse("reproduce at will"),
      ArrangementNote("meet at midnight"),
    )
  }

  it("ignores case when transforming level") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Subseries",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.collectionPath.get.level shouldBe Some(
      CollectionLevel.Series
    )
  }

  it("transforms to invisible work when CatalogueStatus is suppressible") {
    val recordA = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Catalogued"
    )
    val recordB = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Not yet available"
    )
    val recordC = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Partially catalogued"
    )
    val recordD = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "   caTAlogued  "
    )
    val recordE = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "pArtialLy catalogued "
    )
    val recordF = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Third-party metadata"
    )
    val suppressibleRecordA = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Blonk"
    )
    val suppressibleRecordB = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
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
        case _: Work.Invisible[Source] => suppressed shouldBe true
        case _                         => suppressed shouldBe false
      }
    }
  }

  it("Returns Work.Invisible[Source] when missing required source fields") {
    val noTitle = calmRecord(
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Catalogued"
    )
    val noLevel = calmRecord(
      "Title" -> "Stay calm",
      "RefNo" -> "a/b/c",
      "CatalogueStatus" -> "Catalogued"
    )
    val noRefNo = calmRecord(
      "Title" -> "Stay calm",
      "Level" -> "Collection",
      "CatalogueStatus" -> "Catalogued"
    )

    List(noTitle, noLevel, noRefNo) map { record =>
      CalmTransformer(record, version).right.get shouldBe a[Work.Invisible[_]]
    }
  }

  it("returns a Work.Invisible[Source] if invalid access status") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "AccessStatus" -> "AAH",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get shouldBe a[Work.Invisible[_]]
  }

  it("returns a Work.Invisible[Source] if no title") {
    val record = calmRecord(
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get shouldBe a[Work.Invisible[_]]
  }

  it("returns a Work.Invisible[Source] if no format") {
    val record = calmRecord(
      "Title" -> "abc",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get shouldBe a[Work.Invisible[_]]
  }

  it("returns a Work.Invisible[Source] if invalid format") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "TopLevel",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get shouldBe a[Work.Invisible[_]]
  }

  it("returns a Work.Invisible[Source] if no RefNo") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get shouldBe a[Work.Invisible[_]]
  }

  it("does not add language code if language not recognised") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "Lolol",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version).right.get.data.language shouldBe Some(
      Language("Lolol", None))
  }

  it("suppresses Archives and Manuscrupts Resource Guide works") {
    import InvisibilityReason._
    val record = calmRecord(
      "Title" -> "Should suppress",
      "Level" -> "Section",
      "RefNo" -> "AMSG/X/Y",
      "CatalogueStatus" -> "Catalogued"
    )
    CalmTransformer(record, version) shouldBe Right(
      Work.Invisible[Source](
        state = Source(
          SourceIdentifier(
            value = id,
            identifierType = CalmIdentifierTypes.recordId,
            ontologyType = "SourceIdentifier"
          ),
          record.retrievedAt
        ),
        version = version,
        data = WorkData[DataState.Unidentified](
          title = Some("Should suppress"),
          format = Some(Format.ArchivesAndManuscripts),
          collectionPath = Some(
            CollectionPath(
              path = "AMSG/X/Y",
              level = Some(CollectionLevel.Section),
            )
          ),
          otherIdentifiers = List(
            SourceIdentifier(
              value = "AMSG/X/Y",
              identifierType = CalmIdentifierTypes.refNo,
              ontologyType = "SourceIdentifier"),
          ),
          items = List(
            Item(
              title = None,
              locations = List(
                PhysicalLocationDeprecated(
                  locationType = LocationType("scmac"),
                  label = "Closed stores Arch. & MSS",
                  accessConditions = Nil
                )
              )
            )
          ),
          workType = WorkType.Section
        ),
        invisibilityReasons = List(SuppressedFromSource("Calm"))
      )
    )
  }

  def calmRecord(fields: (String, String)*): CalmRecord =
    CalmRecord(
      id = id,
      retrievedAt = Instant.ofEpochSecond(123456789),
      data = fields.foldLeft(Map.empty[String, List[String]]) {
        case (map, (key, value)) =>
          map + (key -> (value :: map.get(key).getOrElse(Nil)))
      }
    )
}

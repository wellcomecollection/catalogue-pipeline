package uk.ac.wellcome.platform.transformer.calm

import java.time.Instant
import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.models.work.internal._

class CalmTransformerTest extends FunSpec with Matchers {

  val version = 3
  val id = "123"

  it("transforms to a work") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
    )
    CalmTransformer(record, version) shouldBe Right(
      UnidentifiedWork(
        version = version,
        sourceIdentifier = SourceIdentifier(
          value = id,
          identifierType = CalmIdentifierTypes.recordId),
        data = WorkData(
          title = Some("abc"),
          workType = Some(WorkType.ArchiveCollection),
          collectionPath = Some(
            CollectionPath(
              path = "a/b/c",
              level = CollectionLevel.Collection,
              label = Some("a.b.c")
            )
          ),
          otherIdentifiers = List(
            SourceIdentifier(
              value = "a/b/c",
              identifierType = CalmIdentifierTypes.refNo),
            SourceIdentifier(
              value = "a.b.c",
              identifierType = CalmIdentifierTypes.altRefNo),
          ),
          items = List(
            Item(
              title = None,
              locations = List(
                PhysicalLocation(
                  locationType = LocationType("scmac"),
                  label = "Closed stores Arch. & MSS",
                  accessConditions = Nil
                )
              )
            )
          )
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
    )
    CalmTransformer(record, version).right.get.data.otherIdentifiers shouldBe
      List(
        SourceIdentifier(
          value = "a/b/c",
          identifierType = CalmIdentifierTypes.refNo),
        SourceIdentifier(
          value = "a.b.c",
          identifierType = CalmIdentifierTypes.altRefNo),
        SourceIdentifier(
          value = "b456",
          identifierType = IdentifierType("sierra-system-number")),
      )
  }

  it("transforms merge candidates") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "BNumber" -> "b456",
    )
    CalmTransformer(record, version).right.get.data.mergeCandidates shouldBe
      List(
        MergeCandidate(
          SourceIdentifier(
            value = "b456",
            identifierType = IdentifierType("sierra-system-number"),
            ontologyType = "Work")
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

  it("transforms physical description") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Extent" -> "long",
      "UserWrapped6" -> "thing",
    )
    CalmTransformer(record, version).right.get.data.physicalDescription shouldBe
      Some("long thing")
  }

  it("transforms subjects") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Subject" -> "botany",
      "Subject" -> "anatomy",
    )
    CalmTransformer(record, version).right.get.data.subjects should contain
    allOf(
      Subject("botany", Nil),
      Subject("anatomy", Nil)
    )
  }

  it("transforms language") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "English"
    )
    CalmTransformer(record, version).right.get.data.language shouldBe Some(
      Language("en", "English")
    )
  }

  it("strips whitespace when transforming language") {
    val recordA = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "English "
    )
    val recordB = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "  "
    )
    CalmTransformer(recordA, version).right.get.data.language shouldBe Some(
      Language("en", "English")
    )
    CalmTransformer(recordB, version).right.get.data.language shouldBe None
  }

  it("parses language codes that can have various labels") {
    val recordA = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "Dutch"
    )
    val recordB = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "Flemish"
    )
    CalmTransformer(recordA, version).right.get.data.language shouldBe Some(
      Language("nl", "Dutch")
    )
    CalmTransformer(recordB, version).right.get.data.language shouldBe Some(
      Language("nl", "Flemish")
    )
  }

  it("transforms multiple contributors") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CreatorName" -> "Bebop",
      "CreatorName" -> "Rocksteady"
    )
    CalmTransformer(record, version).right.get.data.contributors should contain
    allOf(
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
      "Arrangement" -> "meet at midnight"
    )
    CalmTransformer(record, version).right.get.data.notes should contain allOf (
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
    )
    CalmTransformer(record, version).right.get.data.collectionPath.get.level shouldBe CollectionLevel.Series
  }

  it("errors if invalid access status") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "AccessStatus" -> "AAH",
    )
    CalmTransformer(record, version) shouldBe a[Left[_, _]]
  }

  it("errors if no title") {
    val record = calmRecord(
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
    )
    CalmTransformer(record, version) shouldBe a[Left[_, _]]
  }

  it("errors if no workType") {
    val record = calmRecord(
      "Title" -> "abc",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
    )
    CalmTransformer(record, version) shouldBe a[Left[_, _]]
  }

  it("errors if invalid workType") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "TopLevel",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
    )
    CalmTransformer(record, version) shouldBe a[Left[_, _]]
  }

  it("errors if no RefNo") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "AltRefNo" -> "a.b.c",
    )
    CalmTransformer(record, version) shouldBe a[Left[_, _]]
  }

  it("errors if language not recognised") {
    val record = calmRecord(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "Language" -> "Lolol"
    )
    CalmTransformer(record, version) shouldBe a[Left[_, _]]
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

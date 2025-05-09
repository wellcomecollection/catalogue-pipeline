package weco.catalogue.internal_model.work
import weco.catalogue.internal_model.work.WorkState.{
  Denormalised,
  Identified,
  Merged
}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.identifiers.IdentifierType.SierraIdentifier
import weco.catalogue.internal_model.identifiers.{CanonicalId, SourceIdentifier}

import java.time.Instant

class StateTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks {
  private val sourceIdentifier =
    SourceIdentifier(SierraIdentifier, "otype", "id")
  private val workData = WorkData(title = Some("My Title"))
  private val canonicalId = CanonicalId("baadf00d")
  private val identified = Work.Visible[Identified](
    version = 0,
    state = Identified(
      sourceIdentifier = sourceIdentifier,
      canonicalId = canonicalId,
      sourceModifiedTime = Instant.MIN,
      relations = Relations(ancestors = List(SeriesRelation("Mum")))
    ),
    data = WorkData(title = Some("My Title"))
  )
  private val merged = identified.transition[Merged](Instant.MAX)

  describe("transition from identified to merged") {

    it("preserves the Work data as-is") {
      merged.data shouldBe workData
    }

    it("assigns a new mergedTime") {
      merged.state.mergedTime shouldBe Instant.MAX
    }

    it("preserves existing state members") {
      val preservedMembers = Table(
        ("preservedMember", "expectedValue"),
        (
          merged.state.relations,
          Relations(ancestors = List(SeriesRelation("Mum")))
        ),
        (merged.state.sourceIdentifier, sourceIdentifier),
        (merged.state.canonicalId, canonicalId),
        (merged.state.sourceModifiedTime, Instant.MIN)
      )
      forAll(preservedMembers) {
        case (preservedMember, expectedValue) =>
          preservedMember shouldBe expectedValue
      }
    }
  }

  private val denormalised = merged.transition[Denormalised](
    Relations(
      ancestors = List(SeriesRelation("Dad")),
      siblingsPreceding = List(SeriesRelation("Big Brother"))
    )
  )

  describe("transition from merged to denormalised") {
    it("preserves the Work data as-is") {
      denormalised.data shouldBe workData
    }

    it("merges existing relations with newly provided relations") {
      denormalised.state.relations shouldBe Relations(
        ancestors = List(SeriesRelation("Mum"), SeriesRelation("Dad")),
        siblingsPreceding = List(SeriesRelation("Big Brother"))
      )
    }

    it(
      "overwrites relations if they match by name, otherwise concatenating the relation lists in the normal manner"
    ) {
      val merged = Work.Visible[Merged](
        version = 0,
        state = Merged(
          sourceIdentifier = sourceIdentifier,
          canonicalId = canonicalId,
          sourceModifiedTime = Instant.MIN,
          mergedTime = Instant.MIN,
          availabilities = Set(),
          relations = Relations(
            ancestors = List(SeriesRelation("Mum"), SeriesRelation("Dad"))
          )
        ),
        data = WorkData(title = Some("My Title"))
      )

      val newMum = new Relation(
        id = Some(CanonicalId("deadbeef")),
        title = Some("Mum"),
        collectionPath = Some(CollectionPath("cafed00d/deadbeef")),
        workType = WorkType.Standard,
        depth = 0,
        numChildren = 1,
        numDescendents = 1
      )

      val granny = new Relation(
        id = Some(CanonicalId("baadf00d")),
        title = Some("granny"),
        collectionPath = Some(CollectionPath("cafed00d/deadbeef/baadf00d")),
        workType = WorkType.Standard,
        depth = 0,
        numChildren = 1,
        numDescendents = 1
      )

      val denormalised = merged.transition[Denormalised](
        Relations(
          // Mum is already in the list, granny is new
          ancestors = List(newMum, granny)
        )
      )
      denormalised.state.relations.ancestors shouldBe List(
        SeriesRelation("Dad"),
        newMum,
        granny
      )
    }
    
    it("matches relations ignoring trailing terminal punctuation") {
      val merged = Work.Visible[Merged](
        version = 0,
        state = Merged(
          sourceIdentifier = sourceIdentifier,
          canonicalId = canonicalId,
          sourceModifiedTime = Instant.MIN,
          mergedTime = Instant.MIN,
          availabilities = Set(),
          // Unnecessary trailing punctuation is expected to have
          // been removed in the creation of a SeriesRelation.
          relations = Relations(
            ancestors = List(
              SeriesRelation("Basil Hood. Photograph Album"),
              SeriesRelation("Studio Portraits of Women.")
            )
          )
        ),
        data = WorkData(title = Some("My Title"))
      )

      val album = new Relation(
        id = Some(CanonicalId("deadbeef")),
        // The title is the same as the one already there, but with a trailing '.'
        title = Some("Basil Hood. Photograph Album."),
        collectionPath = Some(CollectionPath("cafed00d/deadbeef")),
        workType = WorkType.Standard,
        depth = 0,
        numChildren = 1,
        numDescendents = 1
      )

      val portraits = new Relation(
        id = Some(CanonicalId("cafefeed")),
        // The title is the same as the one already there, retaining the trailing '.'
        title = Some("Studio Portraits of Women."),
        collectionPath = Some(CollectionPath("abadcafe/cafefeed")),
        workType = WorkType.Standard,
        depth = 0,
        numChildren = 1,
        numDescendents = 1
      )

      val denormalised = merged.transition[Denormalised](
        Relations(
          ancestors = List(album, portraits)
        )
      )
      denormalised.state.relations.ancestors shouldBe List(album, portraits)
    }

    it("preserves existing state members") {
      val preservedMembers = Table(
        ("preservedMember", "expectedValue"),
        (denormalised.state.sourceIdentifier, sourceIdentifier),
        (denormalised.state.canonicalId, canonicalId),
        (denormalised.state.sourceModifiedTime, Instant.MIN)
      )
      forAll(preservedMembers) {
        case (preservedMember, expectedValue) =>
          preservedMember shouldBe expectedValue
      }
    }
  }
}

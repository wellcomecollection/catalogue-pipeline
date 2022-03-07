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
  private val merged = Work.Visible[Identified](
    version = 0,
    state = Identified(
      sourceIdentifier = sourceIdentifier,
      canonicalId = canonicalId,
      sourceModifiedTime = Instant.MIN,
      relations = Relations(ancestors = List(SeriesRelation("Mum")))
    ),
    data = WorkData(title = Some("My Title"))
  )
  private val identified = merged.transition[Merged](Instant.MAX)

  describe("transition from identified to merged") {

    it("preserves the Work data as-is") {
      identified.data shouldBe workData
    }

    it("assigns a new mergedTime") {
      identified.state.mergedTime shouldBe Instant.MAX
    }

    it("preserves existing state members") {
      val preservedMembers = Table(
        ("preservedMember", "expectedValue"),
        (
          identified.state.relations,
          Relations(ancestors = List(SeriesRelation("Mum")))),
        (identified.state.sourceIdentifier, sourceIdentifier),
        (identified.state.canonicalId, canonicalId),
        (identified.state.sourceModifiedTime, Instant.MIN)
      )
      forAll(preservedMembers) {
        case (preservedMember, expectedValue) =>
          preservedMember shouldBe expectedValue
      }
    }
  }

  private val denormalised = identified.transition[Denormalised](
    (
      Relations(
        ancestors = List(SeriesRelation("Dad")),
        siblingsPreceding = List(SeriesRelation("Big Brother"))
      ),
      Set()
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

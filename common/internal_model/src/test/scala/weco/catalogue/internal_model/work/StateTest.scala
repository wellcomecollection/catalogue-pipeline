package weco.catalogue.internal_model.work
import weco.catalogue.internal_model.work.WorkState.{
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
      relations = Relations.none
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
          Relations.none
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
}
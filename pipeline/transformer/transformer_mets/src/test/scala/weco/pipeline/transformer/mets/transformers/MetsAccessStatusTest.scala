package weco.pipeline.transformer.mets.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.locations.AccessStatus

class MetsAccessStatusTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks {
  val testCases = Table(
    ("accessConditionStatus", "expectedStatus"),
    ("Restricted files", AccessStatus.Restricted),
    ("Clinical images", AccessStatus.Restricted),
    ("Open", AccessStatus.Open),
    ("Open with advisory", AccessStatus.OpenWithAdvisory),
    ("Requires registration", AccessStatus.OpenWithAdvisory),
    ("Closed", AccessStatus.Closed)
  )

  it("creates an access status") {
    forAll(testCases) {
      case (accessConditionStatus, expectedStatus) =>
        MetsAccessStatus(Some(accessConditionStatus)) shouldBe Right(
          Some(expectedStatus)
        )
    }
  }

  it("returns None if there are no access conditions") {
    MetsAccessStatus(None) shouldBe Right(None)
  }

  it("returns a Left if it can't parse the access conditions") {
    MetsAccessStatus(Some("unintelligible")) shouldBe a[Left[_, _]]
  }
}

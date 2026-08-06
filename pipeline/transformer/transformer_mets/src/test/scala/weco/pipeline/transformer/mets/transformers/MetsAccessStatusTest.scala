package weco.pipeline.transformer.mets.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.locations.AccessStatus

import java.util.Locale

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

  // Capitalisation as found in real Archivematica and Goobi packages.
  val differentlyCasedTestCases = Table(
    ("accessConditionStatus", "expectedStatus"),
    ("Restricted Files", AccessStatus.Restricted),
    ("closed", AccessStatus.Closed),
    ("OPEN", AccessStatus.Open),
    ("open with advisory", AccessStatus.OpenWithAdvisory)
  )

  it("creates an access status") {
    forAll(testCases) {
      case (accessConditionStatus, expectedStatus) =>
        MetsAccessStatus(Some(accessConditionStatus)) shouldBe Right(
          Some(expectedStatus)
        )
    }
  }

  it("ignores the capitalisation of the access status") {
    forAll(differentlyCasedTestCases) {
      case (accessConditionStatus, expectedStatus) =>
        MetsAccessStatus(Some(accessConditionStatus)) shouldBe Right(
          Some(expectedStatus)
        )
    }
  }

  it("ignores capitalisation regardless of the default locale") {
    val defaultLocale = Locale.getDefault()
    try {
      // Turkish lowercases "I" to a dotless "ı", which would break a
      // locale-sensitive match on "Clinical images".
      Locale.setDefault(new Locale("tr", "TR"))
      MetsAccessStatus(Some("CLINICAL IMAGES")) shouldBe Right(
        Some(AccessStatus.Restricted)
      )
    } finally Locale.setDefault(defaultLocale)
  }

  it("still rejects a status that differs by more than capitalisation") {
    MetsAccessStatus(Some("Restricted")) shouldBe a[Left[_, _]]
  }

  it("reports the status as written when it cannot be matched") {
    MetsAccessStatus(Some("Restricted")).left.get.getMessage should include(
      "Restricted"
    )
  }

  it("returns None if there are no access conditions") {
    MetsAccessStatus(None) shouldBe Right(None)
  }

  it("returns a Left if it can't parse the access conditions") {
    MetsAccessStatus(Some("unintelligible")) shouldBe a[Left[_, _]]
  }
}

package weco.pipeline.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.source_model.generators.CalmRecordGenerators

class CalmAccessTermsTest
    extends AnyFunSpec
    with Matchers
    with CalmRecordGenerators {
  it("handles an item which is open") {
    val record = createCalmRecordWith(
      (
        "AccessConditions",
        "The papers are available subject to the usual conditions of access to Archives and Manuscripts material.")
    )

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.Open)) shouldBe Some(
      "The papers are available subject to the usual conditions of access to Archives and Manuscripts material.")
  }

  it("handles an item which is closed") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Closed"),
      ("AccessConditions", "Closed on depositor agreement."),
    )

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.Closed)) shouldBe Some(
      "Closed on depositor agreement.")
  }

  it("handles an item which is restricted") {
    val record = createCalmRecordWith(
      (
        "AccessConditions",
        "Digital records cannot be ordered or viewed online. Requests to view digital records onsite are considered on a case by case basis. Please contact collections@wellcome.ac.uk for more details."),
    )

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.Restricted)) shouldBe Some(
      "Digital records cannot be ordered or viewed online. Requests to view digital records onsite are considered on a case by case basis. Please contact collections@wellcome.ac.uk for more details.")
  }

  it(
    "creates the right note for a closed item where the date is in the access conditions") {
    val record = createCalmRecordWith(
      (
        "AccessConditions",
        "Closed under the Data Protection Act until 1st January 2039."),
      ("ClosedUntil", "01/01/2039")
    )

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.Closed)) shouldBe Some(
      "Closed under the Data Protection Act until 1st January 2039.")
  }

  it(
    "creates the right note for a restricted item where the date is in the access conditions") {
    val record = createCalmRecordWith(
      (
        "AccessConditions",
        "This file is restricted until 01/01/2039 for data protection reasons. Readers must complete and sign a Restricted Access undertaking form to apply for access."),
      ("UserDate1", "01/01/2039")
    )

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.Restricted)) shouldBe Some(
      "This file is restricted until 01/01/2039 for data protection reasons. Readers must complete and sign a Restricted Access undertaking form to apply for access.")
  }

  it(
    "creates a note for a restricted item where the date is not in the access conditions") {
    val record = createCalmRecordWith(
      (
        "AccessConditions",
        "This file is restricted for data protection reasons. When a reader arrives onsite, they will be required to sign a Restricted Access form agreeing to anonymise personal data before viewing the file."),
      ("UserDate1", "01/01/2060")
    )

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.Restricted)) shouldBe Some(
      "This file is restricted for data protection reasons. When a reader arrives onsite, they will be required to sign a Restricted Access form agreeing to anonymise personal data before viewing the file. Restricted until 1 January 2060.")
  }

  it(
    "creates the right note for a closed item where the date is not in the access conditions") {
    val record = createCalmRecordWith(
      ("AccessConditions", "Closed under the Data Protection Act."),
      ("ClosedUntil", "01/01/2039")
    )

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.Closed)) shouldBe Some(
      "Closed under the Data Protection Act. Closed until 1 January 2039.")
  }

  it("creates a note for a closed item with no conditions") {
    val record = createCalmRecordWith(
      ("ClosedUntil", "01/01/2068")
    )

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.Closed)) shouldBe Some(
      "Closed until 1 January 2068.")
  }

  it("creates a note for an item with just a status") {
    val record = createCalmRecord

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.Open)) shouldBe None
  }

  it("creates a note for an item with permission + restrictions") {
    val record = createCalmRecordWith(
      (
        "AccessConditions",
        "Permission must be obtained from <a href=\"mailto:barbie.antonis@gmail.com\">the Winnicott Trust</a>, and the usual conditions of access to Archives and Manuscripts material apply; a Reader's Undertaking must be completed. In addition there are Data Protection restrictions on this item and an additional application for access must be completed."),
      ("UserDate1", "01/01/2072")
    )

    CalmAccessTerms(
      record,
      accessStatus = Some(AccessStatus.PermissionRequired)) shouldBe Some(
      "Permission must be obtained from <a href=\"mailto:barbie.antonis@gmail.com\">the Winnicott Trust</a>, and the usual conditions of access to Archives and Manuscripts material apply; a Reader's Undertaking must be completed. In addition there are Data Protection restrictions on this item and an additional application for access must be completed. Restricted until 1 January 2072.")
  }

  it("adds a missing full stop to access conditions") {
    val record = createCalmRecordWith(
      (
        "AccessConditions",
        "This file is closed for data protection reasons and cannot be accessed"),
      ("ClosedUntil", "01/01/2055")
    )

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.Closed)) shouldBe Some(
      "This file is closed for data protection reasons and cannot be accessed. Closed until 1 January 2055.")
  }

  it("removes trailing whitespace") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Restricted"),
      (
        "AccessConditions",
        "This file is restricted until 01/01/2024 for data protection reasons. Readers must complete and sign a Restricted Access undertaking form to apply for access.\n\n"),
      ("UserDate1", "01/01/2024")
    )

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.Restricted)) shouldBe Some(
      "This file is restricted until 01/01/2024 for data protection reasons. Readers must complete and sign a Restricted Access undertaking form to apply for access.")
  }

  it("handles the fallback case") {
    val record = createCalmRecordWith(
      ("UserDate1", "01/01/2066"),
      (
        "AccessConditions",
        "The papers are available subject to the usual conditions of access to Archives and Manuscripts material. In addition a Restricted Access form must be completed to apply for access to this file.")
    )

    CalmAccessTerms(record, accessStatus = Some(AccessStatus.ByAppointment)) shouldBe Some(
      "The papers are available subject to the usual conditions of access to Archives and Manuscripts material. In addition a Restricted Access form must be completed to apply for access to this file. Restricted until 1 January 2066.")
  }

  it("returns no note if there's no useful access info") {
    val record = createCalmRecordWith()

    CalmAccessTerms(record, accessStatus = None) shouldBe None
  }
}

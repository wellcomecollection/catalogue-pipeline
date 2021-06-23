package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.TermsOfUse
import weco.catalogue.source_model.generators.CalmRecordGenerators

class CalmTermsOfUseTest
    extends AnyFunSpec
    with Matchers
    with CalmRecordGenerators {
  it("handles an item which is open") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Open"),
      (
        "AccessConditions",
        "The papers are available subject to the usual conditions of access to Archives and Manuscripts material.")
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse(
      "The papers are available subject to the usual conditions of access to Archives and Manuscripts material. Open."))
  }

  it("handles an item which is closed") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Closed"),
      ("AccessConditions", "Closed on depositor agreement."),
    )

    CalmTermsOfUse(record) shouldBe Some(
      TermsOfUse("Closed on depositor agreement."))
  }

  it("handles an item which is restricted") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Restricted"),
      (
        "AccessConditions",
        "Digital records cannot be ordered or viewed online. Requests to view digital records onsite are considered on a case by case basis. Please contact collections@wellcome.ac.uk for more details."),
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse(
      "Digital records cannot be ordered or viewed online. Requests to view digital records onsite are considered on a case by case basis. Please contact collections@wellcome.ac.uk for more details. Restricted."))
  }

  it(
    "creates the right note for a closed item where the date is in the access conditions") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Closed"),
      (
        "AccessConditions",
        "Closed under the Data Protection Act until 1st January 2039."),
      ("ClosedUntil", "01/01/2039")
    )

    CalmTermsOfUse(record) shouldBe Some(
      TermsOfUse(
        "Closed under the Data Protection Act until 1st January 2039."))
  }

  it(
    "creates the right note for a restricted item where the date is in the access conditions") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Restricted Access (Data Protection Act)"),
      (
        "AccessConditions",
        "This file is restricted until 01/01/2039 for data protection reasons. Readers must complete and sign a Restricted Access undertaking form to apply for access."),
      ("UserDate1", "01/01/2039")
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse(
      "This file is restricted until 01/01/2039 for data protection reasons. Readers must complete and sign a Restricted Access undertaking form to apply for access."))
  }

  it(
    "creates a note for a restricted item where the date is not in the access conditions") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Restricted"),
      (
        "AccessConditions",
        "This file is restricted for data protection reasons. When a reader arrives onsite, they will be required to sign a Restricted Access form agreeing to anonymise personal data before viewing the file."),
      ("UserDate1", "01/01/2060")
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse(
      "This file is restricted for data protection reasons. When a reader arrives onsite, they will be required to sign a Restricted Access form agreeing to anonymise personal data before viewing the file. Restricted until 1 January 2060."))
  }

  it(
    "creates the right note for a closed item where the date is not in the access conditions") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Closed"),
      ("AccessConditions", "Closed under the Data Protection Act."),
      ("ClosedUntil", "01/01/2039")
    )

    CalmTermsOfUse(record) shouldBe Some(
      TermsOfUse(
        "Closed under the Data Protection Act. Closed until 1 January 2039."))
  }

  it("creates a note for a closed item with no conditions") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Closed"),
      ("ClosedUntil", "01/01/2068")
    )

    CalmTermsOfUse(record) shouldBe Some(
      TermsOfUse("Closed until 1 January 2068."))
  }

  it("creates a note for an item with just a status") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Open")
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse("Open."))
  }

  it("creates a note for an item with permission + restrictions") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Donor Permission"),
      (
        "AccessConditions",
        "Permission must be obtained from <a href=\"mailto:barbie.antonis@gmail.com\">the Winnicott Trust</a>, and the usual conditions of access to Archives and Manuscripts material apply; a Reader's Undertaking must be completed. In addition there are Data Protection restrictions on this item and an additional application for access must be completed."),
      ("UserDate1", "01/01/2072")
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse(
      "Permission must be obtained from <a href=\"mailto:barbie.antonis@gmail.com\">the Winnicott Trust</a>, and the usual conditions of access to Archives and Manuscripts material apply; a Reader's Undertaking must be completed. In addition there are Data Protection restrictions on this item and an additional application for access must be completed. Restricted until 1 January 2072."))
  }

  it("adds a missing full stop to access conditions") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Closed"),
      (
        "AccessConditions",
        "This file is closed for data protection reasons and cannot be accessed"),
      ("ClosedUntil", "01/01/2055")
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse(
      "This file is closed for data protection reasons and cannot be accessed. Closed until 1 January 2055."))
  }

  it("removes trailing whitespace") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Restricted"),
      (
        "AccessConditions",
        "This file is restricted until 01/01/2024 for data protection reasons. Readers must complete and sign a Restricted Access undertaking form to apply for access.\n\n"),
      ("UserDate1", "01/01/2024")
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse(
      "This file is restricted until 01/01/2024 for data protection reasons. Readers must complete and sign a Restricted Access undertaking form to apply for access."))
  }

  it("handles the fallback case") {
    val record = createCalmRecordWith(
      ("AccessStatus", "By Appointment"),
      ("UserDate1", "01/01/2066"),
      (
        "AccessConditions",
        "The papers are available subject to the usual conditions of access to Archives and Manuscripts material. In addition a Restricted Access form must be completed to apply for access to this file.")
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse(
      "The papers are available subject to the usual conditions of access to Archives and Manuscripts material. In addition a Restricted Access form must be completed to apply for access to this file. Restricted until 1 January 2066. By appointment."))
  }

  it("returns no note if there's no useful access info") {
    val record = createCalmRecordWith()

    CalmTermsOfUse(record) shouldBe None
  }
}

package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.locations.AccessStatus
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraAccessStatusTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators
    with TableDrivenPropertyChecks {

  val testCases = Table(
    ("text", "expectedStatus"),
    ("Restricted", AccessStatus.Restricted),
    ("Restricted.", AccessStatus.Restricted),
    ("Restricted .", AccessStatus.Restricted),
    ("Restricted access (Data Protection Act).", AccessStatus.Restricted),
    ("Certain restrictions apply", AccessStatus.Restricted),
    ("Open with advisory.", AccessStatus.OpenWithAdvisory),
    ("Open with Advisory.", AccessStatus.OpenWithAdvisory),
    ("Open", AccessStatus.Open),
    ("Open access.", AccessStatus.Open),
    ("Unrestricted / open", AccessStatus.Open),
    ("Unrestricted (open)", AccessStatus.Open),
    ("Closed.", AccessStatus.Closed),
    (
      "Permission is required to view these item.",
      AccessStatus.PermissionRequired),
    (
      "Permission is required to view this item.",
      AccessStatus.PermissionRequired),
    ("Missing", AccessStatus.Unavailable),
    ("Deaccessioned", AccessStatus.Unavailable),
    ("By Appointment.", AccessStatus.ByAppointment),
    ("Donor Permission", AccessStatus.PermissionRequired),
  )

  it("matches particular strings to an access status") {
    forAll(testCases) {
      case (text, expectedStatus) =>
        val accessStatus = getAccessStatus(
          bibVarFields = List(
            VarField(
              marcTag = "506",
              subfields = List(
                Subfield(tag = "a", content = text)
              )
            )
          )
        )

        accessStatus shouldBe Some(expectedStatus)
    }
  }

  it("returns None if it can't map 506 subfield ǂa to a status") {
    val accessStatus = getAccessStatus(
      bibVarFields = List(
        VarField(
          marcTag = "506",
          subfields = List(
            Subfield(
              tag = "a",
              content = "ACME Library membership required for access.")
          )
        )
      )
    )

    accessStatus shouldBe None
  }

  it("returns None if there's no consistent access status") {
    val accessStatus = getAccessStatus(
      bibVarFields = List(
        VarField(
          marcTag = "506",
          subfields = List(
            Subfield(tag = "a", content = "Restricted"),
            Subfield(tag = "f", content = "Open")
          )
        )
      )
    )

    accessStatus shouldBe None
  }

  it("returns Open if the first indicator is 0") {
    val accessStatus = getAccessStatus(
      bibVarFields = List(
        VarField(
          marcTag = Some("506"),
          indicator1 = Some("0")
        )
      )
    )

    accessStatus shouldBe Some(AccessStatus.Open)
  }

  it("returns None if the first indicator and subfield ǂf disagree") {
    val accessStatus = getAccessStatus(
      bibVarFields = List(
        VarField(
          marcTag = Some("506"),
          indicator1 = Some("0"),
          subfields = List(
            Subfield(tag = "f", content = "Restricted")
          )
        )
      )
    )

    accessStatus shouldBe None
  }

  it("ignores a single period in 506 subfield ǂf") {
    val accessStatus = getAccessStatus(
      bibVarFields = List(
        VarField(
          marcTag = "506",
          subfields = List(
            Subfield(
              tag = "a",
              content = "Access restricted to authorized subscribers"),
            Subfield(tag = "f", content = ".")
          )
        )
      )
    )

    accessStatus shouldBe None
  }

  private def getAccessStatus(
    bibVarFields: List[VarField]): Option[AccessStatus] =
    SierraAccessStatus.forBib(
      bibId = createSierraBibNumber,
      bibData = createSierraBibDataWith(varFields = bibVarFields)
    )
}

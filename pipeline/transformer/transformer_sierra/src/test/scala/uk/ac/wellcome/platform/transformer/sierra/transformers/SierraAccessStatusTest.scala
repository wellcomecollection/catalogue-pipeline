package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.source_model.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import weco.catalogue.source_model.sierra.marc.{MarcSubfield, VarField}

class SierraAccessStatusTest
  extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators
    with TableDrivenPropertyChecks {

  val testCases = Table(
    ("text", "expectedStatus"),
    ("Unrestricted", AccessStatus.Open),
    ("Unrestricted.", AccessStatus.Open),
    ("Restricted", AccessStatus.Restricted),
    ("Restricted.", AccessStatus.Restricted),
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
  )

  it("matches particular strings to an access status") {
    forAll(testCases) {
      case (text, expectedStatus) =>
        val accessStatus = getAccessStatus(
          bibVarFields = List(
            VarField(
              marcTag = Some("506"),
              subfields = List(
                MarcSubfield(tag = "a", content = text)
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
          marcTag = Some("506"),
          subfields = List(
            MarcSubfield(
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
          marcTag = Some("506"),
          subfields = List(
            MarcSubfield(tag = "a", content = "Restricted"),
            MarcSubfield(tag = "f", content = "Open")
          )
        )
      )
    )

    accessStatus shouldBe None
  }

  it("returns None if the first indicator and subfield ǂf disagree") {
    val accessStatus = getAccessStatus(
      bibVarFields = List(
        VarField(
          marcTag = Some("506"),
          indicator1 = Some("0"),
          subfields = List(
            MarcSubfield(tag = "f", content = "Restricted")
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
          marcTag = Some("506"),
          subfields = List(
            MarcSubfield(
              tag = "a",
              content = "Access restricted to authorized subscribers"),
            MarcSubfield(tag = "f", content = ".")
          )
        )
      )
    )

    accessStatus shouldBe None
  }

  private def getAccessStatus(bibVarFields: List[VarField]): Option[AccessStatus] =
    SierraAccessStatus.forBib(
      bibId = createSierraBibNumber,
      bibData = createSierraBibDataWith(varFields = bibVarFields)
    )
}

package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.locations.{AccessCondition, AccessStatus}
import weco.catalogue.source_model.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import weco.catalogue.source_model.sierra.marc.{MarcSubfield, VarField}

class SierraAccessConditionsTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators
    with TableDrivenPropertyChecks {
  it("drops an empty string in 506 subfield ǂa") {
    val accessConditions = getAccessConditions(
      bibVarFields = List(
        VarField(
          marcTag = Some("506"),
          subfields = List(
            MarcSubfield(tag = "a", content = "")
          )
        )
      )
    )

    accessConditions shouldBe empty
  }

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

  it("matches particular strings in 506 subfield ǂa to an access status") {
    forAll(testCases) {
      case (text, expectedStatus) =>
        val accessConditions = getAccessConditions(
          bibVarFields = List(
            VarField(
              marcTag = Some("506"),
              subfields = List(
                MarcSubfield(tag = "a", content = text)
              )
            )
          )
        )

        accessConditions shouldBe List(
          AccessCondition(
            status = Some(expectedStatus),
            terms = None,
            to = None
          )
        )
    }
  }

  it(
    "exposes the terms from 506 subfield ǂa if it can't map them to an AccessStatus") {
    val accessConditions = getAccessConditions(
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

    accessConditions shouldBe List(
      AccessCondition(
        status = None,
        terms = Some("ACME Library membership required for access."),
        to = None
      )
    )
  }

  it(
    "exposes the terms from 506 subfield ǂa if there's no consistent access status") {
    val accessConditions = getAccessConditions(
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

    accessConditions shouldBe List(
      AccessCondition(
        status = None,
        terms = Some("Restricted"),
        to = None
      )
    )
  }

  it("ignores a single period 506 subfield ǂf") {
    val accessConditions = getAccessConditions(
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

    accessConditions shouldBe List(
      AccessCondition(
        status = None,
        terms = Some("Access restricted to authorized subscribers"),
        to = None
      )
    )
  }

  it("strips whitespace from the access conditions") {
    val accessConditions = getAccessConditions(
      bibVarFields = List(
        VarField(
          marcTag = Some("506"),
          subfields = List(
            MarcSubfield("a", "Access restricted to authorized subscribers. "),
          )
        )
      )
    )

    accessConditions shouldBe List(
      AccessCondition(
        status = None,
        terms = Some("Access restricted to authorized subscribers."),
        to = None
      )
    )
  }

  private def getAccessConditions(
    bibVarFields: List[VarField]): List[AccessCondition] =
    SierraAccessConditions(
      bibId = createSierraBibNumber,
      bibData = createSierraBibDataWith(varFields = bibVarFields)
    )
}

package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraDesignationTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {
  it("returns an empty list if a record doesn't use MARC 362") {
    val bibData = createSierraBibData

    SierraDesignation(createSierraBibNumber, bibData) shouldBe List()
  }

  it("uses 362 ǂa") {
    // This is based on b14974708
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "362",
          subfields = List(
            Subfield(
              tag = "a",
              content = "Began in 1955; ceased with v. 49, no. 4 (Dec. 2003). "
            ),
            Subfield(
              tag = "z",
              content = "Cf.National Library of Australia catalogue."
            )
          )
        )
      )
    )

    SierraDesignation(createSierraBibNumber, bibData) shouldBe List(
      "Began in 1955; ceased with v. 49, no. 4 (Dec. 2003)."
    )
  }

  it("gets multiple instances of 362") {
    // This is based on b14975853
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "362",
          subfields = List(
            Subfield(tag = "a", content = "Vol. 51, no. 2, 3 (summer 1988)-")
          )
        ),
        VarField(
          marcTag = "362",
          subfields = List(
            Subfield(
              tag = "a",
              content = "Ceased with v. 59, no. 1 published in 1998."
            )
          )
        )
      )
    )
    SierraDesignation(createSierraBibNumber, bibData) shouldBe List(
      "Vol. 51, no. 2, 3 (summer 1988)-",
      "Ceased with v. 59, no. 1 published in 1998."
    )
  }
}

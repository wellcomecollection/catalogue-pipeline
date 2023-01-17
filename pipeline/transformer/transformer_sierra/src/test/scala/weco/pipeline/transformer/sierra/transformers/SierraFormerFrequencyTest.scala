package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraFormerFrequencyTest extends AnyFunSpec with Matchers with SierraDataGenerators
{
  it("returns an empty list if a record doesn't use MARC 321") {
    val bibData = createSierraBibData

    SierraFormerFrequency(bibData) shouldBe List()
  }

  it("combines 321 ǂa and ǂb") {
    // This is based on b16803930
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "321",
          subfields = List(
            Subfield(tag = "a", content = "Weekly,"),
            Subfield(tag = "b", content = "<Aug. 1, 1988->"),
          )
        )
      )
    )

    SierraFormerFrequency(bibData) shouldBe List("Weekly, <Aug. 1, 1988->")
  }

  it("gets multiple instances of 321") {
    // This is based on b1312092x
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "321",
          subfields = List(
            Subfield(tag = "a", content = "Former frequency: Monthly, 1809-1819, 1822-1827"),
          )
        ),
        VarField(
          marcTag = "321",
          subfields = List(
            Subfield(tag = "a", content = "Former frequency: Weekly, 1801-1808")
          )
        )
      )
    )

    SierraFormerFrequency(bibData) shouldBe List("Former frequency: Monthly, 1809-1819, 1822-1827", "Former frequency: Weekly, 1801-1808")
  }
}

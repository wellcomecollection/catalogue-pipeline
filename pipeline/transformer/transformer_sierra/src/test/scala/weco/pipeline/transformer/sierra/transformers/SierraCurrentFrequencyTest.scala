package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraCurrentFrequencyTest extends AnyFunSpec with Matchers with SierraDataGenerators
{
  it("returns None if a record doesn't use MARC 310") {
    val bibData = createSierraBibData

    SierraCurrentFrequency(bibData) shouldBe None
  }

it("combines multiple 310 fields into a single string") {
     // This is based on b32506697
     val bibData = createSierraBibDataWith(
       varFields = List(
         VarField(
           marcTag = "310",
           subfields = List(
             Subfield(tag = "a", content = "Annual,"),
             Subfield(tag = "b", content = "2007-2012"),
           )
         ),
         VarField(
           marcTag = "310",
           subfields = List(
             Subfield(tag = "a", content = "Solsticial,"),
             Subfield(tag = "b", content = "2004-2006"),
           )
         )
       )
     )

     SierraCurrentFrequency(bibData) shouldBe Some("Annual, 2007-2012 Solsticial, 2004-2006")
   }

  it("combines 310 ǂa and ǂb") {
    // This is based on b32506697
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "310",
          subfields = List(
            Subfield(tag = "a", content = "Annual,"),
            Subfield(tag = "b", content = "2007-2012"),
          )
        )
      )
    )

    SierraCurrentFrequency(bibData) shouldBe Some("Annual, 2007-2012")
  }
}

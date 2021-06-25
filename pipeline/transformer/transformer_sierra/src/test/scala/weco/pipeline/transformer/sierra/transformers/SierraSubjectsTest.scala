package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.{Concept, Subject}
import weco.catalogue.source_model.generators.SierraDataGenerators
import weco.catalogue.source_model.sierra.marc.{MarcSubfield, VarField}

class SierraSubjectsTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {
  it("deduplicates subjects") {
    // This is based on b2506728x.  The different second indicators
    // tell us these are MESH/LCSH concepts, but because we don't expose
    // those identifiers both varfields create the same subject.
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("650"),
          indicator2 = Some("0"),
          subfields = List(MarcSubfield(tag = "a", content = "Medicine"))
        ),
        VarField(
          marcTag = Some("650"),
          indicator2 = Some("2"),
          subfields = List(MarcSubfield(tag = "a", content = "Medicine"))
        )
      )
    )

    SierraSubjects(createSierraBibNumber, bibData) shouldBe List(
      Subject(
        label = "Medicine",
        concepts = List(Concept("Medicine"))
      )
    )
  }
}

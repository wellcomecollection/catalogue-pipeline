package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Concept, Subject}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

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
          subfields = List(Subfield(tag = "a", content = "Medicine"))
        ),
        VarField(
          marcTag = Some("650"),
          indicator2 = Some("2"),
          subfields = List(Subfield(tag = "a", content = "Medicine"))
        )
      )
    )

    SierraSubjects(createSierraBibNumber, bibData) shouldBe List(
      Subject(
        id = IdState.Identifiable(
          SourceIdentifier(
            identifierType = IdentifierType.LabelDerived,
            ontologyType = "Subject",
            value = "Medicine"
          )),
        label = "Medicine",
        concepts = List(Concept("Medicine"))
      )
    )
  }

  it("identifies a subject from its concept") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("650"),
          indicator2 = Some("0"),
          subfields = List(
            Subfield(tag = "a", content = "Medicine"),
            Subfield(tag = "0", content = "sh85083064")
          )
        )
      )
    )

    SierraSubjects(createSierraBibNumber, bibData) shouldBe List(
      Subject(
        id = IdState.Identifiable(
          SourceIdentifier(
            identifierType = IdentifierType.LCSubjects,
            ontologyType = "Subject",
            value = "sh85083064"
          )),
        label = "Medicine",
        concepts = List(Concept("Medicine")),
      )
    )
  }

}

package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.sierra.generators.MarcGenerators
import weco.sierra.models.marc.Subfield

class SierraConceptsTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with TableDrivenPropertyChecks {
  private val transformer = new SierraConcepts {}

  it("extracts identifiers from subfield 0") {
    val maybeIdentifiedConcept = transformer.identifyConcept(
      ontologyType = "Concept",
      varField = createVarFieldWith(
        marcTag = "CCC",
        indicator2 = "0",
        subfields = List(
          Subfield(tag = "a", content = "pilots"),
          Subfield(tag = "0", content = "lcsh/ppp")
        )
      )
    )

    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.LCSubjects,
      value = "lcsh/ppp",
      ontologyType = "Concept"
    )

    maybeIdentifiedConcept shouldBe IdState.Identifiable(sourceIdentifier)
  }

  it("creates a label-derived identifier for concepts with no identifier") {
    val maybeIdentifiedConcept = transformer.identifyConcept(
      ontologyType = "Concept",
      varField = createVarFieldWith(
        marcTag = "CCC",
        indicator2 = "0",
        subfields = List(
          Subfield(tag = "a", content = "WhoKnows")
        )
      )
    )
    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.LabelDerived,
      value = "WhoKnows",
      ontologyType = "Concept"
    )

    maybeIdentifiedConcept shouldBe IdState.Identifiable(sourceIdentifier)
  }

  it("normalises and deduplicates identifiers in subfield 0") {
    val maybeIdentifiedConcept = transformer.identifyConcept(
      ontologyType = "Concept",
      varField = createVarFieldWith(
        marcTag = "CCC",
        indicator2 = "0",
        subfields = List(
          Subfield(tag = "a", content = "martians"),
          Subfield(tag = "0", content = "lcsh/bbb"),
          Subfield(tag = "0", content = "lcsh/bbb"),
          // Including the (DNLM) prefix
          Subfield(tag = "0", content = "(DNLM)lcsh/bbb"),
          // With trailing punctuation
          Subfield(tag = "0", content = "lcsh/bbb."),
          // Including whitespace
          Subfield(tag = "0", content = "lcsh / bbb"),
          // Including a MESH URL prefix
          Subfield(tag = "0", content = "https://id.nlm.nih.gov/mesh/lcsh/bbb")
        )
      )
    )

    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.LCSubjects,
      value = "lcsh/bbb",
      ontologyType = "Concept"
    )

    maybeIdentifiedConcept shouldBe IdState.Identifiable(sourceIdentifier)
  }

  it("ignores multiple instances of subfield 0 in the otherIdentifiers") {
    val maybeIdentifiedConcept = transformer.identifyConcept(
      ontologyType = "Concept",
      varField = createVarFieldWith(
        marcTag = "CCC",
        indicator2 = "0",
        subfields = List(
          Subfield(tag = "a", content = "hitchhiking"),
          Subfield(tag = "0", content = "lcsh/xxx"),
          Subfield(tag = "0", content = "lcsh/yyy")
        )
      )
    )

    maybeIdentifiedConcept shouldBe IdState.Unidentifiable
  }

  it("ignores unknown schemes") {
    forAll(
      Table(
        "indicator2",
        None,
        Some("1"),
        Some("3"),
        Some("4"),
        Some("5"),
        Some("6"),
        Some("7")
      )
    ) { indicator2 =>
      val maybeIdentifiedConcept = transformer.identifyConcept(
        ontologyType = "Concept",
        varField = createVarFieldWith(
          marcTag = "CCC",
          indicator2 = indicator2,
          subfields = List(
            Subfield(tag = "a", content = "hitchhiking"),
            Subfield(tag = "0", content = "dunno/xxx"),
          )
        )
      )

      maybeIdentifiedConcept shouldBe IdState.Unidentifiable
    }

  }

}

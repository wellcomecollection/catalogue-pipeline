package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.Concept
import weco.sierra.generators.MarcGenerators
import weco.sierra.models.marc.Subfield

class SierraConceptsTest extends AnyFunSpec with Matchers with MarcGenerators {

  it("extracts identifiers from subfield 0") {
    val concept =
      Concept(label = "Perservering puffins push past perspiration")

    val maybeIdentifiedConcept = transformer.identifyConcept(
      concept = concept,
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

  it("normalises and deduplicates identifiers in subfield 0") {
    val concept = Concept(label = "Metaphysical mice migrating to Mars")

    val maybeIdentifiedConcept = transformer.identifyConcept(
      concept = concept,
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
          Subfield(
            tag = "0",
            content = "https://id.nlm.nih.gov/mesh/lcsh/bbb")
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
    val concept = Concept(label = "Hitchhiking horses hurry home")

    val maybeIdentifiedConcept = transformer.identifyConcept(
      concept = concept,
      varField = createVarFieldWith(
        marcTag = "CCC",
        subfields = List(
          Subfield(tag = "a", content = "hitchhiking"),
          Subfield(tag = "0", content = "u/xxx"),
          Subfield(tag = "0", content = "u/yyy")
        )
      )
    )

    maybeIdentifiedConcept shouldBe IdState.Unidentifiable
  }

  val transformer = new SierraConcepts {}
}

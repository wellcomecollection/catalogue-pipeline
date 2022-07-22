package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.pipeline.transformer.sierra.transformers.matchers.{ConceptMatchers, HasIdMatchers, SubjectMatchers}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraSubjectsTest
    extends AnyFunSpec
    with Matchers
    with SubjectMatchers
    with ConceptMatchers
      with HasIdMatchers
    with SierraDataGenerators {
  it("deduplicates identical subjects") {
    // This is based on b2506728x.  The different second indicators
    // tell us these are MESH/LCSH concepts, but they do not have the $0 subfield
    // so they create identical subjects, which are then deduplicated.
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("650"),
          indicator2 = Some("0"),
          subfields = List(
            Subfield(tag = "a", content = "Medicine")
          )
        ),
        VarField(
          marcTag = Some("650"),
          indicator2 = Some("2"),
          subfields = List(
            Subfield(tag = "a", content = "Medicine.")
          )
        )
      )
    )
    val List(subject) = SierraSubjects(createSierraBibNumber, bibData)
    subject should have(
      'label ("Medicine"),
      labelDerivedSubjectId("Medicine")
    )
    val List(concept) = subject.concepts

    concept should have(
      'label ("Medicine"),
      labelDerivedConceptId("Medicine")
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
    val List(subject) = SierraSubjects(createSierraBibNumber, bibData)
    subject should have(
      'label ("Medicine"),
      sourceIdentifier(value="sh85083064", ontologyType="Subject", identifierType=IdentifierType.LCSubjects)
    )

    val List(concept) = subject.concepts
    concept should have(
      'label ("Medicine"),
      sourceIdentifier(value="sh85083064", ontologyType="Concept", identifierType=IdentifierType.LCSubjects)
    )
  }

}

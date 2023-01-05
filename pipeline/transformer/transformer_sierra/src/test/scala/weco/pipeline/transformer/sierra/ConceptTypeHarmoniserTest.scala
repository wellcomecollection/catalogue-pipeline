package weco.pipeline.transformer.sierra

import org.scalatest.Inspectors
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.Subject

class ConceptTypeHarmoniserTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks
    with Inspectors {
  describe("Harmonising subjects") {
    it("replaces vague concepts with more specific ones") {
      forAll(
        Table(
          ("vagueType", "specificType"),
          ("Agent", "Person"),
          ("Concept", "Place"),
          ("Agent", "Organisation"),
          ("Concept", "Period"),
          // The "vague" type does not necessarily have to be in a strict
          // hierarchical relationship with the "specific" type, it
          // just has to be more vague.
          ("Concept", "Person"),
          ("Agent", "Place"),
          // Agent is more specific than Concept, even though it is
          // itself "vague"
          ("Concept", "Agent")
        )
      ) { (vagueType, specificType) =>
        val vagueSubject = new Subject(
          label = "Maimonides, in his work on Logic",
          id = Identifiable(
            SourceIdentifier(
              IdentifierType.LCSubjects,
              vagueType,
              "sh00000000"
            )
          )
        )
        val specificSubject = new Subject(
          label = "Maimonides",
          id = Identifiable(
            SourceIdentifier(
              IdentifierType.LCSubjects,
              specificType,
              "sh00000000"
            )
          )
        )
        val (subjects, _) =
          ConceptTypeHarmoniser(
            subjects = List(specificSubject, vagueSubject),
            contributors = Nil
          )
        // Both subjects should be represented in the output list.  They have different labels, so are different objects.
        subjects.length shouldBe 2

        forAll(subjects) { subject =>
          subject
            .asInstanceOf[Subject[IdState.Identifiable]]
            .id
            .sourceIdentifier
            .ontologyType shouldBe specificType
        }
      }
    }
  }

  it("collapses subjects that are no longer unique") {
    val vagueSubject = new Subject(
      label = "Maimonides",
      id = Identifiable(
        SourceIdentifier(
          IdentifierType.LCSubjects,
          "Concept",
          "sh00000000"
        )
      )
    )
    val specificSubject1 = new Subject(
      label = "Maimonides",
      id = Identifiable(
        SourceIdentifier(
          IdentifierType.LCSubjects,
          "Person",
          "sh00000000"
        )
      )
    )
    val specificSubject2 = new Subject(
      label = "Maimonides",
      id = Identifiable(
        SourceIdentifier(
          IdentifierType.LCSubjects,
          "Organisation",
          "sh00000000"
        )
      )
    )
    val (subjects, _) =
      ConceptTypeHarmoniser(
        subjects = List(specificSubject1, specificSubject2, vagueSubject),
        contributors = Nil
      )
    // They all had teh same label before, so they are no longer unique having harmonised their types
    subjects.length shouldBe 1

    subjects.head
      .asInstanceOf[Subject[IdState.Identifiable]]
      .id
      .sourceIdentifier
      .ontologyType shouldBe "Person"

  }

  it("chooses the first specific concept if there are more than one") {
    val vagueSubject = new Subject(
      label = "Maimonides, in his work on Logic",
      id = Identifiable(
        SourceIdentifier(
          IdentifierType.LCSubjects,
          "Concept",
          "sh00000000"
        )
      )
    )
    val specificSubject1 = new Subject(
      label = "Maimonides",
      id = Identifiable(
        SourceIdentifier(
          IdentifierType.LCSubjects,
          "Person",
          "sh00000000"
        )
      )
    )
    val specificSubject2 = new Subject(
      label = "Maimonides and his chums",
      id = Identifiable(
        SourceIdentifier(
          IdentifierType.LCSubjects,
          "Organisation",
          "sh00000000"
        )
      )
    )
    val (subjects, _) =
      ConceptTypeHarmoniser(
        subjects = List(specificSubject1, specificSubject2, vagueSubject),
        contributors = Nil
      )

    subjects.length shouldBe 3

    forAll(subjects) { subject =>
      subject
        .asInstanceOf[Subject[IdState.Identifiable]]
        .id
        .sourceIdentifier
        .ontologyType shouldBe "Person"
    }
  }

}

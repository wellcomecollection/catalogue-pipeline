package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.Inspectors
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.pipeline.transformer.sierra.transformers.matchers.{
  ConceptMatchers,
  HasIdMatchers,
  SubjectMatchers
}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{IdState, SourceIdentifier}
import weco.catalogue.internal_model.work.{Concept, Person, Place, Subject}

class SierraSubjectsTest
    extends AnyFunSpec
    with Matchers
    with SubjectMatchers
    with ConceptMatchers
    with HasIdMatchers
    with SierraDataGenerators
    with TableDrivenPropertyChecks
    with Inspectors {
  import weco.pipeline.transformer.marc_common.OntologyTypeOps._

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
      'label("Medicine"),
      labelDerivedConceptId("medicine")
    )
    val List(concept) = subject.concepts

    concept should have(
      'label("Medicine"),
      labelDerivedConceptId("medicine")
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
      'label("Medicine"),
      sourceIdentifier(
        value = "sh85083064",
        ontologyType = "Concept",
        identifierType = IdentifierType.LCSubjects
      )
    )

    val List(concept) = subject.concepts
    concept should have(
      'label("Medicine"),
      sourceIdentifier(
        value = "sh85083064",
        ontologyType = "Concept",
        identifierType = IdentifierType.LCSubjects
      )
    )
  }

  describe("Harmonising subject types") {
    it("replaces vague concepts with more specific ones with matching ids") {
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
      ) {
        (vagueType, specificType) =>
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
          val subjects =
            List(specificSubject, vagueSubject).harmoniseOntologyTypes
          // Both subjects should be represented in the output list.  They have different labels, so are different objects.
          subjects.length shouldBe 2

          forAll(subjects) {
            subject =>
              subject
                .asInstanceOf[Subject[IdState.Identifiable]]
                .id
                .sourceIdentifier
                .ontologyType shouldBe specificType
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
      val subjects =
        List(
          specificSubject1,
          specificSubject2,
          vagueSubject
        ).harmoniseOntologyTypes
      // They all had the same label before, so they are no longer unique having harmoniseOntologyTypesd their types
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
      val subjects =
        List(
          specificSubject1,
          specificSubject2,
          vagueSubject
        ).harmoniseOntologyTypes

      subjects.length shouldBe 3

      forAll(subjects) {
        subject =>
          subject
            .asInstanceOf[Subject[IdState.Identifiable]]
            .id
            .sourceIdentifier
            .ontologyType shouldBe "Person"
      }
    }

    it("corrects the type of the sole concept in the concepts list") {
      val conceptId = Identifiable(
        SourceIdentifier(
          IdentifierType.LCSubjects,
          "Concept",
          "sh00000000"
        )
      )
      val personId = Identifiable(
        SourceIdentifier(
          IdentifierType.LCSubjects,
          "Person",
          "sh00000000"
        )
      )

      val vagueSubject = new Subject(
        label = "The homunculus in the mind of Maimonides",
        id = conceptId,
        concepts =
          List(new Concept(id = conceptId, label = "Maimonides' homunculus"))
      )

      val specificSubject = new Subject(
        label = "Maimonides",
        id = personId,
        concepts = List(new Person(id = personId, label = "Maimonides"))
      )
      val subjects = List(specificSubject, vagueSubject).harmoniseOntologyTypes
      subjects.length shouldBe 2

      forAll(subjects) {
        subject =>
          subject.concepts.head
            .asInstanceOf[Person[IdState.Identifiable]]
            .id
            .sourceIdentifier
            .ontologyType shouldBe "Person"
      }
    }

    it("leaves the label-derived concept list in a compound subject alone") {
      val conceptId = Identifiable(
        SourceIdentifier(
          IdentifierType.LCSubjects,
          "Concept",
          "sh00000000"
        )
      )
      val personId = Identifiable(
        SourceIdentifier(
          IdentifierType.LCSubjects,
          "Person",
          "sh00000000"
        )
      )

      val vagueSubject = new Subject(
        label = "The homunculus in the mind of Maimonides",
        id = conceptId,
        concepts = List(
          new Concept(
            id = IdState.Identifiable(
              sourceIdentifier = SourceIdentifier(
                identifierType = IdentifierType.LabelDerived,
                value = "Maimonides",
                ontologyType = "Concept"
              )
            ),
            label = "Maimonides"
          ),
          new Concept(
            id = IdState.Identifiable(
              sourceIdentifier = SourceIdentifier(
                identifierType = IdentifierType.LabelDerived,
                value = "homunculus",
                ontologyType = "Concept"
              )
            ),
            label = "homunculus"
          )
        )
      )

      val specificSubject = new Subject(
        label = "Maimonides of Cordoba",
        id = personId,
        concepts = List(Person("Maimonides"), Place("Cordoba"))
      )

      val subjects = List(vagueSubject, specificSubject).harmoniseOntologyTypes

      subjects.length shouldBe 2
      // The vague subject should be changed to be Person
      forAll(subjects) {
        subject =>
          subject
            .asInstanceOf[Subject[IdState.Identifiable]]
            .id
            .sourceIdentifier
            .ontologyType shouldBe "Person"
      }
      // But the concepts list is left unchanged.
      forAll(subjects.head.concepts) {
        concept =>
          concept
            .asInstanceOf[Concept[IdState.Identifiable]]
            .id
            .sourceIdentifier
            .ontologyType shouldBe "Concept"
      }

    }
  }
}

package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal._

trait SubjectGenerators extends RandomStrings {
  def createSubjectWith(label: String = randomAlphanumeric(10),
                        concepts: List[Minted[AbstractRootConcept]] =
                          createConcepts())
    : Minted[Subject[Minted[AbstractRootConcept]]] =
    Unidentifiable(
      Subject(
        label = label,
        concepts = concepts
      )
    )

  def createSubjectWithConcept(label: String = randomAlphanumeric(10),
                               conceptString: String = randomAlphanumeric(8))
    : Minted[Subject[Minted[AbstractRootConcept]]] =
    Unidentifiable(
      Subject(
        label = label,
        concepts = createConcepts(List(conceptString))
      )
    )

  def createSubject: Minted[Subject[Minted[AbstractRootConcept]]] =
    createSubjectWith()

  private def createConcepts(
    conceptStrings: Seq[String] = List.fill(3)(randomAlphanumeric(15)))
    : List[Minted[AbstractRootConcept]] =
    conceptStrings
      .map { concept: String =>
        Unidentifiable(Concept(concept))
      }
      .toList
      .asInstanceOf[List[Minted[AbstractRootConcept]]]
}

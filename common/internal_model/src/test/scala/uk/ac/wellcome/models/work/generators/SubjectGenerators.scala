package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal._

trait SubjectGenerators extends RandomStrings {
  def createSubjectWith(label: String = randomAlphanumeric(10),
                        concepts: List[AbstractRootConcept[Id.Minted]] =
                          createConcepts()): Subject[Id.Minted] =
    Subject(
      id = Id.Unidentifiable,
      label = label,
      concepts = concepts
    )

  def createSubjectWithConcept(
    label: String = randomAlphanumeric(10),
    conceptString: String = randomAlphanumeric(8)): Subject[Id.Minted] =
    Subject(
      id = Id.Unidentifiable,
      label = label,
      concepts = createConcepts(List(conceptString))
    )

  def createSubjectWithMatchingConcept(
    label: String = randomAlphanumeric(10),
  ): Subject[Id.Minted] =
    Subject(
      id = Id.Unidentifiable,
      label = label,
      concepts = createConcepts(List(label))
    )

  def createSubject: Subject[Id.Minted] =
    createSubjectWith()

  private def createConcepts(
    conceptStrings: List[String] = List.fill(3)(randomAlphanumeric(15)))
    : List[AbstractRootConcept[Id.Minted]] =
    conceptStrings.map(Concept(_))
}

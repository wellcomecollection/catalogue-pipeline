package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.fixtures.RandomGenerators
import uk.ac.wellcome.models.work.internal._
import weco.catalogue.internal_model.identifiers.IdState

trait SubjectGenerators extends RandomGenerators {
  def createSubjectWith(label: String = randomAlphanumeric(10),
                        concepts: List[AbstractRootConcept[IdState.Minted]] =
                          createConcepts()): Subject[IdState.Minted] =
    Subject(
      id = IdState.Unidentifiable,
      label = label,
      concepts = concepts
    )

  def createSubjectWithMatchingConcept(
    label: String = randomAlphanumeric(10),
  ): Subject[IdState.Minted] =
    Subject(
      id = IdState.Unidentifiable,
      label = label,
      concepts = createConcepts(List(label))
    )

  def createSubject: Subject[IdState.Minted] =
    createSubjectWith()

  private def createConcepts(
    conceptStrings: List[String] = List.fill(3)(randomAlphanumeric(15)))
    : List[AbstractRootConcept[IdState.Minted]] =
    conceptStrings.map(Concept(_))
}

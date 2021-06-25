package weco.catalogue.internal_model.work.generators

import weco.fixtures.RandomGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractConcept, Concept, Genre}

trait GenreGenerators extends RandomGenerators {

  def createGenreWith(
    label: String = randomAlphanumeric(10),
    concepts: List[AbstractConcept[IdState.Minted]] = createConcepts
  ): Genre[IdState.Minted] =
    Genre(label = label, concepts = concepts)

  def createGenre: Genre[IdState.Minted] =
    createGenreWith()

  // We use this as genres _generally_ have 1 concept matching the base label
  def createGenreWithMatchingConcept(label: String): Genre[IdState.Minted] =
    createGenreWith(label, concepts = List(Concept(label = label)))

  private def createConcepts: List[AbstractConcept[IdState.Minted]] =
    (1 to 3)
      .map { _ =>
        Concept(randomAlphanumeric(15))
      }
      .toList
      .asInstanceOf[List[AbstractConcept[IdState.Minted]]]
}

package weco.catalogue.internal_model.work.generators

import weco.fixtures.RandomGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractConcept, Genre}

trait GenreGenerators
    extends IdentifiedConceptGenerators
    with RandomGenerators {

  def createGenreWith(
    label: String = randomAlphanumeric(10),
    concepts: List[AbstractConcept[IdState.Minted]] = createConcepts
  ): Genre[IdState.Minted] =
    Genre(label = label, concepts = concepts)

  def createGenre: Genre[IdState.Minted] =
    createGenreWith()

  private def createConcepts: List[AbstractConcept[IdState.Minted]] =
    List(createGenreConcept(), createConcept(), createConcept())
}

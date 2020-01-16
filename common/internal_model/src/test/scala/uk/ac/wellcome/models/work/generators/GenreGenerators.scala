package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal._

trait GenreGenerators extends RandomStrings {

  def createGenreWith(
    label: String = randomAlphanumeric(10),
    concepts: List[AbstractConcept[Minted]] = createConcepts): Genre[Minted] =
    Genre(label = label, concepts = concepts)

  def createGenre: Genre[Minted] =
    createGenreWith()

  private def createConcepts: List[AbstractConcept[Minted]] =
    (1 to 3)
      .map { _ => Concept(randomAlphanumeric(15)) }
      .toList
      .asInstanceOf[List[AbstractConcept[Minted]]]
}

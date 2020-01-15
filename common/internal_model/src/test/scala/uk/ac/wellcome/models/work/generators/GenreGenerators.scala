package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal._

trait GenreGenerators extends RandomStrings {
  def createGenreWith(label: String = randomAlphanumeric(10),
                      concepts: List[Minted[AbstractConcept]] =
                        createConcepts): Genre[Minted[AbstractConcept]] =
    Genre(
      label = label,
      concepts = concepts
    )

  def createGenre: Genre[Minted[AbstractConcept]] =
    createGenreWith()

  private def createConcepts: List[Minted[AbstractConcept]] =
    (1 to 3)
      .map { _ =>
        Unidentifiable(Concept(randomAlphanumeric(15)))
      }
      .toList
      .asInstanceOf[List[Minted[AbstractConcept]]]
}

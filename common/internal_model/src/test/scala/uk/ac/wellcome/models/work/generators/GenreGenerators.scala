package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal._

trait GenreGenerators extends RandomStrings {
  def createGenreWith(label: String = randomAlphanumeric(10),
                      concepts: List[Displayable[AbstractConcept]] =
                        createConcepts): Genre[Displayable[AbstractConcept]] =
    Genre(
      label = label,
      concepts = concepts
    )

  def createGenre: Genre[Displayable[AbstractConcept]] =
    createGenreWith()

  private def createConcepts: List[Displayable[AbstractConcept]] =
    (1 to 3)
      .map { _ =>
        Unidentifiable(Concept(randomAlphanumeric(15)))
      }
      .toList
      .asInstanceOf[List[Displayable[AbstractConcept]]]
}

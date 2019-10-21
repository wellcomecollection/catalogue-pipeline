package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{AbstractConcept, Displayable, Genre}

@Schema(
  name = "Genre",
  description = "A genre"
)
case class DisplayGenre(
  @Schema(description = "A label given to a thing.") label: String,
  @Schema(description = "Relates a genre to a list of concepts.") concepts: List[
    DisplayAbstractConcept],
  @JsonKey("type") ontologyType: String = "Genre"
)

object DisplayGenre {
  def apply(genre: Genre[Displayable[AbstractConcept]],
            includesIdentifiers: Boolean): DisplayGenre =
    DisplayGenre(label = genre.label, concepts = genre.concepts.map {
      DisplayAbstractConcept(_, includesIdentifiers = includesIdentifiers)
    }, ontologyType = genre.ontologyType)
}

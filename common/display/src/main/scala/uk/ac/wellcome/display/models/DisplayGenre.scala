package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{Genre, IdState}

@Schema(
  name = "Genre",
  description = "A genre"
)
case class DisplayGenre(
  @Schema(description = "A label given to a thing.") label: String,
  @Schema(description = "Relates a genre to a list of concepts.") concepts: List[
    DisplayAbstractConcept],
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Genre"
)

object DisplayGenre {
  def apply(genre: Genre[IdState.Minted], includesIdentifiers: Boolean): DisplayGenre =
    DisplayGenre(label = genre.label, concepts = genre.concepts.map {
      DisplayAbstractConcept(_, includesIdentifiers = includesIdentifiers)
    }, ontologyType = genre.ontologyType)
}

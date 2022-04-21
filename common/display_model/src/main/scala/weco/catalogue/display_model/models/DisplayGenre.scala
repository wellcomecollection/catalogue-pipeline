package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Genre

case class DisplayGenre(
  label: String,
  concepts: List[DisplayAbstractConcept],
  @JsonKey("type") ontologyType: String = "Genre"
)

object DisplayGenre {
  def apply(
    genre: Genre[IdState.Minted],
    includesIdentifiers: Boolean
  ): DisplayGenre =
    DisplayGenre(
      label = genre.label,
      concepts = genre.concepts.map {
        DisplayAbstractConcept(_, includesIdentifiers = includesIdentifiers)
      }
    )
}

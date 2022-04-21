package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.ImageData

case class DisplayWorkImageInclude(
  id: String,
  @JsonKey("type") ontologyType: String = "Image"
)

object DisplayWorkImageInclude {
  def apply(image: ImageData[IdState.Identified]): DisplayWorkImageInclude =
    new DisplayWorkImageInclude(id = image.id.canonicalId.underlying)
}

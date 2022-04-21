package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey

case class DisplayItemStatus(
  id: String,
  label: String,
  @JsonKey("type") ontologyType: String = "ItemStatus"
)

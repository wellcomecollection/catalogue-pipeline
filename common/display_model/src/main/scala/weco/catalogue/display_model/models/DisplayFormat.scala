package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.work.Format

case class DisplayFormat(
  id: String,
  label: String,
  @JsonKey("type") ontologyType: String = "Format"
)

case object DisplayFormat {
  def apply(format: Format): DisplayFormat = DisplayFormat(
    id = format.id,
    label = format.label
  )
}

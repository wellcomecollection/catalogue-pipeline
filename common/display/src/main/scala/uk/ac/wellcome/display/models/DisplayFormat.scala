package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.Format

@Schema(
  name = "Format",
  description =
    "A broad, top-level description of the form of a work: namely, whether it is a printed book, archive, painting, photograph, moving image, etc."
)
case class DisplayFormat(
  @Schema(
    `type` = "String"
  ) id: String,
  @Schema(
    `type` = "String"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Format"
)

case object DisplayFormat {
  def apply(format: Format): DisplayFormat = DisplayFormat(
    id = format.id,
    label = format.label
  )
}

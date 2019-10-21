package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.WorkType

@Schema(
  name = "WorkType",
  description =
    "A broad, top-level description of the form of a work: namely, whether it is a printed book, archive, painting, photograph, moving image, etc."
)
case class DisplayWorkType(
  @Schema(
    `type` = "String"
  ) id: String,
  @Schema(
    `type` = "String"
  ) label: String,
  @JsonKey("type") ontologyType: String = "WorkType"
)

case object DisplayWorkType {
  def apply(workType: WorkType): DisplayWorkType = DisplayWorkType(
    id = workType.id,
    label = workType.label
  )
}

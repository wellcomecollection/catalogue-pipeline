package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.AugmentedImage

@Schema(
  name = "Image",
  description = "An image"
)
case class DisplayImage(
  @Schema(
    accessMode = Schema.AccessMode.READ_ONLY,
    description = "The canonical identifier given to a thing."
  ) id: String,
  @Schema(
    `type` = "uk.ac.wellcome.display.models.v2.DisplayDigitalLocationV2",
    description = "The location which provides access to the image"
  ) location: DisplayDigitalLocationV2,
  @Schema(
    description = "The work to which the image relates"
  ) parentWork: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Image"
)

object DisplayImage {

  def apply(image: AugmentedImage): DisplayImage =
    new DisplayImage(
      id = image.id.canonicalId,
      location = DisplayDigitalLocationV2(image.location),
      parentWork = image.parentWork.canonicalId
    )

}

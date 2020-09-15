package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{IdState, ImageSource, WorkState}

@Schema(
  name = "ImageSource",
  description = "A description of the entity from which an image was sourced"
)
case class DisplayImageSource(
  @Schema(
    description = "Identifer of the image source"
  ) id: String,
  @JsonKey("type") @Schema(
    name = "type",
    description = "What kind of source this is"
  ) ontologyType: String
)

object DisplayImageSource {

  def apply(imageSource: ImageSource[IdState.Identified, WorkState.Identified])
    : DisplayImageSource =
    new DisplayImageSource(
      id = imageSource.id.canonicalId,
      ontologyType = imageSource.ontologyType
    )
}

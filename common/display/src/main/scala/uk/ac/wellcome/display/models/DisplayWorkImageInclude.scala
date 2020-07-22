package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{Identified, Minted, UnmergedImage}

@Schema(
  name = "Image",
  description = "A partial Image included on a work"
)
case class DisplayWorkImageInclude(
  @Schema(description = "The image ID") id: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Image"
)

object DisplayWorkImageInclude {
  def apply(image: UnmergedImage[Identified, Minted]): DisplayWorkImageInclude =
    new DisplayWorkImageInclude(id = image.id.canonicalId)
}

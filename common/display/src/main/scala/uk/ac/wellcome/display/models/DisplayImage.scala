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
    `type` = "uk.ac.wellcome.Display.models.DisplayDigitalLocation",
    description = "The locations which provide access to the image"
  ) locations: Seq[DisplayDigitalLocationDeprecated],
  @Schema(
    `type` = "uk.ac.wellcome.Display.models.DisplayImageSource",
    description = "A description of the image's source"
  ) source: DisplayImageSource,
  @Schema(
    `type` = "Image",
    description = "A list of visually similar images"
  ) visuallySimilar: Option[Seq[DisplayImage]],
  @Schema(
    `type` = "Image",
    description = "A list of images with similar color palettes"
  ) withSimilarColors: Option[Seq[DisplayImage]],
  @Schema(
    `type` = "Image",
    description = "A list of images with similar features"
  ) withSimilarFeatures: Option[Seq[DisplayImage]],
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Image"
)

object DisplayImage {

  def apply(image: AugmentedImage): DisplayImage =
    new DisplayImage(
      id = image.id.canonicalId,
      locations = Seq(DisplayDigitalLocationDeprecated(image.location)),
      source = DisplayImageSource(image.source),
      visuallySimilar = None,
      withSimilarColors = None,
      withSimilarFeatures = None,
    )

  def apply(image: AugmentedImage,
            visuallySimilar: Option[Seq[AugmentedImage]],
            withSimilarColors: Option[Seq[AugmentedImage]],
            withSimilarFeatures: Option[Seq[AugmentedImage]]): DisplayImage =
    DisplayImage(image).copy(
      visuallySimilar = visuallySimilar.map(_.map(DisplayImage.apply)),
      withSimilarColors = withSimilarColors.map(_.map(DisplayImage.apply)),
      withSimilarFeatures = withSimilarFeatures.map(_.map(DisplayImage.apply)),
    )
}

package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import weco.catalogue.internal_model.image.{Image, ImageState}

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
    description =
      "The location which provides access to an image that can be displayed directly as a thumbnnail"
  ) thumbnail: DisplayDigitalLocation,
  @Schema(
    `type` = "uk.ac.wellcome.Display.models.DisplayDigitalLocation",
    description = "The locations which provide access to the image"
  ) locations: Seq[DisplayDigitalLocation],
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

  def apply(image: Image[ImageState.Indexed],
            includes: ImageIncludes): DisplayImage =
    new DisplayImage(
      id = image.id,
      thumbnail = DisplayDigitalLocation(image.state.derivedData.thumbnail),
      locations = image.locations.map(DisplayDigitalLocation(_)),
      source = DisplayImageSource(image.source, includes),
      visuallySimilar = None,
      withSimilarColors = None,
      withSimilarFeatures = None,
    )

  def apply(
    image: Image[ImageState.Indexed],
    includes: ImageIncludes,
    visuallySimilar: Option[Seq[Image[ImageState.Indexed]]],
    withSimilarColors: Option[Seq[Image[ImageState.Indexed]]],
    withSimilarFeatures: Option[Seq[Image[ImageState.Indexed]]]): DisplayImage =
    DisplayImage(image, includes).copy(
      visuallySimilar =
        visuallySimilar.map(_.map(DisplayImage.apply(_, includes))),
      withSimilarColors =
        withSimilarColors.map(_.map(DisplayImage.apply(_, includes))),
      withSimilarFeatures =
        withSimilarFeatures.map(_.map(DisplayImage.apply(_, includes))),
    )
}

package weco.catalogue.display_model.image

import io.circe.generic.extras.JsonKey
import weco.catalogue.display_model.locations.DisplayDigitalLocation
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.catalogue.internal_model.locations.{DigitalLocation, LocationType}

case class DisplayImage(
  id: String,
  thumbnail: DisplayDigitalLocation,
  locations: Seq[DisplayDigitalLocation],
  aspectRatio: Float,
  averageColor: String,
  source: DisplayImageSource,
  @JsonKey("type") ontologyType: String = "Image"
)

object DisplayImage {
  private def thumbnail(image: Image[_]): DigitalLocation =
    image.locations
      .find(_.locationType == LocationType.IIIFImageAPI)
      .getOrElse(
        // This should never happen
        throw new RuntimeException(
          s"No iiif-image (thumbnail) location found on image ${image.sourceIdentifier}"
        )
      )

  def apply(image: Image[ImageState.Augmented]): DisplayImage =
    new DisplayImage(
      id = image.id,
      thumbnail = DisplayDigitalLocation(thumbnail(image)),
      locations = image.locations.map(DisplayDigitalLocation(_)),
      aspectRatio = image.state.inferredData.aspectRatio.getOrElse(1.0F),
      averageColor =
        image.state.inferredData.averageColorHex.getOrElse("#ffffff"),
      source = DisplayImageSource(image.source)
    )
}

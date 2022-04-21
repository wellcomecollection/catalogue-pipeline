package weco.catalogue.display_model.image

import io.circe.generic.extras.JsonKey
import weco.catalogue.display_model.locations.DisplayDigitalLocation
import weco.catalogue.display_model.models.ImageIncludes
import weco.catalogue.internal_model.image.{Image, ImageState}

case class DisplayImage(
  id: String,
  thumbnail: DisplayDigitalLocation,
  locations: Seq[DisplayDigitalLocation],
  source: DisplayImageSource,
  @JsonKey("type") ontologyType: String = "Image"
)

object DisplayImage {
  def apply(image: Image[ImageState.Indexed]): DisplayImage =
    new DisplayImage(
      id = image.id,
      thumbnail = DisplayDigitalLocation(image.state.derivedData.thumbnail),
      locations = image.locations.map { DisplayDigitalLocation(_) },
      source = DisplayImageSource(image.source),
    )
}

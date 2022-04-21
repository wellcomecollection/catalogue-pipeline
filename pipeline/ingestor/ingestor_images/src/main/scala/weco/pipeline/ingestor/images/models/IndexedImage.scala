package weco.pipeline.ingestor.images.models

import java.time.Instant
import weco.catalogue.display_model.image.DisplayImage
import weco.catalogue.internal_model.image.{ImageSource, ImageState}
import weco.catalogue.internal_model.locations.DigitalLocation

case class IndexedImage(
  version: Int,
  state: ImageState.Indexed,
  locations: List[DigitalLocation],
  source: ImageSource,
  modifiedTime: Instant,
  display: DisplayImage
)

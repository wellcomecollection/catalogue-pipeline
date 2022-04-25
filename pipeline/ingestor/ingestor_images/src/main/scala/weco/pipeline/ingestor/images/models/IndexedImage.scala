package weco.pipeline.ingestor.images.models

import io.circe.Json

import java.time.Instant
import weco.catalogue.internal_model.image.{ImageSource, ImageState}
import weco.catalogue.internal_model.locations.DigitalLocation

case class IndexedImage(
  version: Int,
  state: ImageState.Indexed,
  locations: List[DigitalLocation],
  source: ImageSource,
  modifiedTime: Instant,
  display: Json
)

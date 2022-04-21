package weco.pipeline.ingestor.images.models

import java.time.Instant
import weco.catalogue.display_model.image.DisplayImage
import weco.catalogue.internal_model.image.{ImageSource, ImageState}
import weco.catalogue.internal_model.locations.DigitalLocation
import weco.pipeline_storage.Indexable

case class IndexedImage(
  version: Int,
  state: ImageState.Indexed,
  locations: List[DigitalLocation],
  source: ImageSource,
  modifiedTime: Instant,
  display: DisplayImage
)

case object IndexedImage {
  implicit val indexable: Indexable[IndexedImage] =
    new Indexable[IndexedImage] {
      override def id(image: IndexedImage): String = image.state.canonicalId.underlying

      override def version(image: IndexedImage): Long = image.modifiedTime.toEpochMilli
    }
}

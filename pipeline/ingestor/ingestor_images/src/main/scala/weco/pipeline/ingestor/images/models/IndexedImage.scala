package weco.pipeline.ingestor.images.models

import io.circe.Json

import java.time.Instant
import weco.pipeline_storage.Indexable

case class IndexedImage(
  modifiedTime: Instant,
  display: Json,
  query: ImageQueryableValues,
  aggregatableValues: ImageAggregatableValues,
  debug: DebugInformation
)

case object IndexedImage {
  implicit val indexable: Indexable[IndexedImage] =
    new Indexable[IndexedImage] {
      override def id(image: IndexedImage): String =
        image.query.id

      override def version(image: IndexedImage): Long =
        image.modifiedTime.toEpochMilli
    }
}

package weco.pipeline.sierra_reader.sink

import akka.Done
import akka.stream.scaladsl.Sink
import io.circe.Json
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.s3.S3TypedStore

import scala.concurrent.Future

object SequentialS3Sink {
  def apply(
    store: S3TypedStore[String],
    bucketName: String,
    keyPrefix: String = "",
    offset: Int = 0
  ): Sink[(Json, Long), Future[Done]] =
    Sink.foreach {
      case (json: Json, index: Long) => {
        // Zero-pad the index to four digits for easy sorting,
        // e.g. "1" ~> "0001", "25" ~> "0025"
        val key = f"$keyPrefix${index + offset}%04d.json"
        store
          .put(S3ObjectLocation(bucketName, key))(json.noSpaces)
          .left
          .map(_.e)
          .toTry
          .get
      }
    }
}

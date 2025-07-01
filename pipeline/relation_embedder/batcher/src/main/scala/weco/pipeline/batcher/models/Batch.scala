package weco.pipeline.batcher.models

import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}
import weco.json.JsonUtil._

case class Batch(rootPath: String, selectors: List[Selector])

case object Batch {
  implicit val decoder: Decoder[Batch] =
    deriveConfiguredDecoder

  implicit val encoder: Encoder[Batch] =
    deriveConfiguredEncoder
}

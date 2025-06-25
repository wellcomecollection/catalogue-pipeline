package weco.pipeline.batcher

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._

case class Batch(rootPath: String, selectors: List[Selector])

case object Batch {
  implicit val decoder: Decoder[Batch] =
    deriveConfiguredDecoder

  implicit val encoder: Encoder[Batch] =
    deriveConfiguredEncoder
}

package weco.pipeline.transformer.miro

import io.circe.generic.extras.semiauto._
import io.circe._
import weco.json.JsonUtil._
import weco.pipeline.transformer.miro.source.MiroRecord

object Implicits {
  implicit val encoder: Encoder[MiroRecord] = deriveConfiguredEncoder
  implicit val decoder: Decoder[MiroRecord] = deriveConfiguredDecoder
}

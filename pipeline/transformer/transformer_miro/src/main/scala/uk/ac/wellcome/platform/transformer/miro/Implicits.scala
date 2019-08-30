package uk.ac.wellcome.platform.transformer.miro

import io.circe.generic.extras.semiauto._
import io.circe._

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord

object Implicits {

  implicit val encoder: Encoder[MiroRecord] = deriveEncoder
  implicit val decoder: Decoder[MiroRecord] = deriveDecoder
}

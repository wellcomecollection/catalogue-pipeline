package uk.ac.wellcome.sierra_adapter.model

import io.circe.generic.extras.semiauto._
import io.circe._
import uk.ac.wellcome.json.JsonUtil._
import SierraTransformable._

object Implicits {
  implicit val _dec01: Decoder[SierraTransformable] = deriveDecoder

  implicit val _enc01: Encoder[SierraTransformable] = deriveEncoder

}

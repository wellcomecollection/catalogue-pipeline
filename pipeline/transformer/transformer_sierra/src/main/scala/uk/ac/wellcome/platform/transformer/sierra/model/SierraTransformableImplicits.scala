package uk.ac.wellcome.platform.transformer.sierra.model

import io.circe._
import io.circe.generic.extras.semiauto._
import uk.ac.wellcome.json.JsonUtil._
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import uk.ac.wellcome.sierra_adapter.model.{SierraItemNumber, SierraTransformable}

// This is basically equivalent to uk.ac.wellcome.sierra_adapter.model.Implicits
// but it needs to be duplicated here because the sierra adapter uses circe 0.9.0
// and the sierra transformer uses circe 0.13.0 which are not compatible
// TODO: delete this one the sierra_adapter is updated
object SierraTransformableImplicits {

  implicit val keyEncoder: KeyEncoder[SierraItemNumber] =
    (key: SierraItemNumber) => key.withoutCheckDigit

  implicit val keyDecoder: KeyDecoder[SierraItemNumber] =
    (key: String) => Some(SierraItemNumber(key))

  implicit val _dec01: Decoder[SierraTransformable] = deriveConfiguredDecoder

  implicit val _enc01: Encoder[SierraTransformable] = deriveConfiguredEncoder
}

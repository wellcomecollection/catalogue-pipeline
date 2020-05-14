package uk.ac.wellcome.sierra_adapter.model

import io.circe.generic.extras.semiauto._
import io.circe._
import uk.ac.wellcome.json.JsonUtil._

object Implicits {
  // Because the [[SierraTransformable.itemRecords]] field is keyed by
  // [[SierraItemNumber]] in our case class, but JSON only supports string
  // keys, we need to turn the ID into a string when storing as JSON.
  //
  // This is based on the "Custom key types" section of the Circe docs:
  // https://circe.github.io/circe/codecs/custom-codecs.html#custom-key-types
  //
  implicit val keyEncoder: KeyEncoder[SierraItemNumber] =
    (key: SierraItemNumber) => key.withoutCheckDigit

  implicit val keyDecoder: KeyDecoder[SierraItemNumber] =
    (key: String) => Some(SierraItemNumber(key))

  implicit val _dec01: Decoder[SierraTransformable] = deriveConfiguredDecoder

  implicit val _enc01: Encoder[SierraTransformable] = deriveConfiguredEncoder
}

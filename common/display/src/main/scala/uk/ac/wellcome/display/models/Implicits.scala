package uk.ac.wellcome.display.models

import io.circe.generic.extras.semiauto._
import io.circe._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.display.models.v2._

object Implicits {

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required).

  implicit val _enc01: Encoder[DisplayLanguage] = deriveEncoder
  implicit val _enc02: Encoder[DisplayWorkType] = deriveEncoder
  implicit val _enc03: Encoder[DisplayPeriod] = deriveEncoder
  implicit val _enc04: Encoder[DisplayContributor] = deriveEncoder
  implicit val _enc05: Encoder[DisplayIdentifierV2] = deriveEncoder
  implicit val _enc06: Encoder[DisplaySubject] = deriveEncoder
  implicit val _enc07: Encoder[DisplayGenre] = deriveEncoder
  implicit val _enc08: Encoder[DisplayProductionEvent] = deriveEncoder
  implicit val _enc09: Encoder[DisplayItemV2] = deriveEncoder
  implicit val _enc10: Encoder[DisplayLocationV2] = deriveEncoder
  implicit val _enc11: Encoder[DisplayWorkV2] = deriveEncoder

  implicit val _dec01: Decoder[DisplayLanguage] = deriveDecoder
  implicit val _dec02: Decoder[DisplayWorkType] = deriveDecoder
  implicit val _dec03: Decoder[DisplayPeriod] = deriveDecoder
  implicit val _dec04: Decoder[DisplayContributor] = deriveDecoder
  implicit val _dec05: Decoder[DisplayIdentifierV2] = deriveDecoder
  implicit val _dec06: Decoder[DisplaySubject] = deriveDecoder
  implicit val _dec07: Decoder[DisplayGenre] = deriveDecoder
  implicit val _dec08: Decoder[DisplayProductionEvent] = deriveDecoder
  implicit val _dec10: Decoder[DisplayLocationV2] = deriveDecoder
  implicit val _dec11: Decoder[DisplayWorkV2] = deriveDecoder
}

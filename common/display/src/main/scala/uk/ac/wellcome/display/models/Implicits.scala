package uk.ac.wellcome.display.models

import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}

import uk.ac.wellcome.display.json.DisplayJsonUtil._

object Implicits {

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required).

  implicit val _enc00: Encoder[DisplayAccessCondition] = deriveEncoder
  implicit val _enc01: Encoder[DisplayLanguage] = deriveEncoder
  implicit val _enc02: Encoder[DisplayWorkType] = deriveEncoder
  implicit val _enc03: Encoder[DisplayPeriod] = deriveEncoder
  implicit val _enc04: Encoder[DisplayContributor] = deriveEncoder
  implicit val _enc05: Encoder[DisplayIdentifier] = deriveEncoder
  implicit val _enc06: Encoder[DisplaySubject] = deriveEncoder
  implicit val _enc07: Encoder[DisplayGenre] = deriveEncoder
  implicit val _enc08: Encoder[DisplayProductionEvent] = deriveEncoder
  implicit val _enc09: Encoder[DisplayItem] = deriveEncoder
  implicit val _enc10: Encoder[DisplayNote] = deriveEncoder
  implicit val _enc11: Encoder[DisplayWork] = deriveEncoder
  implicit val _enc12: Encoder[DisplayImage] = deriveEncoder

  implicit val _dec00: Decoder[DisplayAccessCondition] = deriveDecoder
  implicit val _dec01: Decoder[DisplayLanguage] = deriveDecoder
  implicit val _dec02: Decoder[DisplayWorkType] = deriveDecoder
  implicit val _dec03: Decoder[DisplayPeriod] = deriveDecoder
  implicit val _dec04: Decoder[DisplayContributor] = deriveDecoder
  implicit val _dec05: Decoder[DisplayIdentifier] = deriveDecoder
  implicit val _dec06: Decoder[DisplaySubject] = deriveDecoder
  implicit val _dec07: Decoder[DisplayGenre] = deriveDecoder
  implicit val _dec08: Decoder[DisplayProductionEvent] = deriveDecoder
  implicit val _dec09: Decoder[DisplayItem] = deriveDecoder
  implicit val _dec10: Decoder[DisplayNote] = deriveDecoder
  implicit val _dec11: Decoder[DisplayWork] = deriveDecoder
  implicit val _dec12: Decoder[DisplayImage] = deriveDecoder
}

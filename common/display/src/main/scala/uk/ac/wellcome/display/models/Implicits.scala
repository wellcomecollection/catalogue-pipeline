package uk.ac.wellcome.display.models

import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.display.json.DisplayJsonUtil._
import uk.ac.wellcome.display.models.DisplayLocation.{
  DisplayDigitalResource,
  DisplayOpenShelves
}
import uk.ac.wellcome.models.work.internal.Location.ClosedStores

object Implicits {

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required).

  implicit val _enc00: Encoder[DisplayAccessCondition] = deriveConfiguredEncoder
  implicit val _enc01: Encoder[DisplayLanguage] = deriveConfiguredEncoder
  implicit val _enc02: Encoder[DisplayWorkType] = deriveConfiguredEncoder
  implicit val _enc03: Encoder[DisplayPeriod] = deriveConfiguredEncoder
  implicit val _enc04: Encoder[DisplayContributor] = deriveConfiguredEncoder
  implicit val _enc05: Encoder[DisplayIdentifier] = deriveConfiguredEncoder
  implicit val _enc06: Encoder[DisplaySubject] = deriveConfiguredEncoder
  implicit val _enc07: Encoder[DisplayGenre] = deriveConfiguredEncoder
  implicit val _enc08: Encoder[DisplayProductionEvent] = deriveConfiguredEncoder
  implicit val _enc09: Encoder[DisplayItem] = deriveConfiguredEncoder
  implicit val _enc10: Encoder[DisplayNote] = deriveConfiguredEncoder
  implicit val _enc11: Encoder[DisplayWork] = deriveConfiguredEncoder
  implicit val _enc12: Encoder[DisplayImage] = deriveConfiguredEncoder
  implicit val _enc13: Encoder[DisplayOpenShelves] = deriveConfiguredEncoder
  implicit val _enc14: Encoder[ClosedStores] = deriveConfiguredEncoder
  implicit val _enc15: Encoder[DisplayDigitalResource] = deriveConfiguredEncoder

  implicit val _dec00: Decoder[DisplayAccessCondition] = deriveConfiguredDecoder
  implicit val _dec01: Decoder[DisplayLanguage] = deriveConfiguredDecoder
  implicit val _dec02: Decoder[DisplayWorkType] = deriveConfiguredDecoder
  implicit val _dec03: Decoder[DisplayPeriod] = deriveConfiguredDecoder
  implicit val _dec04: Decoder[DisplayContributor] = deriveConfiguredDecoder
  implicit val _dec05: Decoder[DisplayIdentifier] = deriveConfiguredDecoder
  implicit val _dec06: Decoder[DisplaySubject] = deriveConfiguredDecoder
  implicit val _dec07: Decoder[DisplayGenre] = deriveConfiguredDecoder
  implicit val _dec08: Decoder[DisplayProductionEvent] = deriveConfiguredDecoder
  implicit val _dec09: Decoder[DisplayItem] = deriveConfiguredDecoder
  implicit val _dec10: Decoder[DisplayNote] = deriveConfiguredDecoder
  implicit val _dec11: Decoder[DisplayWork] = deriveConfiguredDecoder
  implicit val _dec12: Decoder[DisplayImage] = deriveConfiguredDecoder
  implicit val _dec13: Decoder[DisplayOpenShelves] = deriveConfiguredDecoder
  implicit val _dec14: Decoder[ClosedStores] = deriveConfiguredDecoder
  implicit val _dec15: Decoder[DisplayDigitalResource] = deriveConfiguredDecoder
}

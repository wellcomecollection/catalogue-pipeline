package weco.catalogue.sierra_adapter.models

import io.circe.generic.extras.semiauto._
import io.circe._
import uk.ac.wellcome.json.JsonUtil._

import scala.util.{Failure, Success, Try}

object Implicits {
  import weco.catalogue.sierra_adapter.json.JsonOps._

  // Because the [[SierraTransformable.itemRecords]] field is keyed by
  // [[SierraItemNumber]] in our case class, but JSON only supports string
  // keys, we need to turn the ID into a string when storing as JSON.
  //
  // This is based on the "Custom key types" section of the Circe docs:
  // https://circe.github.io/circe/codecs/custom-codecs.html#custom-key-types
  //
  implicit val itemNumberEncoder: KeyEncoder[SierraItemNumber] =
    (key: SierraItemNumber) => key.withoutCheckDigit

  implicit val holdingsNumberKeyEncoder: KeyEncoder[SierraHoldingsNumber] =
    (key: SierraHoldingsNumber) => key.withoutCheckDigit

  implicit val itemNumberDecoder: KeyDecoder[SierraItemNumber] =
    (key: String) => Some(SierraItemNumber(key))

  implicit val holdingsNumberKeyDecoder: KeyDecoder[SierraHoldingsNumber] =
    (key: String) => Some(SierraHoldingsNumber(key))

  // The API responses from Sierra can return a RecordNumber as a
  // string or an int.  We need to handle both cases.
  implicit val holdingsNumberDecoder: Decoder[SierraHoldingsNumber] =
    (c: HCursor) =>
      c.value.as[StringOrInt].flatMap { id =>
        Try { SierraHoldingsNumber(id.underlying) } match {
          case Success(number) => Right(number)
          case Failure(err) =>
            Left(DecodingFailure(err.toString, ops = List.empty))
        }
    }

  implicit val holdingsNumberEncoder: Encoder[SierraHoldingsNumber] =
    (number: SierraHoldingsNumber) => Json.fromString(number.withoutCheckDigit)

  implicit val _dec01: Decoder[SierraTransformable] = deriveConfiguredDecoder
  implicit val _dec02: Decoder[SierraItemRecord] = deriveConfiguredDecoder
  implicit val _dec03: Decoder[SierraHoldingsRecord] = deriveConfiguredDecoder

  implicit val _enc01: Encoder[SierraTransformable] = deriveConfiguredEncoder
  implicit val _enc02: Encoder[SierraItemRecord] = deriveConfiguredEncoder
  implicit val _enc03: Encoder[SierraHoldingsRecord] = deriveConfiguredEncoder
}

package weco.catalogue.sierra_adapter.models

import io.circe.generic.extras.semiauto._
import io.circe._
import uk.ac.wellcome.json.JsonUtil._

import scala.util.{Failure, Success, Try}

object Implicits {
  import weco.catalogue.sierra_adapter.json.JsonOps._

  implicit val recordTypeEncoder: Encoder[SierraRecordTypes.Value] =
    (value: SierraRecordTypes.Value) => Json.fromString(value.toString)

  // Because the [[SierraTransformable.itemRecords]] field is keyed by
  // [[SierraItemNumber]] in our case class, but JSON only supports string
  // keys, we need to turn the ID into a string when storing as JSON.
  //
  // This is based on the "Custom key types" section of the Circe docs:
  // https://circe.github.io/circe/codecs/custom-codecs.html#custom-key-types
  //
  implicit val itemNumberKeyEncoder: KeyEncoder[SierraItemNumber] =
    (key: SierraItemNumber) => key.withoutCheckDigit

  implicit val holdingsNumberKeyEncoder: KeyEncoder[SierraHoldingsNumber] =
    (key: SierraHoldingsNumber) => key.withoutCheckDigit

  implicit val itemNumberKeyDecoder: KeyDecoder[SierraItemNumber] =
    (key: String) => Some(new SierraItemNumber(key))

  implicit val holdingsNumberKeyDecoder: KeyDecoder[SierraHoldingsNumber] =
    (key: String) => Some(new SierraHoldingsNumber(key))

  // We have Sierra record numbers stored in three formats:
  //
  //  - a string (from Sierra API responses for bibs and items)
  //  - a number (from Sierra API responses for holdings)
  //  - a dict like {"recordNumber": "1234567"} (from the automatically
  //    derived Circe-encoder in some old case classes)
  //
  // We always encode as a string, but we need to cope with all three
  // forms for decoding.
  private case class RecordNumberDict(recordNumber: StringOrInt)

  def createDecoder[T](create: String => T): Decoder[T] =
    (c: HCursor) => {
      val idString =
        (c.value.as[StringOrInt], c.value.as[RecordNumberDict]) match {
          case (Right(value), _) => Right(value.underlying)
          case (_, Right(value)) => Right(value.recordNumber.underlying)
          case (Left(err), _)    => Left(err)
        }

      idString.flatMap { id =>
        Try { create(id) } match {
          case Success(number) => Right(number)
          case Failure(err) =>
            Left(DecodingFailure(err.toString, ops = List.empty))
        }
      }
    }

  implicit val untypedSierraNumberDecoder: Decoder[UntypedSierraRecordNumber] =
    createDecoder(new UntypedSierraRecordNumber(_))

  implicit val holdingsNumberDecoder: Decoder[SierraHoldingsNumber] =
    createDecoder(new SierraHoldingsNumber(_))

  implicit val itemNumberDecoder: Decoder[SierraItemNumber] =
    createDecoder(new SierraItemNumber(_))

  implicit val bibNumberDecoder: Decoder[SierraBibNumber] =
    createDecoder(new SierraBibNumber(_))

  implicit val typedSierraRecordNumberEncoder
    : Encoder[TypedSierraRecordNumber] =
    (number: TypedSierraRecordNumber) =>
      Json.fromString(number.withoutCheckDigit)

  implicit val holdingsNumberEncoder: Encoder[SierraHoldingsNumber] =
    (number: SierraHoldingsNumber) => Json.fromString(number.withoutCheckDigit)

  implicit val itemNumberEncoder: Encoder[SierraItemNumber] =
    (number: SierraItemNumber) => Json.fromString(number.withoutCheckDigit)

  implicit val bibNumberEncoder: Encoder[SierraBibNumber] =
    (number: SierraBibNumber) => Json.fromString(number.withoutCheckDigit)

  implicit val _dec01: Decoder[SierraItemRecord] = deriveConfiguredDecoder
  implicit val _dec02: Decoder[SierraHoldingsRecord] = deriveConfiguredDecoder
  implicit val _dec03: Decoder[SierraBibRecord] = deriveConfiguredDecoder
  implicit val _dec04: Decoder[SierraTransformable] = deriveConfiguredDecoder

  implicit val _enc01: Encoder[SierraItemRecord] = deriveConfiguredEncoder
  implicit val _enc02: Encoder[SierraHoldingsRecord] = deriveConfiguredEncoder
  implicit val _enc03: Encoder[SierraBibRecord] = deriveConfiguredEncoder
  implicit val _enc04: Encoder[SierraTransformable] = deriveConfiguredEncoder
}

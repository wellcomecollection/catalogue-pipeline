package weco.catalogue.source_model.sierra.identifiers

import io.circe._
import weco.json.JsonUtil._
import weco.catalogue.source_model.json.JsonOps.StringOrInt

import scala.util.{Failure, Success, Try}

trait SierraRecordNumber {
  val recordNumber: String

  if ("""^[0-9]{7}$""".r.unapplySeq(recordNumber) isEmpty) {
    throw new IllegalArgumentException(
      s"requirement failed: Not a 7-digit Sierra record number: $recordNumber"
    )
  }

  override def toString: String = withoutCheckDigit

  /** Returns the ID without the check digit or prefix. */
  def withoutCheckDigit: String = recordNumber
}

trait
SierraRecordNumberOps[T <: SierraRecordNumber] {
  def apply(number: String): T

  implicit val keyEncoder: KeyEncoder[T] =
    (key: T) => key.withoutCheckDigit

  implicit val keyDecoder: KeyDecoder[T] =
    (key: String) => Some(apply(key))

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

  implicit val decoder: Decoder[T] =
    (c: HCursor) => {
      val idString =
        (c.value.as[StringOrInt], c.value.as[RecordNumberDict]) match {
          case (Right(value), _) => Right(value.underlying)
          case (_, Right(value)) => Right(value.recordNumber.underlying)
          case (Left(err), _)    => Left(err)
        }

      idString.flatMap { id =>
        Try { apply(id) } match {
          case Success(number) => Right(number)
          case Failure(err) =>
            Left(DecodingFailure(err.toString, ops = List.empty))
        }
      }
    }

  implicit val encoder: Encoder[T] =
    (number: T) => Json.fromString(number.withoutCheckDigit)
}

package weco.catalogue.internal_model.identifiers

import io.circe.{Decoder, Encoder, HCursor, Json}

class ReferenceNumber(val underlying: String) {
  override def toString: String = underlying

  // Normally we use case classes for immutable data, which provide these
  // methods for us.
  //
  // We deliberately don't use case classes here so we skip automatic
  // case class derivation for JSON encoding/DynamoDB in Scanamo,
  // and force callers to intentionally import the implicits below.
  def canEqual(a: Any): Boolean = a.isInstanceOf[ReferenceNumber]

  override def equals(that: Any): Boolean =
    that match {
      case that: ReferenceNumber =>
        that.canEqual(this) && this.underlying == that.underlying
      case _ => false
    }

  override def hashCode: Int = underlying.hashCode
}

object ReferenceNumber {
  def apply(underlying: String): ReferenceNumber =
    new ReferenceNumber(underlying)

  implicit val encoder: Encoder[ReferenceNumber] =
    (value: ReferenceNumber) => Json.fromString(value.toString)

  implicit val decoder: Decoder[ReferenceNumber] = (cursor: HCursor) =>
    cursor.value.as[String].map(ReferenceNumber(_))
}

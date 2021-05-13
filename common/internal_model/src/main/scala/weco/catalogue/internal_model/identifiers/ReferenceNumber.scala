package weco.catalogue.internal_model.identifiers

import io.circe.{Decoder, Encoder, HCursor, Json}

/** A reference number is a special type of identifier.
  *
  *     when [the identifier is] the reference we have written on the actual item, they
  *     should be the referenceNumber

  *     Ie there is a number written in pencil on the old piece of paper and it is XXXXX -
  *     that's the reference number, as it's what people will see when they have the
  *     object in their hands
  *
  * See https://wellcome.slack.com/archives/C294K7D5M/p1620733997159000 for more
  *
  */
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

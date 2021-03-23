package weco.catalogue.internal_model.identifiers

import io.circe.{Decoder, Encoder, HCursor, Json}
import org.scanamo.DynamoFormat

class CanonicalID(val underlying: String) {
  override def toString: String = underlying

  require(
    !underlying.contains(" "),
    s"Canonical ID cannot contain whitespace; got $underlying"
  )

  require(
    underlying.length == 8,
    s"Canonical ID should have length 8; got $underlying.  Is this actually a canonical ID?"
  )

  // Normally we use case classes for immutable data, which provide these
  // methods for us.
  //
  // We deliberately don't use case classes here so we skip automatic
  // case class derivation for JSON encoding/DynamoDB in Scanamo,
  // and force callers to intentionally import the implicits below.
  def canEqual(a: Any): Boolean = a.isInstanceOf[CanonicalID]

  override def equals(that: Any): Boolean =
    that match {
      case that: CanonicalID =>
        that.canEqual(this) && this.underlying == that.underlying
      case _ => false
    }

  override def hashCode: Int = underlying.hashCode
}

object CanonicalID {
  def apply(underlying: String): CanonicalID =
    new CanonicalID(underlying)

  implicit val encoder: Encoder[CanonicalID] =
    (value: CanonicalID) => Json.fromString(value.toString)

  implicit val decoder: Decoder[CanonicalID] = (cursor: HCursor) =>
    cursor.value.as[String].map(CanonicalID(_))

  implicit def evidence: DynamoFormat[CanonicalID] =
    DynamoFormat
      .coercedXmap[CanonicalID, String, IllegalArgumentException](
        CanonicalID(_),
        _.underlying
      )

  implicit val ordering: Ordering[CanonicalID] =
    (x: CanonicalID, y: CanonicalID) => x.underlying.compare(y.underlying)
}


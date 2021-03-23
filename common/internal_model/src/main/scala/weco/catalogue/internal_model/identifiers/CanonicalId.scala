package weco.catalogue.internal_model.identifiers

import io.circe.{Decoder, Encoder, HCursor, Json}
import org.scanamo.DynamoFormat

class CanonicalId(val underlying: String) {
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
  def canEqual(a: Any): Boolean = a.isInstanceOf[CanonicalId]

  override def equals(that: Any): Boolean =
    that match {
      case that: CanonicalId =>
        that.canEqual(this) && this.underlying == that.underlying
      case _ => false
    }

  override def hashCode: Int = underlying.hashCode
}

object CanonicalId {
  def apply(underlying: String): CanonicalId =
    new CanonicalId(underlying)

  implicit val encoder: Encoder[CanonicalId] =
    (value: CanonicalId) => Json.fromString(value.toString)

  implicit val decoder: Decoder[CanonicalId] = (cursor: HCursor) =>
    cursor.value.as[String].map(CanonicalId(_))

  implicit def evidence: DynamoFormat[CanonicalId] =
    DynamoFormat
      .coercedXmap[CanonicalId, String, IllegalArgumentException](
        CanonicalId(_),
        _.underlying
      )

  implicit val ordering: Ordering[CanonicalId] =
    (x: CanonicalId, y: CanonicalId) => x.underlying.compare(y.underlying)
}

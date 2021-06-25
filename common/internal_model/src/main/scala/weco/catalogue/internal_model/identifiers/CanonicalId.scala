package weco.catalogue.internal_model.identifiers

import weco.storage.TypedStringScanamoOps
import weco.json.TypedString

class CanonicalId(val underlying: String) extends TypedString[CanonicalId] {
  require(
    !underlying.contains(" "),
    s"Canonical ID cannot contain whitespace; got $underlying"
  )

  require(
    underlying.length == 8,
    s"Canonical ID should have length 8; got $underlying.  Is this actually a canonical ID?"
  )
}

object CanonicalId extends TypedStringScanamoOps[CanonicalId] {
  def apply(underlying: String): CanonicalId =
    new CanonicalId(underlying)

  implicit val ordering: Ordering[CanonicalId] =
    (x: CanonicalId, y: CanonicalId) => x.underlying.compare(y.underlying)
}

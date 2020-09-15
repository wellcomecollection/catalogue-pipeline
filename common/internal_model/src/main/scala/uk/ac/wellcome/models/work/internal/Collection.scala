package uk.ac.wellcome.models.work.internal

/**
  * A CollectionPath represents the position of an individual work in a
  * collection hierarchy.
  */
case class CollectionPath(
  path: String,
  level: Option[CollectionLevel] = None,
  label: Option[String] = None,
) {

  lazy val tokens: List[String] =
    path.split("/").toList

  lazy val depth: Int =
    tokens.length

  def isDescendent(other: CollectionPath): Boolean =
    tokens.slice(0, other.depth) == other.tokens
}

sealed trait CollectionLevel

object CollectionLevel {
  object Collection extends CollectionLevel
  object Section extends CollectionLevel
  object Series extends CollectionLevel
  object Item extends CollectionLevel
}

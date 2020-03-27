package uk.ac.wellcome.models.work.internal

case class CollectionPath(
  path: String,
  level: CollectionLevel,
  label: Option[String] = None,
)

sealed trait CollectionLevel

object CollectionLevel {
  object Collection extends CollectionLevel
  object Section extends CollectionLevel
  object Series extends CollectionLevel
  object Item extends CollectionLevel
}

package weco.catalogue.internal_model.work

/**
  * A CollectionPath represents the position of an individual work in a
  * collection hierarchy.
  */
case class CollectionPath(
  path: String,
  label: Option[String] = None,
)

package weco.catalogue.internal_model.work

/** A CollectionPath represents the position of an individual work in a
  * collection hierarchy.
  *
  * This is an internal value used by the relation embedder to construct trees,
  * not a value we display publicly.
  */
case class CollectionPath(path: String)

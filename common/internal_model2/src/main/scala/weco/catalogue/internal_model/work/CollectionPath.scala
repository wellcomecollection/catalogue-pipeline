package weco.catalogue.internal_model.work

/** Represents the position of an individual work in a collection hierarchy.
  *
  * @param path
  *   The internal control field that describes the position of a Work within
  *   the hierarchy
  * @param label
  *   The path we'll display as publicly visible on /works
  *
  * Note that these two don't have to be the same (although they often are).
  *
  * e.g. in Calm the path is the RefNo, the label is the AltRefNo. A work could
  * have the RefNo PPDAL/E/2/13/1 but the AltRefNo PP/DAL/E/2/13/1 -- because
  * the top-level item of the hierarchy is PPDAL (personal papers of A G Dally),
  * not PP.
  */
case class CollectionPath(
  path: String,
  label: Option[String] = None
)

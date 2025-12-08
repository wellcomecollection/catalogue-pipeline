package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.identifiers.CanonicalId

/** Holds relations for a particular work.
  *
  * @param ancestors
  *   Ancestors from root downwards
  * @param children
  *   Children of the work
  * @param siblingsPreceding
  *   Siblings preceding the work
  * @param siblingsSucceeding
  *   Siblings following the work
  */
case class Relations(
  ancestors: List[Relation] = Nil,
  children: List[Relation] = Nil,
  siblingsPreceding: List[Relation] = Nil,
  siblingsSucceeding: List[Relation] = Nil
)

object Relations {

  def none: Relations =
    Relations(
      ancestors = Nil,
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
}

case class Relation(
  id: Option[CanonicalId],
  title: Option[String],
  collectionPath: Option[CollectionPath],
  workType: WorkType,
  depth: Int,
  numChildren: Int,
  numDescendents: Int
)

package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.WorkState.{Indexed, Merged}

/** Holds relations for a particular work.
  *
  * @param ancestors Ancestors from root downwards
  * @param children Children of the work
  * @param siblingsPreceding Siblings preceding the work
  * @param siblingsSucceeding Siblings following the work
  */
case class Relations(
  ancestors: List[Relation] = Nil,
  children: List[Relation] = Nil,
  siblingsPreceding: List[Relation] = Nil,
  siblingsSucceeding: List[Relation] = Nil,
) {
  def size: Int =
    List(
      ancestors,
      children,
      siblingsPreceding,
      siblingsSucceeding
    ).map(_.size).sum
}

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
  numDescendents: Int,
)

object Relation {
  private def apply(
    id: CanonicalId,
    data: WorkData[_],
    depth: Int,
    numChildren: Int,
    numDescendents: Int
  ): Relation =
    Relation(
      id = Some(id),
      title = data.title,
      collectionPath = data.collectionPath,
      workType = data.workType,
      depth = depth,
      numChildren = numChildren,
      numDescendents = numDescendents,
    )

  def apply[State <: WorkState](work: Work[State],
                                depth: Int,
                                numChildren: Int,
                                numDescendents: Int): Relation =
    work.state match {
      case state: Indexed =>
        apply(state.canonicalId, work.data, depth, numChildren, numDescendents)

      case state: Merged =>
        apply(state.canonicalId, work.data, depth, numChildren, numDescendents)

      case _ =>
        throw new IllegalArgumentException(s"Cannot create Relation from $work")
    }
}

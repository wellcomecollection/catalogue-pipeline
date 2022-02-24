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

  /**
   * A Relation created with just a String is a Series.  The string is the title.
   * A Series is not an entity in its own right, so does not have an id of its own.
   *
   * Practically, a Series does not exist in a hierarchy, so does not have depth or
   * children or descendents, even though (obviously) there are objects that are "partOf"
   * the series.
   */
  def apply(series: String): Relation =
    new Relation(None, Some(series), None, WorkType.Series, 0, 0, 0)

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

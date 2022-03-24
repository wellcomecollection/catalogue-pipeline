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

  def +(that: Relations): Relations = {
    Relations(
      ancestors = RelationSet(this.ancestors ::: that.ancestors),
      children = RelationSet(this.children ::: that.children),
      siblingsPreceding =
        RelationSet(this.siblingsPreceding ::: that.siblingsPreceding),
      siblingsSucceeding =
        RelationSet(this.siblingsSucceeding ::: that.siblingsSucceeding)
    )
  }
}

/**
  * Lists of Relations are unique by title, with identified relations taking
  * priority over unidentified ones.
  *
  * The situation can arise where an early stage sets up some relations
  * but only knows the title of a given relation, but a later stage then
  * then finds that same relation using an identifier.
  * In that case, the newer, identifier-based one supersedes
  * the older name-only one.
  *
  * Sometimes, that early name-only relation is not identified, so should
  * be preserved.
  *
  * A concrete example of this is where a Sierra document contains a 773
  * field.  During transformation, we do not know whether this will result
  * in it becoming a member of a Series or a Hierarchy, because that distinction
  * is driven by the presence of a reciprocal 774 relationship on the parent.
  *
  * So, during the transformer phase, a candidate Series ancestor is created.
  * If, during the relation embedder phase, the other end is found, then the
  * Series ancestor is to be discarded.
  */
object RelationSet {
  def apply(relations: List[Relation]): List[Relation] = {
    // Store the original first positions, in order to preserve order.
    // Order is significant in RelationSets, e.g. in a hierarchy, ancestors = parent,grandparent,great grandparent etc.
    // For hierarches, this could be determined by depth, but in the case of Series links,
    // the order is determined by the order in the source data.
    val indexMap = relations.map(_.title).distinct.zipWithIndex.toMap
    relations
      .groupBy(_.title)
      .values
      .map(_.maxBy(_.id))
      .toList
      .sortBy(relation => indexMap(relation.title))
  }
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

/**
  * A Relation can be created with just a String representing the title of a Series.
  * A Series is not an entity in its own right, so does not have an id of its own.
  *
  * Practically, a Series does not exist in a hierarchy, so does not have depth or
  * children or descendants, nor does it have a collectionPath, even though (obviously)
  * there are objects that are "partOf" the series.
  */
object SeriesRelation {
  def apply(series: String): Relation =
    Relation(
      id = None,
      title = Some(series),
      collectionPath = None,
      workType = WorkType.Series,
      depth = 0,
      numChildren = 0,
      numDescendents = 0
    )
}

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

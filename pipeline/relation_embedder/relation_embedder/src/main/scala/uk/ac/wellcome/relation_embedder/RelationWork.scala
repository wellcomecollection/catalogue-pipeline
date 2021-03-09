package uk.ac.wellcome.relation_embedder

import uk.ac.wellcome.models.work.internal._

/** Contains the minimal set of fields on a Work needed for generating a
  * Relation object.
  */
case class RelationWork(
  data: RelationWorkData,
  state: RelationWorkState
) {

  def toRelation(depth: Int, numChildren: Int, numDescendents: Int) =
    Relation(
      id = state.canonicalId,
      title = data.title,
      collectionPath = data.collectionPath,
      workType = data.workType,
      depth = depth,
      numChildren = numChildren,
      numDescendents = numDescendents,
    )
}

case class RelationWorkState(
  canonicalId: String,
  availabilities: Set[Availability],
)

case class RelationWorkData(
  title: Option[String],
  collectionPath: Option[CollectionPath],
  workType: WorkType
)

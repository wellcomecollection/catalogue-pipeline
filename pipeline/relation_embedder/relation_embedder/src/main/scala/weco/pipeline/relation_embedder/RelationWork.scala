package weco.pipeline.relation_embedder

import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work._

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
      relationPath = data.relationPath,
      workType = data.workType,
      depth = depth,
      numChildren = numChildren,
      numDescendents = numDescendents,
    )
}

case class RelationWorkState(
  canonicalId: CanonicalId,
  availabilities: Set[Availability],
)

case class RelationWorkData(
  title: Option[String],
  relationPath: Option[RelationPath],
  workType: WorkType
)

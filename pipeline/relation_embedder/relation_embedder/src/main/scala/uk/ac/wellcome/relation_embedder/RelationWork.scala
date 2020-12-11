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
    Relation[DataState.Identified](
      id = IdState.Identified(
        sourceIdentifier = state.sourceIdentifier,
        canonicalId = state.canonicalId
      ),
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
  sourceIdentifier: SourceIdentifier
)

case class RelationWorkData(
  title: Option[String],
  collectionPath: Option[CollectionPath],
  workType: WorkType
)

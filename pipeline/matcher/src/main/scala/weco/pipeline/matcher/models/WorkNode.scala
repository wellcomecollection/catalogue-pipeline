package weco.pipeline.matcher.models

import weco.catalogue.internal_model.identifiers.CanonicalId

case class WorkNode(
  id: CanonicalId,
  subgraphId: String,
  componentIds: List[CanonicalId],
  sourceWork: Option[SourceWorkData] = None,
) {
  require(componentIds.sorted == componentIds)
}

case object WorkNode {
  def apply(id: CanonicalId, subgraphId: String, componentIds: List[CanonicalId], sourceWork: SourceWorkData): WorkNode =
    WorkNode(id = id, subgraphId = subgraphId, componentIds = componentIds, sourceWork = Some(sourceWork))
}

case class SourceWorkData(
  version: Int,
  suppressed: Boolean = false,
  mergeCandidateIds: List[CanonicalId] = List(),
) {
  require(mergeCandidateIds.sorted == mergeCandidateIds)
}

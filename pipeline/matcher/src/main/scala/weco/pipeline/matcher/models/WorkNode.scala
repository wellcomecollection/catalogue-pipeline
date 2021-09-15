package weco.pipeline.matcher.models

import weco.catalogue.internal_model.identifiers.CanonicalId

case class WorkNode(
  id: CanonicalId,
  version: Option[Int],
  linkedIds: List[CanonicalId],
  componentId: String
)

object WorkNode {
  def apply(id: CanonicalId,
            version: Int,
            linkedIds: List[CanonicalId],
            componentId: String): WorkNode =
    WorkNode(id, Some(version), linkedIds, componentId)
}

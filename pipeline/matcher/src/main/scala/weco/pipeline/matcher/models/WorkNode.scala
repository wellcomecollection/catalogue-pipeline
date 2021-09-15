package weco.pipeline.matcher.models

import weco.catalogue.internal_model.identifiers.CanonicalId

import java.time.Instant

case class WorkNode(
  id: CanonicalId,
  modifiedTime: Option[Instant],
  linkedIds: List[CanonicalId],
  componentId: String
)

object WorkNode {
  def apply(id: CanonicalId,
            modifiedTime: Instant,
            linkedIds: List[CanonicalId],
            componentId: String): WorkNode =
    WorkNode(id, Some(modifiedTime), linkedIds, componentId)
}

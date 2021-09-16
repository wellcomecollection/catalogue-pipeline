package weco.pipeline.matcher.models

import weco.catalogue.internal_model.identifiers.CanonicalId

import java.time.Instant

/** Represents a Work in the matcher graph.
  *
  * The modifiedTime is optional because this might represent a Work we
  * haven't seen yet.  e.g. if we got a Work A that referred to B:
  *
  *       A --> B
  *
  * then we'd create a WorkIdentifier for A and B, even though we haven't
  * seen B yet.  This means that if/when we see B, we'll remember its
  * association with A.
  *
  */
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

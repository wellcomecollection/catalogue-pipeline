package uk.ac.wellcome.models.matcher

import weco.catalogue.internal_model.identifiers.CanonicalID

case class WorkNode(
  id: CanonicalID,
  version: Option[Int],
  linkedIds: List[CanonicalID],
  componentId: String
)

object WorkNode {
  def apply(id: CanonicalID,
            version: Int,
            linkedIds: List[CanonicalID],
            componentId: String): WorkNode =
    WorkNode(id, Some(version), linkedIds, componentId)
}

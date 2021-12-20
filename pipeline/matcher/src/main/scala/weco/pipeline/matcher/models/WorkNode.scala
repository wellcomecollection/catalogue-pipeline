package weco.pipeline.matcher.models

import weco.catalogue.internal_model.identifiers.CanonicalId

case class WorkNode(
  id: CanonicalId,
  version: Option[Int],
  linkedIds: List[CanonicalId],
  // Note: this field really identifies all the works that should be retrieved
  // together.  It's not a "component" ID in the strict graph sense of the word,
  // it's used to group works that should be processed together.
  //
  // TODO: Check the name of this field to something like "group ID".
  componentId: String,
  // Records whether this work is suppressed in a source system -- if so,
  // we shouldn't be using it to construct matcher graphs.
  suppressed: Boolean = false
)

object WorkNode {
  def apply(id: CanonicalId,
            version: Int,
            linkedIds: List[CanonicalId],
            componentId: String): WorkNode =
    WorkNode(id, Some(version), linkedIds, componentId)

  def apply(id: CanonicalId,
            version: Int,
            linkedIds: List[CanonicalId],
            componentId: String,
            suppressed: Boolean): WorkNode =
    WorkNode(id, Some(version), linkedIds, componentId, suppressed)
}

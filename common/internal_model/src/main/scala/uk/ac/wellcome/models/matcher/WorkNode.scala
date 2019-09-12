package uk.ac.wellcome.models.matcher

case class WorkNode(
  id: String,
  version: Option[Int],
  linkedIds: List[String],
  componentId: String
)

object WorkNode {

  def apply(id: String,
            version: Int,
            linkedIds: List[String],
            componentId: String): WorkNode =
    WorkNode(id, Some(version), linkedIds, componentId)
}

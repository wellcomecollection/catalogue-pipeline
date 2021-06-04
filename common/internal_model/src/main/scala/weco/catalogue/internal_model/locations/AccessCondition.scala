package weco.catalogue.internal_model.locations

case class AccessCondition(
  method: Option[AccessMethod] = None,
  status: Option[AccessStatus] = None,
  terms: Option[String] = None,
  to: Option[String] = None,
  note: Option[String] = None
) {
  def isEmpty: Boolean =
    this == AccessCondition()

  def isAvailable: Boolean = status.exists(_.isAvailable)

  def hasRestrictions: Boolean = status.exists(_.hasRestrictions)
}

case object AccessCondition {
  def apply(status: AccessStatus): AccessCondition =
    AccessCondition(status = Some(status))
}

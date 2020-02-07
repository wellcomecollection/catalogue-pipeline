package uk.ac.wellcome.models.work.internal

case class AccessCondition(
  status: Option[AccessStatus] = None,
  terms: Option[String] = None,
  to: Option[String] = None
)

sealed trait AccessStatus

object AccessStatus {

  case object Open extends AccessStatus

  case object OpenWithAdvisory extends AccessStatus

  case object Restricted extends AccessStatus

  case object Closed extends AccessStatus

  case object LicensedResources extends AccessStatus

  case object Unavailable extends AccessStatus

  case object PermissionRequired extends AccessStatus
}

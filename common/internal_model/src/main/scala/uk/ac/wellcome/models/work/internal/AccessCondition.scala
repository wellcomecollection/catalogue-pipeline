package uk.ac.wellcome.models.work.internal

import java.time.Instant

case class AccessCondition(
  status: AccessStatus,
  terms: Option[String] = None,
  to: Option[Instant] = None
)

sealed trait AccessStatus

object AccessStatus {

  case object Open extends AccessStatus

  case object OpenWithAdvisory extends AccessStatus

  case object Restricted extends AccessStatus

  case object Closed extends AccessStatus

  case object LicensedResources extends AccessStatus
}

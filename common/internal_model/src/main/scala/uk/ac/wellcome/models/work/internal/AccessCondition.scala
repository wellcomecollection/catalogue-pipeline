package uk.ac.wellcome.models.work.internal

import uk.ac.wellcome.models.work.internal.result.Result

case class AccessCondition(
  status: Option[AccessStatus] = None,
  terms: Option[String] = None,
  to: Option[String] = None
) {
  def filterEmpty: Option[AccessCondition] =
    this match {
      case AccessCondition(None, None, None) => None
      case accessCondition                   => Some(accessCondition)
    }
}

sealed trait AccessStatus

object AccessStatus {

  case object Open extends AccessStatus

  case object OpenWithAdvisory extends AccessStatus

  case object Restricted extends AccessStatus

  case object Closed extends AccessStatus

  case object LicensedResources extends AccessStatus

  case object Unavailable extends AccessStatus

  case object PermissionRequired extends AccessStatus

  def apply(str: String): Result[AccessStatus] =
    str.toLowerCase match {
      case status if status.startsWith("open with advisory") =>
        Right(AccessStatus.OpenWithAdvisory)
      case status if status.startsWith("open") =>
        Right(AccessStatus.Open)
      case status if status.startsWith("restricted") =>
        Right(AccessStatus.Restricted)
      case status if status.startsWith("cannot be produced") =>
        Right(AccessStatus.Restricted)
      case status if status.startsWith("certain restrictions apply") =>
        Right(AccessStatus.Restricted)
      case status if status.startsWith("closed") =>
        Right(AccessStatus.Closed)
      case status if status.startsWith("missing") =>
        Right(AccessStatus.Unavailable)
      case status if status.startsWith("temporarily unavailable") =>
        Right(AccessStatus.Unavailable)
      case status if status.startsWith("permission required") =>
        Right(AccessStatus.PermissionRequired)
      case status =>
        Left(new Exception(s"Unrecognised access status: $status"))
    }
}

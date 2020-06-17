package uk.ac.wellcome.models.work.internal

class UnknownAccessStatus(status: String) extends Exception(status)

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

sealed trait AccessStatus { this: AccessStatus =>

  def name = this.getClass.getSimpleName.stripSuffix("$")
}

object AccessStatus {

  case object Open extends AccessStatus

  case object OpenWithAdvisory extends AccessStatus

  case object Restricted extends AccessStatus

  case object Closed extends AccessStatus

  case object LicensedResources extends AccessStatus

  case object Unavailable extends AccessStatus

  case object PermissionRequired extends AccessStatus

  def apply(status: String): Either[Exception, AccessStatus] =
    status.toLowerCase match {
      case lowerCaseStatus if lowerCaseStatus.startsWith("open with advisory") =>
        Right(AccessStatus.OpenWithAdvisory)
      case lowerCaseStatus if lowerCaseStatus.startsWith("requires registration") =>
        Right(AccessStatus.OpenWithAdvisory)
      case lowerCaseStatus if lowerCaseStatus.startsWith("open") =>
        Right(AccessStatus.Open)
      case lowerCaseStatus if lowerCaseStatus.startsWith("restricted") =>
        Right(AccessStatus.Restricted)
      case lowerCaseStatus if lowerCaseStatus.startsWith("cannot be produced") =>
        Right(AccessStatus.Restricted)
      case lowerCaseStatus if lowerCaseStatus.startsWith("certain restrictions apply") =>
        Right(AccessStatus.Restricted)
      case lowerCaseStatus if lowerCaseStatus.startsWith("clinical images") =>
        Right(AccessStatus.Restricted)
      case lowerCaseStatus if lowerCaseStatus.startsWith("by appointment") =>
        Right(AccessStatus.Restricted)
      case lowerCaseStatus if lowerCaseStatus.startsWith("closed") =>
        Right(AccessStatus.Closed)
      case lowerCaseStatus if lowerCaseStatus.startsWith("missing") =>
        Right(AccessStatus.Unavailable)
      case lowerCaseStatus if lowerCaseStatus.startsWith("temporarily unavailable") =>
        Right(AccessStatus.Unavailable)
      case lowerCaseStatus if lowerCaseStatus.startsWith("deaccessioned") =>
        Right(AccessStatus.Unavailable)
      case lowerCaseStatus if lowerCaseStatus.startsWith("in copyright") =>
        Right(AccessStatus.LicensedResources)
      case lowerCaseStatus if lowerCaseStatus.startsWith("permission required") =>
        Right(AccessStatus.PermissionRequired)
      case _ =>
        Left(new UnknownAccessStatus(status))
    }
}

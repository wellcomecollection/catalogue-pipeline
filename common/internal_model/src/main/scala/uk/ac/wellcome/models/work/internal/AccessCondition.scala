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

  def hasRestrictions: Boolean = status.exists(_.hasRestrictions)
}

sealed trait AccessStatus { this: AccessStatus =>

  def name = this.getClass.getSimpleName.stripSuffix("$")

  def hasRestrictions: Boolean = this match {
    case AccessStatus.OpenWithAdvisory   => true
    case AccessStatus.Restricted         => true
    case AccessStatus.Closed             => true
    case AccessStatus.PermissionRequired => true
    case _                               => false
  }
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
      case lowerCaseStatus if lowerCaseStatus.startsWith(
        "open with advisory",
        "requires registration"
      ) =>
        Right(AccessStatus.OpenWithAdvisory)

      case lowerCaseStatus if lowerCaseStatus.startsWith(
        "unrestricted",
        "open"
      ) =>
        Right(AccessStatus.Open)

      case lowerCaseStatus if lowerCaseStatus.startsWith(
        "restricted",
        "cannot be produced",
        "certain restrictions apply",
        "clinical images",
        "by appointment",
        "the file is restricted",
        "this file is restricted"
      ) =>
        Right(AccessStatus.Restricted)

      case lowerCaseStatus if lowerCaseStatus.startsWith(
        "closed",
        "the file is closed",
        "this file is closed",
        "the papers are closed",
        "the files in this series are closed"
      ) =>
        Right(AccessStatus.Closed)

      case lowerCaseStatus if lowerCaseStatus.startsWith(
        "missing",
        "temporarily unavailable",
        "deaccessioned"
      ) =>
        Right(AccessStatus.Unavailable)

      case lowerCaseStatus if lowerCaseStatus.startsWith("in copyright") =>
        Right(AccessStatus.LicensedResources)

      case lowerCaseStatus if lowerCaseStatus.startsWith(
        "permission required",
        "permission is required",
        "donor permission",
        "permission must be obtained"
      ) =>
        Right(AccessStatus.PermissionRequired)

      case _ =>
        Left(new UnknownAccessStatus(status))
    }

  implicit class StringOps(s: String) {
    def startsWith(prefixes: String*): Boolean =
      prefixes.exists { s.startsWith }
  }
}

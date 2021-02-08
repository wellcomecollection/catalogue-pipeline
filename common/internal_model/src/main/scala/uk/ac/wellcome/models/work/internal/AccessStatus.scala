package uk.ac.wellcome.models.work.internal

class UnknownAccessStatus(status: String) extends Exception(status)

sealed trait AccessStatus { this: AccessStatus =>

  def name: String = this.getClass.getSimpleName.stripSuffix("$")

  def hasRestrictions: Boolean = this match {
    case AccessStatus.OpenWithAdvisory   => true
    case AccessStatus.Restricted         => true
    case AccessStatus.ByAppointment      => true
    case AccessStatus.Closed             => true
    case AccessStatus.PermissionRequired => true
    case _                               => false
  }
}

object AccessStatus {

  // These types should reflect our collections access framework, as described in
  // Wellcome Collection's Access Policy.
  // See https://wellcomecollection.org/pages/Wvmu3yAAAIUQ4C7F#access-policy
  //
  // This is based on §12 Research access, as retrieved 8 February 2021
  //
  case object Open extends AccessStatus

  case object OpenWithAdvisory extends AccessStatus

  case object Restricted extends AccessStatus

  case object ByAppointment extends AccessStatus

  case object TemporarilyUnavailable extends AccessStatus

  case object Unavailable extends AccessStatus

  case object Closed extends AccessStatus

  case object LicensedResources extends AccessStatus

  case object PermissionRequired extends AccessStatus

  def apply(status: String): Either[Exception, AccessStatus] =
    status.toLowerCase match {
      case lowerCaseStatus
          if lowerCaseStatus.startsWith(
            "open with advisory",
            "requires registration"
          ) =>
        Right(AccessStatus.OpenWithAdvisory)

      case lowerCaseStatus
          if lowerCaseStatus.startsWith(
            "unrestricted",
            "open"
          ) =>
        Right(AccessStatus.Open)

      case lowerCaseStatus
          if lowerCaseStatus.startsWith(
            "restricted",
            "cannot be produced",
            "certain restrictions apply",
            "clinical images",
            "the file is restricted",
            "this file is restricted"
          ) =>
        Right(AccessStatus.Restricted)

      case lowerCaseStatus
          if lowerCaseStatus.startsWith(
            "by appointment"
          ) =>
        Right(AccessStatus.ByAppointment)

      case lowerCaseStatus
          if lowerCaseStatus.startsWith(
            "closed",
            "the file is closed",
            "this file is closed",
            "the papers are closed",
            "the files in this series are closed"
          ) =>
        Right(AccessStatus.Closed)

      case lowerCaseStatus
          if lowerCaseStatus.startsWith(
            "missing",
            "deaccessioned"
          ) =>
        Right(AccessStatus.Unavailable)

      case lowerCaseStatus
          if lowerCaseStatus.startsWith(
            "temporarily unavailable"
          ) =>
        Right(AccessStatus.TemporarilyUnavailable)

      case lowerCaseStatus if lowerCaseStatus.startsWith("in copyright") =>
        Right(AccessStatus.LicensedResources)

      case lowerCaseStatus
          if lowerCaseStatus.startsWith(
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

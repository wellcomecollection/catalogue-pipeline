package weco.catalogue.internal_model.locations

import enumeratum.{Enum, EnumEntry}

class UnknownAccessStatus(status: String) extends Exception(status)

sealed trait AccessStatus extends EnumEntry { this: AccessStatus =>
  def name: String = this.getClass.getSimpleName.stripSuffix("$")

  def isAvailable: Boolean = this match {
    case AccessStatus.Open              => true
    case AccessStatus.OpenWithAdvisory  => true
    case AccessStatus.LicensedResources => true
    case _                              => false
  }

  def hasRestrictions: Boolean = this match {
    case AccessStatus.OpenWithAdvisory   => true
    case AccessStatus.Restricted         => true
    case AccessStatus.ByAppointment      => true
    case AccessStatus.Closed             => true
    case AccessStatus.PermissionRequired => true
    case _                               => false
  }
}

object AccessStatus extends Enum[AccessStatus] {
  val values = findValues
  assert(
    values.size == values.map { _.name }.toSet.size,
    "IDs for AccessStatus are not unique!"
  )

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

  def apply(status: String): Either[Exception, AccessStatus] = {
    val normalisedStatus = status.trim.stripSuffix(".").trim.toLowerCase()

    normalisedStatus match {
      case value if value == "open with advisory" =>
        Right(AccessStatus.OpenWithAdvisory)

      // This has to come after the "OpenWithAdvisory" branch so we don't
      // match on the partial open.
      case value
          if value == "open" || value == "unrestricted" || value == "unrestricted / open" || value == "unrestricted (open)" || value == "open access" =>
        Right(AccessStatus.Open)

      case value
          if value == "restricted" || value == "certain restrictions apply" || value
            .startsWith("restricted access") =>
        Right(AccessStatus.Restricted)

      case value if value.startsWith("by appointment") =>
        Right(AccessStatus.ByAppointment)

      case value if value == "closed" =>
        Right(AccessStatus.Closed)

      case value
          if value == "cannot be produced" || value == "missing" || value == "deaccessioned" =>
        Right(AccessStatus.Unavailable)

      case value if value == "temporarily unavailable" =>
        Right(AccessStatus.TemporarilyUnavailable)

      case value
          if value == "donor permission" || value == "permission is required to view these item" || value == "permission is required to view this item" =>
        Right(AccessStatus.PermissionRequired)

      case _ =>
        Left(new UnknownAccessStatus(status))
    }
  }

  implicit class StringOps(s: String) {
    def startsWith(prefixes: String*): Boolean =
      prefixes.exists { s.startsWith }
  }
}

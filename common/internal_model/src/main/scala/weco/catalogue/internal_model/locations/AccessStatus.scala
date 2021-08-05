package weco.catalogue.internal_model.locations

import enumeratum.{Enum, EnumEntry}
import weco.catalogue.internal_model.locations.AccessStatus.LicensedResources

class UnknownAccessStatus(status: String) extends Exception(status)

sealed trait AccessStatus extends EnumEntry { this: AccessStatus =>
  def name: String = this.getClass.getSimpleName.stripSuffix("$")

  val id: String
  val label: String

  def isAvailable: Boolean = this match {
    case AccessStatus.Open                                          => true
    case AccessStatus.OpenWithAdvisory                              => true
    case AccessStatus.LicensedResources(LicensedResources.Resource) => true

    // This is used for cases where we have items that link to something
    // related to the item (e.g. a description on a publisher website),
    // but which isn't the same as the item itself.
    //
    // We still want these items on the work, we just don't want these items
    // to match the "available online" filter.
    case AccessStatus.LicensedResources(LicensedResources.RelatedResource) => false

    case _ => false
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
  case object Open extends AccessStatus {
    override val id: String = "open"
    override val label: String = "Open"
  }

  case object OpenWithAdvisory extends AccessStatus {
    override val id: String = "open-with-advisory"
    override val label: String = "Open with advisory"
  }

  case object Restricted extends AccessStatus {
    override val id: String = "restricted"
    override val label: String = "Restricted"
  }

  case object ByAppointment extends AccessStatus {
    override val id: String = "by-appointment"
    override val label: String = "By appointment"
  }

  case object TemporarilyUnavailable extends AccessStatus {
    override val id: String = "temporarily-unavailable"
    override val label: String = "Temporarily unavailable"
  }

  case object Unavailable extends AccessStatus {
    override val id: String = "unavailable"
    override val label: String = "Unavailable"
  }

  case object Closed extends AccessStatus {
    override val id: String = "closed"
    override val label: String = "Closed"
  }

  object LicensedResources {
    // This is based on MARC field 856 indicator 2
    // See https://www.loc.gov/marc/bibliographic/bd856.html
    //
    // We don't expose this distinction in the public API, but we need it for
    // the "available online" filter (see above).
    sealed trait Relationship
    case object Resource extends Relationship
    case object RelatedResource extends Relationship
  }

  case class LicensedResources(relationship: LicensedResources.Relationship = LicensedResources.Resource) extends AccessStatus {
    override val id: String = "licensed-resources"
    override val label: String = "Licensed resources"
  }

  case object PermissionRequired extends AccessStatus {
    override val id: String = "permission-required"
    override val label: String = "Permission required"
  }

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

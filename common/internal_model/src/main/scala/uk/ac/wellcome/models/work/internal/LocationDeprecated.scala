package uk.ac.wellcome.models.work.internal

sealed trait LocationDeprecated {
  val locationType: LocationType
  val accessConditions: List[AccessCondition]

  def hasRestrictions: Boolean =
    accessConditions.exists { accessCondition =>
      accessCondition.status exists {
        case AccessStatus.OpenWithAdvisory   => true
        case AccessStatus.Restricted         => true
        case AccessStatus.Closed             => true
        case AccessStatus.PermissionRequired => true
        case _                               => false
      }
    }
}

case class DigitalLocationDeprecated(
  url: String,
  locationType: LocationType,
  license: Option[License] = None,
  credit: Option[String] = None,
  accessConditions: List[AccessCondition] = Nil
) extends LocationDeprecated

case class PhysicalLocationDeprecated(
  locationType: LocationType,
  label: String,
  accessConditions: List[AccessCondition] = Nil
) extends LocationDeprecated

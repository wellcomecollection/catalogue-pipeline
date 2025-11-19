package weco.catalogue.internal_model.locations

sealed trait Location {
  val locationType: LocationType
  val accessConditions: List[AccessCondition]
  val license: Option[License]

  def isAvailable: Boolean =
    accessConditions.exists(_.isAvailable)

  def hasRestrictions: Boolean =
    accessConditions.exists(_.hasRestrictions)
}

case class DigitalLocation(
  url: String,
  locationType: DigitalLocationType,
  license: Option[License] = None,
  credit: Option[String] = None,
  linkText: Option[String] = None,
  accessConditions: List[AccessCondition] = Nil,
  createdDate: Option[String] = None
) extends Location

case class PhysicalLocation(
  locationType: PhysicalLocationType,
  label: String,
  license: Option[License] = None,
  shelfmark: Option[String] = None,
  accessConditions: List[AccessCondition] = Nil
) extends Location

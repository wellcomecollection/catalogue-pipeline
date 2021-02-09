package uk.ac.wellcome.models.work.internal

sealed trait Location {
  val locationType: LocationType
  val accessConditions: List[AccessCondition]
  val license: Option[License]

  def hasRestrictions: Boolean =
    accessConditions.exists(_.hasRestrictions)
}

case class DigitalLocation(
  url: String,
  locationType: LocationType,
  license: Option[License] = None,
  credit: Option[String] = None,
  accessConditions: List[AccessCondition] = Nil
) extends Location

case class PhysicalLocation(
  locationType: LocationType,
  label: String,
  license: Option[License] = None,
  accessConditions: List[AccessCondition] = Nil
) extends Location
